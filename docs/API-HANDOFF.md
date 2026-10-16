# Latest update — Kabuto toggle0.1.8

Manual F close and Henshin recast now return to human; form modifiers/Clock/Hyper/leap clear without replacing items or refilling resources. Corrected client hides the whole dormant suit and helmet.26unit tests and18two-client runtime checks pass. Use FantasyHubClient-0.1.8-human-fix.jar with Core OR Bridge0.1.8. All3JARs uploaded individually; exact links/hashes in docs/KABUTO-0.1.8-DELIVERY.json. No config reset or new full asset pack. Do not use preliminary Client0.1.8.jar. Full-port gate remains FAIL. Broader native skill rerun still has an arena0CastOff slam failure; studio rerun succeeds. Preserve the failed result and investigate before claiming full native regression passes.

## Previous checkpoints

# Latest checkpoint — Kabuto VFX/gun correction0.1.7

Core/Bridge/Client/Protocol0.1.7, assets kabuto-vfx-v8. First-person gun stays as user confirmed; third-person corrected independently. New server-confirmed GUN_FIRE pose and KabutoFx S2C events. Native Minecraft plus11actual Cobblemon ParticleStorm effects; private fixes for upstream flash/metal sprite references. Config FantasyHub/kabuto-vfx.yml, /kabuto reload, F8Settings density. No damage authority on client.

Focused real QA passes: native F/inventory, skills,60two-client VFX checks,8density/damage-isolation checks, actual first/third gun firing frames.25fresh Hub unit tests;33Mobs baseline unchanged. See docs/KABUTO-VFX-0.1.7-QA.md and docs/CONTINUATION_STATE.md. Source/build/config/private assets included in new0.1.7 ZIP; delivery confirmed at https://drive.google.com/file/d/1tNoDTXc7qvmE6aVupm4V--N4IeqHZJu2/view. Code checkpoint1bd806b, receipt docs/KABUTO-0.1.7-DELIVERY.json. Full-port matrix still FAIL.

## Previous deliveries

## Latest continuation — Kabuto / Wolven / Skyrim 0.1.6, 2026-10-16

User's latest corrections are implemented in the code checkpoint: **square pauldrons**, broad silver forehead V/patterned cyan visor, original red Rider helmet, horizontal red/gold Zecter with silver strap, same Gun frame with two reference grip pivots. Actual Blockbench5.2.2 generated seven editable projects in FantasyHub/assets-source/kabuto; all required generated assets are inside the authorized source archive. F while wearing Zecter automatically Henshins Masked; Cast Off switches Rider; reopening F retains Rider. The complete suit is a server-confirmed overlay/stat form preserving actual Inventory NBT. Original actor/movie audio was not provided; six digital cues are original synthesis.

Core/Bridge/Client/Protocol0.1.6, asset revision kabuto-blockbench-v7; Mobs0.1.2. Separate Lib/MMO/Items/Integration remain1.7.2 /1.13.3 /0.1.1 /0.1.18, Skript0.1.0. Final local builds pass10Core+13client+33Mobs tests. All five nested JAR hashes match raw layout, Bridge/Core class bytes identical. Live bundled-Core/real-client acceptance passes native F/key3, actual skill damage, Clock5sec/AIrestore/slowimmunity, kickkillCDreset, Gunprojectile/Kunaibleed, Hyper, repeatHenshin/reloadHP, two-client appearance and two reconnects on the same continuously running server (49.8classHP, F65.8Masked, client/serveragree). Accessories and Trinkets actual API paths pass separately. Wolven3pieces/nohelmet/10%narrowerarms and four Skyrim sets equip/render/realstats are captured. Original18sourcegear definitions and6Kabutoitems are new; Iron reuses5Dragonborn IDs. Armor upgrades excluded by user.

Delivered ZIP, source, configs, JARs and three unedited Minecraft videos: https://drive.google.com/file/d/1cIfHcoxK2aSz8xzbBxEa6ulzKqhbN5T8/view . Receipt docs/KABUTO-0.1.6-DELIVERY.json, report docs/KABUTO-IMPLEMENTATION-QA.md, evidence docs/qa/kabuto. Size39,686,896bytes; SHA25659ffee5916827d355a72ec4965d68531c8379d80a8cf0be10e1e303dbcb5cf95. Source code commits main62841f3 /Mobs1e79e92 are pushed with [skip ci]; this documentation receipt follows those archived code commits. No GitHub Actions used. Core OR Bridge+five raw modules; never both. Skript JAR in both server layouts. Client JAR belongs on players, not a dedicated server.

