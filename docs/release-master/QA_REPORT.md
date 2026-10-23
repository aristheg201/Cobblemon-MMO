# Release QA — FantasyHub 0.2.5 / WorldBoss 0.2.0

## Environment and method

Minecraft Fabric 1.21.1, Java 21, Cobblemon 1.8.2, the rebuilt native SVFrame modules, and the unmodified official BEconomy distribution were run in isolated copies of the existing server/player data. Two real Minecraft clients were used where another player was required. Screenshots and videos come from the running client under Xvfb/software OpenGL. The local proxy used offline test accounts because Mojang authentication/key requests were unavailable in this environment. No production player data was mutated.

Operator-only probes were enabled with `SVFRAME_RUNTIME_QA=1`. They create fixtures, issue native purchases, force recorded SNAP outcomes, and advance boss health/phases. They do not replace the native economy, class engine, Pokémon storage, battles, rendering, damage or encounter lifecycle. Do not enable these probes on production.

## Automated build

The complete Gradle wrapper build passed for Lib, MMO, Items, Mobs, FantasyHub and the Cobblemon integration. Native MMO/Items CoreSmoke and Cobblemon IntegrationSmoke passed. JUnit: **347 tests, zero failures/errors** (Lib 34, Items 24, Mobs 33, FantasyHub 237, integration 19). Full logs and JUnit XML are included in the QA archive. The optional move-provider semantic audit explicitly reports NOT_RUN and is not counted as a pass. GitHub Actions was not executed remotely.

## Native runtime evidence

- Dedicated server and client cold starts, version/asset handshake, reconnects and repeated server restarts. Native class registry loaded without the mixin-package or missing `agito_belt` failures.
- Thirteen commerce scenarios passed: first free class, BC and HC class purchases, free ready switching, cooldown rejection, BC-only bypass, expired-cooldown request with zero charge, both item payment paths, insufficient funds, duplicate requests, owned equipment-only bundle, full-inventory mail recovery and unowned bundle entitlement without class activation. A real GUI confirmation purchased a class with HC while preserving the active class and BC balance.
- Invalid HC=1, BC=499 and disabled item offers were rejected from the authoritative catalog with no debit or reward. Invalid configuration was logged. Positive switch cooldown and pending mail survived restart; repeated claims did not duplicate items.
- Mana/stamina reached 50 each; level 51 allocations were rejected. Resource maxima rose by 250 each and allocations persisted across restart. Locked class skill activation was rejected.
- Strange portal accepted an air destination at 14.99954 blocks, with a 20-second visual zone. Real screenshots show full-size player heads/skins/capes for Ikonn/astral effects and Flash echoes. Thor, Strange, Spider-Man and Batman showcases captured eight successful skill casts and their actual VFX.
- All eight configured bosses spawned and completed valid native victories with persisted once-only loot state. Separate scheduled and personal encounters ran concurrently; personal Thanos had 5,500 HP and owner restrictions. Tickets/reservations and cleanup were inspected in the native ledger.
- Thanos footage covers punch/beam/rain and the 50% ultimate charge; cover blocked the shockwave. Collector footage covers all four phases, stasis, positive-buff removal and absorption, minions, gravity, mind control and elemental sweeps. Boss phases were accelerated with operator damage; this is not a full raid balance benchmark.
- Repeated Thanos victories using the unchanged 15% Stone/20% Gauntlet configuration produced an actual Gauntlet reward. A repeated claim did not duplicate it.
- All ten forced but durably recorded SNAP outcomes completed on copied data with two clients. Party/PC knockout outcomes preserved UUIDs, ownership and slots while setting HP to zero. Pre-mutation snapshots and native storage writes were verified. Normal Cobblemon auto-healing remains active.
- Pokédex loss survived restart: the count stayed 4; existing party/PC UUIDs and slots remained intact. Exact pre-existing Pokémon are excluded from the automatic startup reconstruction only; new encounters can still discover species.
- SNAP currency reward added 100,000 BEconomy Secondary. The lethal user outcome incremented the native death counter even while holding Time Stone. The custom SNAP carousel was captured with the supplied original item assets and no ShadowCrates dependency.
- All six Stone active effects passed real mouse-input checks: Power dealt 35 damage, Reality healed, Mind/Time froze the target, Time applied buffs, Soul rejected incoming damage, and Space moved the player. The native item-use callback preemption regression was reproduced before the phase-ordering fix.
- Missing-six SNAP activation consumed no cooldown; actual sneak-right-click with all six Stones reserved one outcome, displayed the carousel and completed. A personal ticket used by mouse initialized an owner-isolated encounter and was consumed once, then aborted cleanly. A purchased catalyst used by mouse upgraded existing hero armor from +0 to +1 and consumed one catalyst. All 22 generated hero armor definitions reference the existing native standard template.
- Wild temperament probes confirmed Friendly refusal to retaliate, Neutral retaliation against the actual attacker, and Hostile/Alpha attack preparation. The fixture temporarily used the shipped defaults and restored administrator policy afterward.
- RPG health probe: a level-30 Rattata entity scaled from 17 to 204 HP, while native Pokémon HP/max stayed 59/59; suspension returned entity max to 17 and restoration returned it to 204. Owned/boss exclusions were checked.
- A real native Pokémon battle started and ended in victory, blocked overworld AI/damage during battle, raised class level 50 to 51, and rejected duplicate victory EXP. An earlier fixture failure was traced to the existing guild level cap; the corrected legal level-10 fixture passed.

## Limits and operation

This is local functional QA, not a production concurrency/load test or an external Microsoft-authenticated session. Third-party equipment model combinations and arbitrary administrator boss configurations have not all been exercised. Cross-store I/O ambiguity is retained in transaction journals for administrator reconciliation; exhaustive crash/fault injection has not been performed. Never guess or replay an ambiguous payment or SNAP outcome.

The evidence includes earlier failed probes and before-fix screenshots/logs for diagnosis. Those are not passing results. Consult the named final JSON reports and final build logs, and follow INSTALLATION.md to merge configuration without replacing existing balances or player data.
