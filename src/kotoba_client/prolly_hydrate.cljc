(ns kotoba-client.prolly-hydrate
  "A `missing-cids` implementation (see `kotoba-client.core/hydrate-via-blocks`)
  for `prolly-tree`-shaped trees: walks nodes already present in `store` to
  find child CIDs (internal-node `children` entries) not yet fetched,
  mirroring the deleted JS client's `missingBlockCids` walk."
  (:require [cbor.core :as cbor]))

(defn missing-cids
  "`root-cid` : the tree root to resolve.
  `store`     : `{:get-fn (cid -> bytes-or-nil), ...}` (same shape passed to
                `kotoba-client.core/hydrate-via-blocks`).
  Returns every CID reachable from `root-cid` that is not yet in `store`,
  by decoding whatever nodes ARE already present and following their
  `children` links; a not-yet-present node is itself reported missing
  (its own children are undiscoverable until it is fetched)."
  [root-cid {:keys [get-fn]}]
  (letfn [(walk [cid]
            (let [bytes (get-fn cid)]
              (if (nil? bytes)
                [cid]
                (let [node (cbor/decode bytes)]
                  (if (= "internal" (get node "kind"))
                    (mapcat (fn [[_ child-cid]] (walk child-cid)) (get node "children"))
                    [])))))]
    (vec (walk root-cid))))