The full original port remains unfinished; matrix26145records, only5narrow regressions VERIFIED_COMPLETE, release gateFAIL. Broad Kabuto/progression/protection/Hyper cases and original gear policy coverage remain IN_PROGRESS. Live Mythic-root immunity still requires its dedicated runtime case, despite native slow immunity passing. All original class/boss/GUI and upstreamSkript compatibility claims remain subject to the matrix. Continue Gunner/Awakened Gunslinger/Awakened Mage/Mage source graph gaps without repeating the source audit. Latest resumable environment and commands are in docs/CONTINUATION_STATE.md. Do not relabel this delivered checkpoint as 100% production completion.

## Previous checkpoint — Hunter 0.1.5

The latest user prioritizes the existing Hunter and its original gear, then Gunner / Awakened Gunslinger / Awakened Mage / Mage. Stop searching for a second Hunter. Hunter's seven original wrappers now pass focused dedicated-server / real-client scenarios, including source totem/stun/pull, Aard rank30/range/dense exclusions, actual F/2 input, real wild/owned Pokémon,24 original armor pieces with five original Witcher geometries, two continuous-server reconnects at125.8 health, native healing without hurt feedback, and actual death/retained Halfmoon gear. See docs/HUNTER-IMPLEMENTATION-QA.md and docs/qa/hunter. These narrow scenarios do not certify full class/equipment or ecosystem parity.

Development versions are Core/Bridge/Client/Protocol0.1.5, assets reference-hunter-v5, Mobs0.1.1-fabric. Mobs commit d724404; existing Lib/MMO/Items/Integration versions remain1.7.2 /1.13.3 /0.1.1 /0.1.18. Build outputs in the separate module checkouts can be stale: package only the exact verified QA runtime JARs or freshly rebuilt matching sources, and compare hashes. The bundle must match all five verified raw artifacts byte for byte. Never overwrite the already delivered EXP0.1.4 archive.

Vanilla EXP0.1.4 was already delivered at https://drive.google.com/file/d/1BRn6j8ILY4MXGq4nqFCbYaM7ff-omIxC/view . Four real client/server display/mirroring modes passed native orb/Mending/enchant/anvil and non-overwrite tests. Source/push checkpoint main49ee2be, MMO06a1814, Lib77dd638, Items3d79aa7, previous Mobs ef21e77. The user waived only the absent NinjaDamageEvent hook; defined mechanics remain required. Original AMK Skript function/guard/taunt/PvP and formal-battle protections have focused live evidence; full upstream Skript parity still fails.

Next named classes are not finished: Gunner needs conditional-cast semantics, mob bullets/summons, projectile offsets/homing and associated original VFX; Awakened Mage needs its actual orbital/model/aura graphs and source negative-amplifier semantics. Meteor_Of_Doom_CAST exists in the supplied source; its parse failure must not be mislabeled as missing material. Mobs now reports unavailable dependencies before missing references. Keep the global matrix honest and reuse the retained inventory; do not start another audit.

# SVFrame / Fantasy Hub — API continuation handoff, 2026-10-11

## Delivery decision and constraints

The user now explicitly requests packaging existing JARs, pushing source, and handing context to the API. This delivery is **not completion of the original full framework specification**. Continue from current authorized branches; do not repeat the audit or replace working systems. Build locally, never with GitHub Actions. Use `[skip ci]` when pushing.

Target: Minecraft 1.21.1 only, Fabric Loader >=0.18.4, Java 21, Cobblemon 1.8.x. Server owns gameplay; client owns presentation. MMO/Lib remain the single stat/progression authority. No Bukkit/Paper runtime. No separately installed resource pack.

User priorities retained: all source classes eventually; Dragonborn before other class expansion; original base armor is required, armor upgrades are unnecessary. Keep original Death Sentence launch/hover/crash timings and mint/white-green colors. Skript scope is only class/dash and related AMK behavior; item/shop/unrelated scripts are unnecessary. Desired workflow: copy original `.sk` into mod config unchanged. HUD: HP at hearts, stamina above HP, mana above food, RPG level at vanilla level position. F is the native offhand-swap casting toggle; client skill slots only display the server casting state. Purchased skills share native `/skills`, client UI, binding and progression APIs.

## Source locations and branches

Remote: https://github.com/aristheg201/Cobblemon-MMO

| Workspace | Existing authorized branch | Primary module |
| --- | --- | --- |
| `/workspace/lib` | `svframelib-fabric-1.21.1` | SVFrameLib |
| `/workspace/mmo` | `svframemmo-fabric-1.21.1` | SVFrameMMO |
| `/workspace/items` | `svframeitems-fabric-1.21.1` | SVFrameItems |
| `/workspace/mobs` | `svframemobs-fabric-1.21.1` | SVFrameMobs, native Mythic subset |
| `/workspace/integration` | `svframemmo-cobblemon-integration-1.21.1` | Cobblemon integration, FantasyHub Core/Client/Protocol/Skript |

