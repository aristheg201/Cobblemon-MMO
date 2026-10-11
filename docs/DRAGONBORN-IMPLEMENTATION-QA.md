# Dragonborn implementation and actual Minecraft QA — 2026-10-11

This delivery advances the existing Fabric ports. It does **not** finish the entire Fantasy Hub specification, all source classes/bosses, or full Skript language/addon compatibility. The later native Skript milestone is documented in [SKRIPT-IMPLEMENTATION-QA.md](SKRIPT-IMPLEMENTATION-QA.md). The user's latest priority is Dragonborn's original skills and base armor; no additional armor-upgrade workflow is required. The retained source inventory was used without repeating the audit.

## Original Dragonborn content

The original `config2/MMOCore/classes/dragonborn.yml` supplies seven skills: Dragon Slayer, Unrelenting Force, Fire Breath, Whirlwind Sprint, Become Ethereal, Battle Cry and Storm Call. The MMOCore variant of `dragonborn_skills_mmocore.yml` takes precedence over alternate copies. Its fifteen referenced meta-skills compile and execute through the native Fabric Mythic graph engine. Costs, cooldown formulas, level requirements, warmups, selectors, repeated effects, source particles and five supplied sound recordings are retained.

- Dragon Slayer executes automatically from the original 80-tick TIMER slot; it refreshes Strength, Resistance and Fire Resistance for 120 ticks.
- Unrelenting Force executes its warmup and swept sonic projectile, real damage, slow and knockback. Source `throw` units are converted to Minecraft velocity rather than applied literally as block-per-tick values.
- Fire Breath executes a distinct flame projectile and real ignition.
- Whirlwind Sprint executes repeated camera-directed lunges, a moving damage/ignition area and its source fall-damage ward.
- Become Ethereal cancels actual damage during its ward and hides the actor and equipment from other players through tracking packets. Tracking, invisibility and damage return when it expires; the server entity remains present.
- Battle Cry applies the source package of eight buffs, actual absorption, particles and sounds.
- Storm Call emits seven separate timed cosmetic lightning strikes. Each strike executes its own real area damage, throw and slow. Cosmetic bolts avoid unintended vanilla lightning damage/fire.

The original old four-piece set and the newer five-piece collection (hat **or** helmet, chestplate, leggings and boots) provide **nine base item definitions**, all tested at upgrade level zero. Source models/icons, two armor families and the authored horned hat geometry are packaged in the client JAR. The fourteen source prestige/avatar records are retained as configuration; their upgrade behavior is outside this priority and is not claimed tested.

The Items runtime preserves source class restrictions, dye/model metadata, unbreakability and disabled drop/death-drop/enchant/crafting/smelting/smithing/repair policies. The 2-piece and 4-piece thresholds **add together**: the old set contributes 55 flat health and the new set 65. Stable modifier IDs make repeated equipment refresh idempotent. Source stat precision is retained, including 0.024 knockback resistance. Imported equipment suppresses vanilla leather attribute bonuses so they cannot duplicate the authoritative RPG stats. Tooltips now use native styled text and English/Vietnamese stat labels.

Retained death inventory is transferred and equipped before MMO's respawn attribute attachment. Pending retained inventory is also restored during early login. This fixes the intermediate low-current-health respawn caused by applying set bonuses only on a later tick. The same equipment restoration now precedes current-health clamping on class changes and live definition reloads. A late transition test exposed that saved Dragonborn health was clamped to its class-only base during a return from DK; the new callback restores the complete maximum before restoring saved current health.

## Verification

Java 21, Minecraft 1.21.1, Fabric Loader 0.18.4 and actual Cobblemon 1.8.0 were used. Builds ran locally; GitHub Actions were not used.

| Test | Actual result |
| --- | --- |
| Seven Dragonborn source abilities and three armor combinations | **68 boolean checks pass** in `dragonborn-runtime.json`; all nine scenarios pass the public runner. |
| Source damage/targeting | Real 500-health cow targets take damage; fire/sprint ignite; Force throws; Sprint moves the real player; Storm damages seven distinct target positions. |
| Costs/cooldowns | Six active source casts retain zero mana/stamina costs, establish real cooldowns and reject immediate repeat casts. |
| Ethereal | Real damage is cancelled, invisibility is present, both expire, and damage becomes effective again. A second real client records removal/restoration of the actor and armor. |
| Original base armor | Three combinations apply exact cumulative bonuses; native material modifiers are absent; drop/enchant restrictions and unbreakability pass. Actual models are captured in inventory and class-preview screenshots. |
| Continuous-server reconnect | Three actual logout/rejoin cycles on the **same PID 53556** retain actual server/client maximum health **137.35** and the same four item instance UUIDs. No server restart is used. |
| Death/respawn, keepInventory=false | Four original item UUIDs survive exactly once; actual maximum and current health are both **137.35** before normal post-respawn gameplay. |
| Dimension / class / temporary buff | Actual server/client maximum remains 137.35 through Nether and Overworld travel. A temporary modifier raises both to 157.35 and expires to 137.35. Switching to DK removes the Dragonborn armor bonus; returning restores it and the saved current health (47.05 → 47.15 with normal regeneration). Seven transition checks pass. |
| Healing regression | Direct, native skill, potion, group and regeneration paths all increase actual health with **zero** added damage packets and hurt-camera frames. Actual damage adds one damage packet and legitimate hurt-camera frames. |
| Dedicated presentation client | A real second client connects with **FantasyHubClient only**, no Core or raw SVFrame framework JARs; its Vietnamese GUI, source armor preview and HUD render. |
| Existing framework regression | Death Sentence 11, DK/AMK adapters 21 and Cobblemon entity/battle integration 17 checks pass again on the updated server. Earlier catalog/shop evidence remains separately identified. |
| Unit tests | Lib **21**, Items **24**, Mobs **17**: zero failures. MMO native core smoke and Cobblemon Integration local build pass. |

