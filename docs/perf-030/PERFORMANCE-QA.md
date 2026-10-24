# Performance and QA — FantasyHub 0.3.0 candidate

## Acceptance status

The source refactor is built and exercised on native Fabric servers and a real graphical client. **The complete production acceptance gate is not met:** representative 20-player P95 remains above 50 ms, stable 20 TPS is not established, and isolated maximum ticks remain large. This is a reviewable candidate, not a claim that the entire server is optimized.

## Equivalent workload

Baseline: canonical source release 0.2.9 (`d21ae77`, checkout baseline `9dd9b12`). Candidate: source-built 0.3.0, embedded protocol 4. No ASM/binary hotfix baseline. Each run restores the same copied world/player/configuration fixture. Both use Java 21.0.2, Minecraft 1.21.1, Fabric 0.18.4, Cobblemon 1.8.2, the same SVFrame dependencies, BEconomy, Tough As Nails and WorldBoss. The server loads 135 Fabric mods including dependencies and the QA probe; the graphical client loads 110. This is not asserted to reproduce every mod in the user's production pack.

Hardware: Linux container, 5 available logical processors, cgroup quota equivalent to 4 CPU cores, 16 GiB memory limit; server `-Xmx4G`. View/simulation distances are 3. No graphical clients or compilation ran during the final candidate sampling windows. Test-only flight enforcement was relaxed for headless transport clients.

Native-server network clients: 1, 5, 10, 20, 40. Each phase has a 30-second warmup and approximately 60-second CPU window, with an additional allocation window at 20 players. Actors use skills, open Profile/Class/Shop/Upgrade/Pokémon views, preview bundles, attempt purchases, change equipment, receive damage, change a class, reload commerce, respawn and fight an active Thanos encounter. Failed connection/arena-development runs are excluded. Every accepted CPU sample window keeps its required player count; no client failures occur. Final candidate teardown verifies zero native sessions and zero pipeline sessions after all five phases.

These are real connections/entities and game systems on a native Minecraft server, **not 40 graphical clients or a human counterplay playtest**. The bots do not implement all modded entity metadata or client physics. Their custom-payload totals cover the full actor session, including setup/warmup; they are not FantasyHub-only packet counts for the isolated CPU window.

## Whole-server results

| Players | Mean ms before/after | P95 ms before/after | P99 ms before/after | Max ms before/after | Main MB/s before/after |
|---:|---:|---:|---:|---:|---:|
| 1 | 6.50 / 9.65 | 11.89 / 27.83 | 41.51 / 108.50 | 199.56 / 247.32 | 31.90 / 31.51 |
| 5 | 14.67 / 8.98 | 39.19 / 13.43 | 66.53 / 29.14 | 228.46 / 65.94 | 110.31 / 105.92 |
| 10 | 18.27 / 16.04 | 41.88 / 26.94 | 82.56 / 53.42 | 186.31 / 486.49 | 200.92 / 196.87 |
| 20 | 38.51 / 35.65 | 100.58 / 66.19 | 196.79 / 161.55 | 439.12 / 1035.24 | 362.26 / 357.21 |
| 40 | 53.65 / 47.46 | 86.27 / 73.99 | 183.86 / 164.49 | 428.59 / 642.65 | 665.89 / 668.43 |


At 20 players, Spark last-minute TPS is 18.98 → 19.28. The single-player final run and several maxima regress; no across-the-board improvement is claimed. Exact tick/allocation samples, process CPU, heap, GC totals, player counts and `/fantasyhubperf` output are retained in `comparison.json`, `ticks.json` and `samples.json`.

## FantasyHub-specific attribution

In matched 20-player Spark CPU windows, sampled wall time inside the periodic sync callback is 2304 → 1184 ms, approximately **48.6% less main-thread sampled sync work**. These are inclusive sampled wall times, not an exact CPU stopwatch. The callback names differ because the old monolithic loop was replaced by `NativeSyncRuntime.tick`.

In the separate matched allocation windows, estimated sampled allocations beneath that callback are 1008.5 → 395.8 MB, approximately **60.7% less main-thread sync allocation**. This is not a 61% reduction in total server allocation. Whole-server main-thread allocation in the CPU window remains 362.3 → 357.2 MB/s.

