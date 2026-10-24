# FantasyHub performance 0.3.1

Implementation baseline: `91e337b3ef022b5d7a2ddbfea8df1a4bab4951d6`, the delivered 0.3.0 source. Historical bytecode hotfixes are not implementation inputs.

## Capturing and dispatching

The owner thread captures Minecraft, inventory, progression, native formula and currency data. FrozenValues rejects live objects and detaches collections. Immutable rows and shared immutable class definitions cross the worker boundary; Gson transformation and compression execute there. UUID/session generation/domain revision/sequence fence every result. Client generation and ordering checks remain authoritative for presentation. Gameplay authorization remains on the server.

Two default worker threads are partitioned into one realtime and one bulk worker, rather than increasing the pool. A single-worker configuration shares one bounded executor. Realtime completions overtake bulk completions. Bulk admission reserves capacity for realtime work, and the existing rotating fairness pick is retained. Disconnect, dimension change and server stop cancel obsolete work. Connection generations are fenced before dispatch. Completed captures are drained before consuming ordinary resource dirty signals, so continuously changing vitals cannot be perpetually superseded before delivery. Direct class/purchase invalidations still reject superseded results.

Captures remain limited by count and elapsed time. Class rosters are constructed in small slices. Pokémon catalogs capture at most 16 rows by default, with a provider limit of 32. An observed-cost admission model checks whether a bulk capture is likely to fit the remaining tick budget. One native operation cannot safely be preempted: this is an admission bound, not a hard real-time guarantee. Outlier observations are capped in the cost estimator so a GC pause cannot starve a view forever.

Dispatch has independent count, byte and elapsed-time limits (32 packets, 128 KiB and approximately 0.5 ms by default). A single bounded packet may cross the byte/time threshold; the next packet waits. Existing maximum payload/decompression limits remain enforced.

## Immutable definitions and live progression

Class metadata is frozen once per native definition identity. Per-player rows reference it; workers expand the metadata for the existing GUI schema. Class registry/configuration invalidation and the bounded definition cache retain their previous lifecycle.

Cobblemon Integration 0.1.21 exposes optional main-thread `catalogPage`/`catalogRevision` methods. A shared, bounded index and static description cache are invalidated by the native move-adapter revision or configuration identity. Ownership, external skill level, allowed slots, formula values, payment availability and server-defined prices are captured live. The purchase path does not consult display caches. Main-thread reflection resolves these optional methods once per provider class.

Per-session catalog streams cache frozen rows. Class/progression/equipment changes invalidate the stream. Reopening starts at row zero; incomplete streams restart. Active views reconcile the provider revision once per second. Configuration changes during a stream restart it. Protocol 5 sends bounded ordered catalog chunks, which the client presents progressively. A missing chunk requests a resync; duplicate or obsolete snapshots cannot append duplicate products.

## Equipment

`BeltEquipment.equippedAny` resolves each installed Accessories/Trinkets component once and requests all belt candidates once. Selection still ranks item type first, then backend, then native slot order. Native nested-slot traversal is retained. Mutable stacks are never cached across calls, so unequip is visible in the same tick. Empty container reconciliation, equal-stack notifications and empty Trinkets callbacks are ignored. Optional equip/unequip/container callbacks invalidate equipment/combat/vitals/extension/GUI domains without retaining player objects. Reconciliation probes remain available for non-event resource changes.

## Client

Shop tab lists are indexed by the offers-array identity, independently of balance changes. Equipment previews and wrapped text are cached within the screen with explicit invalidation/bounds. Long names use measured Unicode ellipsis with logarithmic width checks. Pokémon filtering is reused until its source array, language or filters change. Upgrade lists and preview stacks are invalidated together. Native ItemStack rendering and authoritative purchase confirmation are retained.

## Transactions

Currency queries, withdrawals, inventory changes and player mutations remain on the owner thread. The existing durable intent/receipt, duplicate protection, rollback and mailbox lifecycle is unchanged. Durable saves are still awaited where the existing transaction contract requires it; new transaction/durable-wait metrics expose that cost. No asynchronous rollback spanning concurrent player mutations has been introduced. Slow storage/providers can therefore still stall a transaction and must be evaluated separately from sync callback time.

## Diagnostics and scope

`/fantasyhubperf` adds CPU time, capture times per domain and GUI scope, probe/dispatch time and transaction/durable-wait time. Existing queue/cache/serialization/payload/error/latency metrics remain. The test-only load probe instruments the identical callback on both 0.3.0 and 0.3.1 and records exact thread allocation and CPU time independently of server MSPT. It also measures actual graphical-client frame intervals. Remove the probe outside QA.

No combat damage, boss difficulty/drop rates, economy prices, class cooldowns, wild Pokémon AI/HP or native Cobblemon battle statistics are changed. No Card World products or extra gameplay engines are introduced.
