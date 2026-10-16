# Kabuto, Grandmaster Wolven and Skyrim — 0.1.6 checkpoint

Minecraft 1.21.1, Java 21, Fabric Loader 0.18.4+, Cobblemon 1.8.x. Built locally; no GitHub Actions. This report certifies the stated scenarios only. The full ecosystem release gate remains **FAIL**.

## Delivered behavior

A Kabuto wearing the real Zecter belt automatically enters Masked Form when native F enables MMO casting. Cast Off switches the authoritative form to Rider. Reopening F preserves Rider. The client renders the complete server-confirmed suit; physical armor and inventory remain intact. The form grants actual server stats, not cosmetic health. Repeated Henshin and `/kabuto reload` replace modifiers without losing current HP. Removing the belt, changing class, death, dimension change or disconnect clears temporary transformation state.

Seven actives: Henshin, Cast Off / Rider Jump, Clock Up, Rider Kick, Kunai Mode, Kunai Strike and Hyper Form. Zecter Bond is the passive. Skills use the existing MMO catalog/loadout, resources and cooldowns. Base cooldowns are 2/12/45/20/0.5/1/60 seconds, configured in `config/FantasyHub/kabuto.yml`; class unlocks are in `config/SVFrameMMO/classes/kabuto.yml`. Hyper unlocks at level 100. Gun/close-combat mode can also be toggled with shift-right-click. A Masked player holding the weapon and an offhand shield can use vanilla shield blocking.

The belt uses the actual public Accessories or Trinkets belt API, contributes once to the existing stat owner and renders at the waist. Both APIs were exercised separately. This does not certify installing both accessory mods simultaneously or moving existing accessory data between them.

## Models and authoring

Seven editable Blockbench 5.2.2 projects are retained in `FantasyHub/assets-source/kabuto`. They were opened/exported through the real desktop editor's native model API. The original supplied 30-cube Rider helmet is retained without rounding its unsupported Java-item rotations. Newly authored Masked geometry has a broad silver forehead V, patterned cyan visor/red gasket, jaw vents and **square** silver pauldrons. No round shoulder discs. The horizontal red/gold Zecter has a silver strap and left-facing gold fork. Both close-combat and Gun states use the same 45-element weapon frame with different grip pivots, matching the supplied hand-placement references. Shading uses an original 256-pixel metal atlas, patterned lenses and a 64-pixel undersuit.

Runtime assets are inside the client JAR. No separate resource pack or Blockbench installation is required by players. Six original synthesized digital announcer cues are included; original actor/movie recordings were not supplied and are not claimed. `blockbench-square-masked-editor-preview.png` is explicitly an editor preview. Every image under `presentation`, `native-f`, `gear` and `reconnect` is captured from actual running Minecraft; videos are unedited runtime captures in the delivery ZIP.

## Source gear

The new pack has 24 new item definitions: six Kabuto items and 18 source-defined Wolven/Skyrim IDs. Skyrim Iron reuses five existing Dragonborn definitions rather than adding weaker duplicates. All scalar stats, restrictions, flags and set definitions are reconciled with the retained `config2/MMOItems/item/armor.yml` and `item-sets.yml`. The user excluded equipment upgrade progression. Alternative HAT/HELMET IDs remain available; live gear checks below use HAT where applicable.

Grandmaster Wolven has exactly three supplied pieces, no helmet. Arm width/depth are multiplied by 0.9. Four Skyrim presentations are packaged: Iron, Ebony, Daedric and Dragonbone, using their supplied textures/models. The dark Dragonbone helmet texture is present in the supplied source and is preserved.

## Final artifact QA