Screenshots and recordings come from the running game. `seven-skills-native-runtime.mp4` records the opt-in administrator scenario runner; the separate `unrelenting-force-player-keybind.mp4` records actual player F9 activation. F9 is a QA binding, not the distributed default. Test setup commands modify only dedicated test characters and are gated by `SVFRAME_RUNTIME_QA=1` and operator permission.

To reproduce the seven-skill/base-armor tests, connect an operator test client to a running QA server and run:

```sh
python3 FantasyHub/tools/run_dragonborn_qa.py \
  --server /path/to/server --player QAPlayer --output /path/to/evidence
```

Set `FANTASYHUB_QA_RCON_PASSWORD` in the local process environment. Credentials are never written to reports. The runner changes class, level, loadout and armor, and creates a stone test arena. The RCON reader waits for the first command reply before sending a nonempty response boundary, preventing vanilla packet coalescing and stale empty-command responses from corrupting the observations.

For lifecycle reproduction, keep the dedicated server running, equip the base set, disconnect/reconnect three times, inspect the real `minecraft:generic.max_health` attribute and client telemetry, then die with `keepInventory=false` and respawn. `dragonborn-lifecycle.json` contains server PID, health, armor UUIDs and actual client observations for each stage. Healing comparisons isolate fresh telemetry rows and allow the native HEAL skill's authored two-second cast delay to complete.

## Migration coverage and unresolved work

The source inventory contains **36 classes / 237 class skill IDs**, **4542 unique meta-skills**, **1079 unique mob definitions**, **40 Skript files / 28 enabled**, and **685 model blueprints**. All 36 class definitions are imported into the configurable registry; the existing Arcane Mage makes 37 total entries. Definition registration does not establish playable parity for every class. Dragonborn's seven graphs and the fourteen existing DK/AMK adapters have runtime evidence; **zero classes are certified against a complete original-environment conformance suite**.

The Mythic compiler parses **4259** unique definitions and accepts **793** graphs; **3749** of the 4542 source definitions remain unavailable, including parse failures. `mythic-compatibility.json` gives exact per-definition reasons and dependencies. The accepted count is compiler coverage, not a claim that all 793 were runtime tested. Complex source bosses, ModelEngine entities, encounters, spawners, loot and unsupported mechanics are unfinished; **zero complete original boss encounters** are claimed. The existing Pokémon targeting/battle protections remain intact. Fifteen player-purchase move adapters are validated out of the 936 runtime moves.

**Updated delivery:** SVFrameSkript-0.1.0 now interprets the three required unchanged class/dash scripts. They compile and have bounded native runtime evidence, documented in [SKRIPT-IMPLEMENTATION-QA.md](SKRIPT-IMPLEMENTATION-QA.md). The historical inventory remains 40 files / 28 enabled; unrelated item/shop scripts were excluded by the user. Complete Skript/addon conformance remains unfinished. Bukkit plugin JARs cannot run unchanged on Fabric.

Dragonborn's source mastery tree and progression/unlock policy are imported, but quest completion, mastery allocation, premium unlock purchase/permissions, class-switch economic transactions and respec paths are not certified by the new Dragonborn runtime suite. The Dragonborn-specific suite did not use BEconomy; a subsequent native Skript class-switch test runs the real BEconomy primary provider, as documented in the Skript report. Missing configured providers fail closed. A crash-atomic journal coordinating external economy and character persistence remains unfinished. Mixed item-policy combinations and exhaustively all crafting/repair variants are not certified.

Other class/boss previews and the complete talents, bestiary, equipment, quests, NPC and richer HUD/animation interfaces remain unfinished. Only two of the five requested art-reference images were found in the supplied folder. The exact `NinjaDamageEvent` definition referenced at `config2/MythicMobs/Packs/WorldBossPack2/Mobs/WardenMobs.yml:1` is absent; the reference does not specify its filename. Other unimplemented features are not described as missing files.

Vendor plugin JARs and proprietary source/assets are excluded from Git. Local generated assets preserve the supplied originals for inspection; the selected exact input paths and hashes are in the asset/source-pack manifests. No distribution license for those third-party inputs was established during this task.