Retained original inputs: `/workspace/references/user-packs/{config1,config2,amk,plugins}`. Migration manifest: `/workspace/artifacts/fantasy-hub-migration-manifest-v2.{json,md}`. Do not publish vendor plugin JARs, premium source scripts/assets, runtime player records, credentials or worlds. Asset/source conversion commands are in `FantasyHub/README.md`; generated resources require the retained local source inputs and are intentionally excluded from Git.

## Completed and verified portions

- Fixed true reconnect max-health loss: cached stat handles now follow current UUID/stat-engine generation; login attaches after NBT load and before normal player sync. Stable modifier IDs prevent duplicates. High-health server/client attributes agree over continuous-server reconnects, death, dimension, class, equipment and temporary-buff transitions.
- Healing uses native heal paths; direct, skill, potion, group and regeneration windows produce no new damage packet, hurt timers or camera shake. Actual damage retains hurt feedback.
- Dragonborn: seven distinct original abilities through source graph execution, nine original base armor definitions, source models/icons/sounds, additive set bonuses and item restrictions. 68 live checks plus seven transition checks. Equipment restoration precedes health clamping at respawn/login/class transitions.
- Death Sentence: source warmup 28 ticks, launch 15, hover 60, descent 8; camera-directed landing ring, original mint/green-white particle colors, real damage and knockup. Dedicated and singleplayer runtime evidence.
- Cobblemon: reusable entity adapter, ownership/capture/faint/battle safeguards, actual Pokémon entities from native Mythic-style definitions; real timer mechanic damages Pokémon. The recovered expansion supplies 155 curated player-move adapters across 18 types and 14 families, verified against the installed provider data; the prior fifteen retain historical live evidence. New Lick/Bite purchase, casting, native VFX, GUI/command upgrades, binding and continuous-reconnect QA pass and are tracked separately in `EXPANDED-SKILLS-QA.md`. Formal battles remain controlled by Cobblemon.
- Core bridge and separate Client: versioned handshake, packaged source visual subset, server-backed character/attributes/skills/settings/class screens and separate Pokémon shop. English/Vietnamese. Presentation-only real client verified.
- Compact HUD at requested vanilla positions; armor/oxygen layer correctly, chat and high-health vanilla toggle verified at two real-client scales.
- Native Skript: three unchanged required source files compile (19 functions, 6 commands, 12 triggers). Eight real-client dash scenarios pass; actual dodge damage cancellation/expiry. Six AMK active casts delegate to original script. Original class fee/cooldown exercised with exact CobbleDollars balances and a narrower actual BEconomy provider test. Persistent source cooldown survived restart. See `SKRIPT-IMPLEMENTATION-QA.md` and `FantasyHub/skript/README.md`.

## Exact coverage and unresolved implementation

| Category | Source / registry count | Actual implementation limit |
| --- | ---: | --- |
| Classes | 36 source, 37 registered including Arcane Mage | Registration is not parity. DK/AMK/Dragonborn have specific behavior evidence; zero classes certified against the complete original-environment suite. |
| Class skills | 237 IDs | 21 distinct DK/AMK/Dragonborn behaviors; seven AMK behaviors now use source Skript. Other class skills remain incomplete. |
| Mythic meta-skills | 4542 unique / 4955 definitions | 4259 parsed, 793 accepted/executable, 3749 unavailable. Accepted is not runtime-tested coverage. |
| Mob definitions | 1079 unique / 1132 definitions | Zero complete original boss encounters; phases, AI, models, loot and spawners unfinished. |
| Cobblemon moves | 936 | 155 curated adapters have provider/model checks; 781 are not offered as functioning player skills. Historical live coverage: 15; current Lick/Bite live regressions pass (see separate report). All 155 have not received live gameplay certification. |
| Scripts | Historical 40 / 28 enabled; current required scope 3 | Three unchanged compile and targeted live cases pass; no full Skript/addon or original-Paper behavioral certification. |
| Models | 685 authored blueprints | 235 selected converted model/animation resources in DK/AMK/Dragonborn subset; full class/boss/NPC coverage unfinished. |

Remaining: other classes' complete identities/skills/passives/progression; unsupported Mythic graphs/conditions/bosses/spawners/drops; full requested UI/talents/bestiary/quests/NPCs/model previews; additional move-specific casting; script passive/guard/taunt/PvP and Pokémon interaction matrix; atomic cross-provider economy/character/variable crash journal; broad compatibility and performance qualification. Source-valid class filtering was the final compiled change and lacks an additional live-client pass. Do not report these as implemented or as missing-file excuses.