| Scenario | Observed result | Evidence |
| --- | --- | --- |
| Local build/tests | Core 10, client 13, Mobs 33: zero failures/errors/skips | `qa/kabuto/build-verification.json` |
| Artifact equivalence | All five nested framework JAR hashes match separate layout; all Bridge/Core class bytes identical | build verification |
| Actual F and bound key 3 | Masked 65.8 max HP; native Cast Off -> Rider; reopen F retains Rider | `qa/kabuto/native-f/verification.json` |
| Inventory preservation | Complete server Inventory NBT equal before/after Henshin and Cast Off | native F commands and verification |
| Native belt | Six distinct equipment definitions; belt SKILL_DAMAGE 5, weapon attack 7 | `qa/kabuto/native-skills/kabuto-runtime.json` |
| Henshin/reload | Repeat Henshin retains 65.8 current HP; reload retains 65.8 current HP | native skill report |
| Cast Off | Immediate target 200 ->191.6; separate six-block landing victim ->187.4 | native skill report |
| Clock Up | Movement 0.300000004, attack speed 12, slow rejected, real mob AI frozen without persisting NoAI; expired/restored after 100 ticks | native skill report |
| Rider Kick | Real 10-HP victim defeated, cooldown immediately reset | native skill report |
| Gun / close combat | Real tick projectile damages target; alternate mode has direct damage plus later bleed | native skill report |
| Hyper | Max-level Hyper and Hyper Clock accepted; server-confirmed presentation captured | native skill report and presentation |
| Two real clients | Tracking observer sees form, belt, gun firing pose and alternate grip | `qa/kabuto/presentation` and runtime video |
| Continuous reconnect | Two reconnects through Velocity, same server PID 22948: real class max HP 49.8, F restores temporary Masked 65.8; client/server agree | `qa/kabuto/reconnect/verification.json` |
| Wolven | Three equipped pieces, empty helmet slot; actual max HP 51.3, armor 22.07 in Hunter-level-30 fixture | `qa/kabuto/gear/equipment-stats.json` |
| Skyrim | Four pieces each: Iron HP37.8/armor4.87; Ebony HP72.8/armor30; Daedric HP73.8/armor30; Dragonbone HP131.8/armor30, same fixture | gear report and screenshots |
| Vanilla shield | Actual right-click block, shield damage5, gauge/energy +20 in prior same-code callback scenario | `qa/kabuto/shield-full-inventory.json`, retained earlier runtime evidence |

The continuous reconnect test deliberately clears temporary transformation state on disconnect. It verifies persistent class health remains above 20 and F deterministically reapplies the temporary +16 modifier. It does not claim temporary buffs persist across logout.

## Reproduce

Enable `SVFRAME_RUNTIME_QA=1` only in an isolated QA server. Connect the real matching client through the proxy. `execute as QAPlayer run fantasyhubqa kabuto_prepare` installs the isolated level-100 fixture and real belt. Use `FantasyHub/tools/run_kabuto_casting_qa.py` with a fresh untransformed connection for native keyboard F/3 acceptance. Use `run_kabuto_qa.py` for server gameplay/real-screen capture. Both read RCON credentials locally without publishing them. Camera arena coordinates and the exact commands are retained in the evidence. Remove test fixture entities afterwards; do not enable QA hooks on a production server.

For authoring, open Blockbench with remote debugging port9223, run `package_additional_gear.py --references <extracted-additions>`, then `run_blockbench_authoring.py`; that runner uses native Blockbench APIs and refreshes the asset index. Normal source-package builds consume the included generated resources without requiring the private source archives. See delivery `BUILD.sh` for local Java21 module order.

## Remaining verification and full-port limitations

The broad Kabuto feature entry remains IN_PROGRESS: every level-up/upgrade, protection/PvP/battle transition, all damage immunities and every Hyper dash collision case have not passed a complete runtime acceptance matrix. Native slow immunity and mob AI restoration pass; the new Mythic stun release guard compiles/tests but still needs its dedicated real Mythic-root acceptance case. Original armor alternative HELMET variants and every death/drop/crafting flag are not all runtime-certified. No combat-load performance claim is made from this small two-client test.

The full original classes, bosses, client interfaces, Mythic configuration semantics and upstream Skript parity remain unfinished in `PORT_COMPLETION_MATRIX.json`. In particular Gunner, Awakened Gunslinger, Awakened Mage and Mage still require source-graph work. SVFrameSkript 0.1.0 is the existing native runtime, not a claim of complete upstream Skript compatibility. Do not present this checkpoint as the final 100% production port.
