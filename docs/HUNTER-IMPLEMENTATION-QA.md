# Hunter source runtime and original armor — 0.1.5

This is a focused Hunter repair, not completion of the entire plugin port. Gunner, Awakened Gunslinger, Awakened Mage, Mage and the broader source inventory still have unresolved runtime mechanics. The full release gate remains FAIL.

Minecraft 1.21.1, Fabric Loader 0.18.4, Java 21 and real Cobblemon 1.8.0 were used. Builds are local. A real Minecraft client connected through Velocity to the dedicated Fabric backend. No external resource pack was installed. The client contains the original presentation assets.

## Source preserved

The existing `HUNTER` comes from `config2/MMOCore/classes/hunter.yml`. Its seven wrappers are `CLS_HUNTER_HUNTER_*`, retaining the supplied wrapper scaling/cost/cooldown values and the original graphs in `config2/MythicMobs/Skills/hunter_skills_mmocore.yml`. There is no invented second Hunter.

The 24 original base armor entries come from `config2/MMOItems/item/armor.yml`: four `SVFRAME_HUNTER_*` Halfmoon pieces, and four pieces each for Feline, Griffin, Ursine, Kaer Morhen and Wolven. Upgrade/prestige/avatar variants are excluded as requested. Original numeric stats, restrictions, flags and the cumulative Halfmoon set bonuses are retained. Witcher entries retain their original absence of a class restriction; none is invented. Original Witcher geometry and textures are packaged unchanged, including cube pivots/rotation/inflation/mirroring and the Kaer Morhen per-face UVs. The Halfmoon hat uses its supplied model, textures and animation metadata.

## Executed acceptance

| Test | Actual result |
| --- | --- |
| Seven original wrappers | All seven available and cast through MMO's real server runtime |
| Endurance | Bound TIMER passive automatically applies resistance, regeneration and speed |
| Aard | Damage and throw; armor stands excluded before target limit; 21.9 inside / 22.1 outside radius |
| Aard upgrades | Real catalog transaction spends 29 points once, reaches rank30; damage10 → 28.850006 |
| Igni | Repeated native projectiles damage and ignite actual targets |
| Yrden | Fixed purple ring applies slow/blindness/weakness; no damage, matching this Hunter source |
| Quen | Real damage cancellation, position lock, free facing, both immunity and movement lock expire |
| Adrenaline Rush | Actual self damage plus poison/strength/speed |
| Blackhole | Pulls towards the fixed origin and damages there after caster teleports20 blocks away |
| F / keyboard2 | Real input shows six active HUD slots; Aard reduces a native cow100 → 90 |
| Real wild Pokémon | Original Aard, via F / keyboard2, reduces wild Pikachu overworld health27 → 17 |
| Real owned Pokémon | Actual party send-out Bulbasaur remains protected during a confirmed Aard cast |
| Six original armor sets | All24 pieces equipped; source stats/set bonus and idempotent modifiers checked |
| Reconnect twice | Halfmoon max health125.8 remains125.8 on both actual server and client; same continuously running server |
| Death / respawn | Actual death with keepInventory=false retains all four Halfmoon pieces and native max health125.8 |
| Healing with Hunter armor | Health121.144005 → 125.644005; no new damage packet/camera frame and hurtTime remains0 |
| Manual Q drop | Original source hat remains in the selected inventory slot |
| Class preview | Opening class selection focuses current Hunter and renders its actual Halfmoon equipment |

Overworld Pokémon health and formal battle Pokémon HP are distinct Cobblemon state. The wild test uses its native overworld damage path: battle Pokémon HP108 remains unchanged. This does not claim that Aard becomes a legal turn-based battle action. The shared battle/ownership adapter remains authoritative; no ownership, capture or formal-battle state is rewritten by the test.

An actual test failure exposed oversized diagnostic observations when the full source skill descriptions were echoed after pressing F. Diagnostics now send bounded slot identities/timers; a dedicated regression passes. Gameplay packets and HUD skill data are preserved. Another regression exposed target limits being applied before inline conditions; excluded armor stands no longer hide eligible enemies.

## Evidence and reproduction

Real screenshots, an unedited runtime armor video, source conformance, original JSON observations and build reports are retained in `docs/qa/hunter/`. `hunter-runtime-final.json` contains the final seven-skill/rank/range/dense-selector run. `gear-and-reconnect-runtime.json` retains all six equipment scenarios. `reconnect-before.json`, `reconnect-after.json`, and `reconnect-after-2.json` show identical native health and increasing server ticks without restart. The fixture world has doImmediateRespawn=true; the death screenshot is after automatic respawn, not a fabricated death-screen UI. The target-selection fix was installed afterward and the skill scenarios were rerun against the final Mobs binary. Both separate Bridge and bundled Core layouts passed the seven-skill/rank/range/dense-selector runtime runner with the real client through Velocity. Bundled nested JAR hashes match all five verified raw modules.

Module JUnit results: Mobs33, Core6, Client13, zero failures/errors. These counts are not source parity percentages. Source conformance separately checks24 generated base definitions and byte-identical copies of all five Witcher geometries/textures.

On an isolated QA instance, set `SVFRAME_RUNTIME_QA=1` on server and client, connect the real client, then run:

```sh
python FantasyHub/tools/verify_hunter_sources.py --references /path/to/user-packs
python FantasyHub/tools/run_hunter_qa.py --server /path/to/test-server --output /path/to/evidence
```

The runner reads RCON credentials locally from the QA server properties; never publish them. `fantasyhubqa hunter_armor_halfmoon`, `hunter_armor_feline`, `hunter_armor_griffin`, `hunter_armor_ursine`, `hunter_armor_kaer_morhen`, and `hunter_armor_wolven` exercise equipment. These opt-in admin fixtures modify only the QA character/world and are not production player commands.

## Installation boundary and remaining scope

Use matching Core/Bridge + Client0.1.5 and Mobs0.1.1-fabric. Choose bundled Core OR Bridge with the five framework mods. Do not install both layouts together. FantasyHubClient belongs on the player's game; keep it off a dedicated server. The dedicated server must have Cobblemon and its normal dependencies. Keep custom configuration edits; source-pack installation only updates unchanged previously generated files.

This evidence certifies the stated scenarios. It does not certify every Hunter unlock/economy/multiplayer/death/configuration combination or every inventory restriction interaction. The full class/equipment requirements remain in progress pending that wider acceptance. None of the other four named classes is represented as finished merely because it appears in the roster.
