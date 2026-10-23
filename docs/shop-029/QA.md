# FantasyHub 0.2.9 / SVFrameItems 0.1.3 QA

Fabric Minecraft 1.21.1, Java 21, Cobblemon 1.8.2. Tests ran on disposable copies of the existing native server/client, with the old SVFrameMMO preference-length limit and unchanged SVFrameLib. No production data was modified.

## Automated

Full FantasyHub build: 263 tests, 257 passed, six existing Skript tests skipped, zero failures/errors. Full SVFrameItems build: 25 passed, zero failures/errors. Total: 282 passed, six skipped. Final incremental complete builds succeeded. New regressions cover closed-screen dispatch, coalescing, subscription expiry, per-player rate limiting, bounded/revisioned preview caching, quote leases and fallback templates across item types while preserving explicit templates. Red logs and test XML are supplied. The initial explicit-template fixture had setId/template arguments reversed; it was corrected before the passing build.

## Spark: original evidence and controlled QA

The supplied production profile contains 42,052 ms of sampled Server-thread time; `FantasyHubCore.state()` accounts for 87.0731%. 240 of 1,200 ticks passed the 100 ms filter. Nested percentages are inclusive. Spark stores the threshold as 100,000 microseconds.

Controlled QA used one connected player, the same copied world, Spark 1.10.109, 60-second captures and a closed Fantasy HUD.

| Metric | 0.2.8 | 0.2.9 |
| --- | ---: | ---: |
| Full capture, bridge tick callback share | 43.6750% | 0.1534% |
| Full capture, `state()` share | 43.0936% | absent from samples |
| Full capture, Shop view share | 26.8729% | absent from samples |
| Warm >100 ms capture, qualifying ticks | 24/1,200 | 8/1,200 |
| Warm >100 ms capture, bridge callback share | 95.05% | 0.6289% |

Runtime counters across the closed-screen window reported zero UI snapshots, zero Shop views, zero preview generation/encoding, zero Pokémon catalog builds and zero large-state JSON characters. The small HUD channel continued updating. Spark was captured before the final Items-only tooltip correction; that correction does not change the closed-HUD code path. Final artifact startup, persistence, counters and upgrade displays were checked separately. The measured shares describe this QA environment, not a guaranteed production CPU percentage.

The first cold 0.2.9 threshold run recorded 53/1,153 heavy ticks. Its dominant remaining path was ToughAsNails temperature/AreaFill; the bridge callback was 1.6570%, with no catalog/state rebuild. The later warm comparison is retained alongside this cold result. No other mod was disabled to obtain the result.

Opening Shop the first time generated/encoded 131 unique previews in one 152.4 ms snapshot. Reopening and switching tabs reused them: one Shop build and 131 encodes remained unchanged. Config reload generated exactly one replacement catalog and 131 replacement previews; they stopped rebuilding afterward. This fixes recurring idle work; initial cold GUI construction can still cause a short tick.

## Native transactions, upgrades and screenshots

- F8 opened the complete HUD; Character, Attributes and Skills remained accessible. Shop and Equipment Upgrade stayed nested screens. All three Shop tabs rendered with native models and localized names.
- Native registry probe: 157/157 item definitions resolve an upgrade template; none are missing.
- Zolda Leggings, previously without a template, upgraded through the actual GUI from +0 to +1. One catalyst was consumed; no unconfigured currency cost was charged.
- Three Ruby Gems split into two +0 and one +1. The upgraded unit has a new instance UUID; the remainder retains the original UUID. Duplicate use of the confirmation consumed one catalyst and applied one upgrade. The explicit gem template remained in effect.
- Full inventory rejected a stacked upgrade; inventory, catalysts and balances were unchanged after removing the QA filler.
- Agito equipment-only bundle purchased with BeastCoin and Kabuto equipment-only bundle with HunterCoin. Duplicate tokens produced one debit/grant. Class stayed Flash. Insufficient BeastCoin did not fall back to HunterCoin.
- Actual Shop GUI EXP Booster purchase charged 500 BeastCoin and zero HunterCoin. Balance display updated correctly.
- Early owned-class switch charged exactly 500 BeastCoin and zero HunterCoin. Normal switching after cooldown was free; requesting bypass after expiry also charged nothing.
- Full inventory retained eight Agito rewards in the durable mailbox; freeing slots and claiming delivered them once and cleared the mail count.
- Source equipment tooltips now show `+level` and effective upgraded stats (native Zolda Boots armor 2 → 2.1). Translated Rider names remain intact; this was verified on the final Items artifact.
- No unresolved upgrade journal remained. Restart/reconnect results are recorded in persistence-check.json.

Screenshots are native Minecraft captures at GUI scale 3, not mockups. The earlier 0.2.8 multi-resolution/localization QA remains historical evidence; this scoped performance fix did not rerun every earlier feature or unrelated WorldBoss/Infinity scenario.

## Limits and existing warnings

External-auth network failures and the pre-existing owo networking `NullPointerException: player` during join appear in both baseline and new QA client logs; the client continues joining and the HUD works. No new FantasyHub/Items startup or rendering exception was observed. Runtime profiles remove server configuration metadata before redistribution; timing samples are retained. Production Spark should be repeated with its real player count.

Items without stats can gain upgrade levels but do not receive invented combat stats. Existing explicit costs/chances/caps/destruction rules are preserved. Ambiguous cost-provider/storage failures retain the existing recovery journal rather than automatically rerolling.
