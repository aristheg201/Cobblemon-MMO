# FantasyHub 0.2.9 / SVFrameItems 0.1.3

- Stop rebuilding the entire HUD/shop/Pokémon catalog every five ticks. Realtime resources/casting use a small packet; screen-specific catalogs subscribe on open and stop on close/disconnect.
- Coalesce GUI requests, dispatch at most one snapshot per server tick, and limit each player to one build per ten ticks. Reuse unchanged serialized screen snapshots.
- Cache immutable native item previews by item/config revision. Reward grants still generate fresh server-authoritative items. Keep balances separate from catalog previews.
- Cache Pokémon catalog/formulas while its screen is open, with a ten-second refresh to detect external integration changes. Never build it during idle HUD updates.
- Preserve purchase/upgrade confirmations while their screens stay open; replay protection, native prices, mailbox and transaction journals remain in force.
- Add operator-only `/fantasyhubperf [reset]` counters for catalog/preview/encoding work and packet sizes.
- Enable upgrades for every SVFrameItems definition: explicit templates win; otherwise use the configured `standard` template, including its chance, costs, level cap and stat scaling.
- Show upgrade levels and effective stats in native source-item tooltips; keep translated Rider item names.
- Upgrade one unit of stackable RPG items at a time, giving a successfully split unit a fresh instance UUID. Require one free slot before attempting a stacked upgrade. Reserve the HUD catalyst before native costs and retain ambiguous attempts for reconciliation.
- Keep full Fantasy HUD navigation, Attributes, Skills, three Shop tabs, native icons and class/currency rules. No WorldBoss, Infinity, AI, MMO or Lib replacement.
