(ns kotoba-client.core
  "Browser-oriented orchestration for kotoba's content-addressed graph:
  CID-verified block ingest and hydrate-to-convergence over injected
  fetch/store ports. Mirrors the deleted `KotobaNode.ingestBlock` /
  `hydrateViaBlocks` JS/wasm client (git-history-only since
  `kotoba-lang/kotoba` commit `604896171b`, 2026-07-01).

  IPNS-record signature verification (trustless head resolution, mirroring
  the deleted `hydrate-and-query-verified!`) is explicitly NOT implemented
  here -- it needs `cacao`'s exact Ed25519 signing surface, which this
  landing does not depend on yet. Tracked as a follow-up, not fabricated."
  (:require [multiformats.core :as mf]))

(defn ingest-block
  "Verify `bytes` actually hashes to `claimed-cid` (dag-cbor codec) before
  accepting it. Returns `bytes` on success; throws `ex-info` on CID
  mismatch (never silently accepts unverified content)."
  [claimed-cid bytes]
  (let [actual-cid (mf/cidv1-dag-cbor bytes)]
    (when (not= actual-cid claimed-cid)
      (throw (ex-info "kotoba-client: CID mismatch on block ingest"
                       {:claimed claimed-cid :actual actual-cid})))
    bytes))

(defn hydrate-via-blocks
  "Walk `root-cid`'s missing blocks to convergence, fetching each via the
  injected `fetch-block` port and ingesting (CID-verified) into the
  injected `store` port, mirroring the deleted `hydrateViaBlocks` loop.

  opts:
    :missing-cids (root-cid, store) -> seq of CIDs still needed to resolve
                  `root-cid` (e.g. `kotoba-client.prolly-hydrate/missing-cids`
                  for prolly-tree/quad-store-shaped trees).
    :fetch-block  cid -> bytes (may throw; not caught here).
    :store        {:put! (cid, bytes -> _), :get-fn (cid -> bytes)}.
    :max-rounds   safety cap on hydrate iterations (default 64, matching
                  the deleted JS client's default).

  Returns the number of blocks ingested this call. Throws if convergence
  is not reached within :max-rounds (never loops forever)."
  [{:keys [missing-cids fetch-block store max-rounds]
    :or {max-rounds 64}}
   root-cid]
  (loop [round 0 ingested 0]
    (let [missing (missing-cids root-cid store)]
      (cond
        (empty? missing) ingested

        (>= round max-rounds)
        (throw (ex-info "kotoba-client: hydrate-via-blocks did not converge"
                         {:root root-cid :max-rounds max-rounds}))

        :else
        (do
          (doseq [cid missing]
            ((:put! store) cid (ingest-block cid (fetch-block cid))))
          (recur (inc round) (+ ingested (count missing))))))))