Genuine retained source gaps: `NinjaDamageEvent` is referenced by `config2/MythicMobs/Packs/WorldBossPack2/Mobs/WardenMobs.yml:1` but has no supplied definition or specified filename. Only two of five art-direction references were found. Third-party asset redistribution rights remain unestablished; internal artifact delivery does not certify a public redistribution license.

## Build, tests and API boundaries

Local tools: `/workspace/tools/jdk-21.0.2`, Gradle 8.8 for the framework/FantasyHub, Gradle 9.2.1 for Cobblemon Integration (Loom 1.14.10). Latest Lib 25, Client 4 and Skript 24 tests pass; Lib includes four regression cases for inventory overlay click order. Earlier Items 24 and Mobs 17 pass; converter 5 pass. No full production certification.

Build matching Lib first, copy its JAR into the integration dependency path, build MMO/Items/Mobs/Cobblemon integration, then:

```sh
JAVA_HOME=/workspace/tools/jdk-21.0.2 /workspace/tools/gradle-8.8/bin/gradle \
  :skript:build :core:build :client:build \
  -PframeworkBundle=/workspace/scratch/skript-framework-jars \
  -PsuppliedScripts=/workspace/references/user-packs/config2/Skript/scripts \
  --offline --console=plain --no-daemon
```

`frameworkBundle` nests all five framework mods in Core. Without it Core is the bridge requiring separate mods. Install exactly one option, never both. Core/Client already nest Protocol. The rebuilt Lib contains new `ScriptCompatibilityHooks`; its version label is unchanged, so verify delivery hashes instead of mixing old same-version JARs.

Server compatibility hooks: `ScriptCompatibilityHooks` mediates source class policy/menu actions. `ReferenceClassSkillRuntime.ScriptAdapter` gives original AMK script behavior ownership. Entity interactions use `RpgEntityAdapters`. Client receives server state and semantic presentation messages; the `close` screen action is server-sent. Native MMO remains authoritative for mana/stamina/cooldowns and stats.

Real runtime: `/workspace/runtime-server`, `/workspace/runtime-client`, `/workspace/runtime-client-two`, X displays `:99` and `:100`. Do not copy live userdata or launch env into public source. The running server uses the preceding runtime build; the delivered final selectable-class hook has build evidence only. Enable `SVFRAME_RUNTIME_QA=1` exclusively for operator test environments; leave unset in production. Read RCON credentials locally from server configuration without logging them.

Evidence: `/workspace/qa-evidence/{dragonborn,compact-hud,skript}`, committed `docs/qa`, and delivered Runtime-QA ZIP. Original source hashes are retained. No screenshots are generated mockups. Runtime providers and QA credits are recorded accurately; test credits are not defaults.

## Next API turn

Read this handoff and current branch status, verify artifact hashes, then test the final source class filtering/close-screen paths against the continuously running server after deploying the matching new Lib/Core/Skript. Preserve current stats and source behavior. Continue the outstanding implementation without re-auditing the source. Do not claim 1:1 Skript/Mythic compatibility before actual conformance passes.

## Expanded skill and upgrade continuation

The uploaded Windows ZIPs contain artifacts and config but no source. Their own mod classes were remapped and recovered from bytecode, reconciled with this workspace source, then rebuilt locally. The installed Cobblemon moves.js matches the uploaded provider audit SHA256 `7f3ffc8ace9de6dc86048006c29c9ae2dc891b3a83afa8b9e7d682b192f2fbae`. Do not replace the current source with the older artifact-only ZIPs.

Three upgrade defects were identified: GeneratedInventory displayed the last overlapping item but dispatched clicks to the first (native skill-list slot 15 is both a level preview and upgrade button); PlayerData's public upgrade/bind APIs and command suggestions still excluded learned external skills; the client upgrade handler resolved an unowned class fallback instead of the purchased external definition. Owned definitions now take priority, including parameter/lore level lookup. Click dispatch now resolves the top visible overlay; public progression/binding and native command listings use PlayerSkillCatalog while explicitly named class-scoped primitives avoid recursion and accidental external unbinding. Successful upgrades save the point balance and external level, preserve configured maximum levels and reject unowned skills or insufficient points. Client upgrade controls show current skill points and localized rejection reasons.

All visuals remain presentation-only; the server owns F casting mode, slot/input mapping, damage, costs, cooldowns, purchases and levels. Cobblemon action-effect plans and native Snowstorm packets supply move VFX; Bite/Lick additionally have bounded Minecraft particle accents. RPG adaptations do not claim formal turn-based move parity.
