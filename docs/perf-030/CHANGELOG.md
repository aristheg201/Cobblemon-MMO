# 0.3.0

- Replace synchronized global HUD rebuilds with staggered, revision-based domain sync.
- Add bounded immutable snapshot processing and off-thread JSON/catalog transformation/compression.
- Add lifecycle/version fences, GUI cancellation, fair coalescing and backpressure.
- Cache skill/profile/Pokémon presentation, catalog templates and native item previews at their dependencies.
- Keep commerce inactive while closed; reduce repeated balance queries and quote lookup cost.
- Synchronize cooldown deadlines and animate them locally without repeated definition packets.
- Preserve native CLASS / SHOP / BUNDLE rendering, currencies, purchases, mailbox and class rules.
- Cache optional belt API reflection metadata; do not change belt ownership/equipment behavior.
- Add reproducible QA harness sources, bounded queue/thread tests and real native-server load evidence.

Install both client and Bridge together: embedded protocol 4 is incompatible with the old protocol 3.
