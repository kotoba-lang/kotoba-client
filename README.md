# kotoba-client

`kotoba-lang/kotoba-client` is the shared CLJC home for browser-oriented
orchestration over kotoba's content-addressed graph: CID-verified block
ingest and hydrate-to-convergence over injected fetch/store ports. It
mirrors the deleted `KotobaNode.ingestBlock` / `hydrateViaBlocks` JS/wasm
client (git-history-only since `kotoba-lang/kotoba` commit `604896171b`,
2026-07-01 — the whole Rust workspace, including the browser client, was
removed with no CLJC replacement plan). See
`90-docs/adr/2607010930-clj-wgsl-migration.md` Phase 6.

This is the top of the dependency chain landed for that phase:
`multiformats`/`dag-cbor` (existing) → `ipld` → `prolly-tree` → `quad-store`
→ `kqe` → `kotoba-client` (this repo).

Since the tag-42 migration (see the superproject ADR), the walk is
**generic**: `kotoba-client.ipld-hydrate/missing-cids` follows real IPLD
links via `ipld.core/links`, so one walker hydrates prolly-tree nodes,
quad-store commit blocks, and commit-dag commits alike.
`kotoba-client.prolly-hydrate` remains as a delegating compatibility
facade.

## Use

```clojure
(require '[kotoba-client.core :as kc]
         '[kotoba-client.ipld-hydrate :as ih]
         '[prolly-tree.core :as pt])

;; server side: build a tree, keep its blocks in `server`
(def server (atom {}))
(def root (pt/build-tree (fn [cid bytes] (swap! server assoc cid bytes))
                          (sort-by first [["a" 1] ["b" 2]])))

;; client side: start empty, hydrate over injected ports
(def client (atom {}))
(def client-store {:put! (fn [cid bytes] (swap! client assoc cid bytes))
                    :get-fn (fn [cid] (get @client cid))})

(kc/hydrate-via-blocks {:missing-cids ih/missing-cids
                         :fetch-block (fn [cid] (get @server cid))
                         :store client-store}
                        root)

(pt/lookup (:get-fn client-store) root "a")  ;=> 1, resolved entirely client-side
```

`kotoba-client.core/ingest-block` re-hashes every fetched block and throws
on a CID mismatch — untrusted bytes from `fetch-block` are never accepted
into `store` unverified.

## What is NOT in this landing

**IPNS-record signature verification (trustless head resolution)** — the
deleted client's `hydrate-and-query-verified!` resolved a signed IPNS
record to a head CID before hydrating, so an untrusted `fetch-block` port
couldn't serve a stale-but-internally-consistent tree. This repo hydrates
whatever `root-cid` it is given; verifying that the root itself is the
*right* one needs `cacao`'s Ed25519 signing surface, which this landing
does not depend on. Tracked as a follow-up, not fabricated.

A Service-Worker-equivalent request interception (the deleted `kotoba-sw.js`
transparently answering same-origin `datomic.q` fetches from the local
wasm store) is also out of scope here — that is host/browser-glue, not
domain logic, and belongs in a thin adapter once this core is proven.

## Test

```bash
clojure -M:test
```

## License

MIT