Final 20-player `/fantasyhubperf`: realtime HUD capture P95 0.070 ms; total sync tick-work mean 1.225 ms, P95 7.323 ms and max 83.364 ms. Capture-to-dispatch P95 91.99 ms. No worker or capture errors; queues remain bounded and drain. The 2 ms budget is soft: a native formula/item/provider call and a global GC pause cannot be preempted.

JFR sees 29 snapshot-worker execution samples in the 20-player window and 82 at 40 players, with **zero observed live Minecraft/SVFrame/Cobblemon frames on those workers**. Workers perform detached transformation, Gson and compression. Sampling is a cross-check, not exhaustive proof; immutable-value rejection and owner-thread tests provide additional evidence. Source-level lifecycle tests caught a disconnect cleanup issue during QA; the final rerun includes its main-thread handoff and reconnect identity fix. Earlier candidate measurements are not substituted for final results.

## Remaining dominant owner

JFR finds Tough As Nails temperature code on 2,098 of 2,988 sampled main-thread execution stacks at 20 players (about 70%). The retained Spark call path is:

```
MinecraftServer.tick
  → ServerPlayerEntity/player tick
  → toughasnails TemperatureHandler.onPlayerTick
  → TemperatureHelperImpl.getTemperatureAtPos / proximityModifier
  → AreaFill.fill / expand / checkPassable
  → world/chunk block-state reads
```

Recursive `AreaFill.fill` percentages in flat inclusive Spark summaries must not be added or interpreted as exclusive CPU percentages. Large individual maxima are not all conclusively attributed to this one path; the evidence also retains GC pauses and latency spikes. Tough As Nails is not disabled or binary-patched. FantasyHub's input probing still uses at most the original five-tick interval; responsiveness was not traded for a 20/40-tick refresh.

## Automated and runtime regression evidence

Affected modules: 148 Core and 61 Client tests, no failures. The integrated build also checks 34 WorldBoss and 39 Skript tests (6 existing skips). Total 276 passed, 6 skipped. The final verification build reuses unchanged Gradle outputs; `build-final2.log` contains the executed post-fix build/tests.

Real native transactions pass 14 checks: free starter selection; BeastCoin and HunterCoin class ownership; free owned switching; failed cooldown switch without debit; 500 BeastCoin-only bypass; expired bypass with no charge; duplicate bundle nonce; Agito belt/weapons; owned equipment-only pricing; full-inventory mailbox recovery; Kabuto belt/Kunai Gun; 2-HunterCoin item purchase; 157/157 registered upgrade templates. Real GUI clicks additionally verify the bypass button, charging exactly 500 BeastCoin and zero HunterCoin.

Graphical screenshots show F8's Profile/Attributes/Skills, CLASS/SHOP/BUNDLE, localized native Agito models and tooltips, belt/weapons after scrolling, upgrade inventory, English GUI Scale 3, Vietnamese GUI Scale 2, and a 960×540 layout. Actual F/number-key skill activation renders the local cooldown countdown. Reconnect preserves class, level and balances. Dimension changes leave one live session and no worker/capture errors. Protocol-3 traffic is rejected before player join. Dedicated-server restart/persistence passes with the same Thor class, level 50, 167,000 BeastCoin and 91 HunterCoin after the real GUI bypass transaction (`before-server-restart.json` / `after-server-restart.json`).

## Coverage limits

Native class-definition/name/icon reloads require the documented additional `/fantasyshop reload` step for shop presentation.

Not certified: production modpack parity, 20 human/graphical clients, extended active multi-hour soak, injected slow/failing BEconomy provider behavior, every possible crash/reward-failure interleaving, all arena/boss/SNAP outcomes, and an independent production GitHub Actions run. Destructive SNAP outcomes are not retested by this scoped refactor. Purchases and storage still use the existing authoritative transaction paths; no unsafe provider calls were moved off-thread.

The user's latest `Kn7fO2T0fP.sparkprofile` contains 1,200 ticks with a 100 ms filter but zero included ticks and zero sampled thread stacks. It cannot substantiate a hot-path percentage or a before/after CPU claim. This report uses the new paired native-server windows instead.
