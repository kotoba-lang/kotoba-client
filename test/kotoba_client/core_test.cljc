(ns kotoba-client.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [multiformats.core :as mf]
            [kotoba-client.core :as kc]))

(deftest ingest-block-accepts-matching-cid
  (let [bytes (.getBytes "hello" "UTF-8")
        cid (mf/cidv1-dag-cbor bytes)]
    (is (= bytes (kc/ingest-block cid bytes)))))

(deftest ingest-block-rejects-tampered-bytes
  (let [bytes (.getBytes "hello" "UTF-8")
        cid (mf/cidv1-dag-cbor bytes)
        tampered (.getBytes "hellx" "UTF-8")]
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                           #"CID mismatch"
                           (kc/ingest-block cid tampered)))))

(defn- fake-graph
  "A tiny 2-node linear chain: root -> leaf, addressed by content (so the
  CIDs below are stand-ins, not real hashes -- this test exercises the
  hydrate loop's convergence/port-wiring, not CID computation, which
  `ingest-block-*` above already covers with real CIDs)."
  []
  {"root" (.getBytes "root-body" "UTF-8")
   "leaf" (.getBytes "leaf-body" "UTF-8")})

(deftest hydrate-via-blocks-fetches-until-missing-cids-is-empty
  (let [remote (fake-graph)
        local (atom {})
        rounds (atom 0)
        ;; first round: root missing. second round: nothing missing.
        missing-cids (fn [_root _store]
                       (swap! rounds inc)
                       (if (= 1 @rounds) ["fake-cid"] []))
        fetch-block (fn [cid] (get remote cid (.getBytes "fake-cid-body" "UTF-8")))]
    (with-redefs [kc/ingest-block (fn [_claimed bytes] bytes)] ; bypass real CID check for this port-wiring test
      (let [n (kc/hydrate-via-blocks
               {:missing-cids missing-cids
                :fetch-block fetch-block
                :store {:put! (fn [cid bytes] (swap! local assoc cid bytes))
                        :get-fn (fn [cid] (get @local cid))}}
               "root")]
        (is (= 1 n))
        (is (contains? @local "fake-cid"))))))

(deftest hydrate-via-blocks-throws-if-it-never-converges
  (with-redefs [kc/ingest-block (fn [_claimed bytes] bytes)]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
         #"did not converge"
         (kc/hydrate-via-blocks
          {:missing-cids (fn [_ _] ["always-missing"]) ; never empties
           :fetch-block (fn [_] (.getBytes "x" "UTF-8"))
           :store {:put! (fn [_ _]) :get-fn (fn [_] nil)}
           :max-rounds 3}
          "root")))))
