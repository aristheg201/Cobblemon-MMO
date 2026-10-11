# SVFrame / Fantasy Hub — API continuation handoff, 2026-10-11

## Delivery decision and constraints

The user now explicitly requests packaging existing JARs, pushing source, and handing context to the API. This delivery is **not completion of the original full framework specification**. Continue from current authorized branches; do not repeat the audit or replace working systems. Build locally, never with GitHub Actions. Use `[skip ci]` when pushing.

Target: Minecraft 1.21.1 only, Fabric Loader >=0.18.4, Java 21, Cobblemon 1.8.x. Server owns gameplay; client owns presentation. MMO/Lib remain the single stat/progression authority. No Bukkit/Paper runtime. No separately installed resource pack.

User priorities retained: all source classes eventually; Dragonborn before other class expansion; original base armor is required, armor upgrades are unnecessary. Keep original Death Sentence launch/hover/crash timings and mint/white-green colors. Skript scope is only class/dash and related AMK behavior; item/shop/unrelated scripts are unnecessary. Desired workflow: copy original `.sk` into mod config unchanged. HUD: HP at hearts, stamina above HP, mana above food, RPG level at vanilla level position.

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
- Cobblemon: reusable entity adapter, ownership/capture/faint/battle safeguards, actual Pokémon entities from native Mythic-style definitions; real timer mechanic damages Pokémon. Fifteen player-purchasable move adapters have actual purchase/equip/cast evidence. Formal battles remain controlled by Cobblemon.
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
| Cobblemon moves | 936 | 15 validated purchasable player adapters, 921 not certified as working player abilities. |
| Scripts | Historical 40 / 28 enabled; current required scope 3 | Three unchanged compile and targeted live cases pass; no full Skript/addon or original-Paper behavioral certification. |
| Models | 685 authored blueprints | 235 selected converted model/animation resources in DK/AMK/Dragonborn subset; full class/boss/NPC coverage unfinished. |

Remaining: other classes' complete identities/skills/passives/progression; unsupported Mythic graphs/conditions/bosses/spawners/drops; full requested UI/talents/bestiary/quests/NPCs/model previews; additional move-specific casting; script passive/guard/taunt/PvP and Pokémon interaction matrix; atomic cross-provider economy/character/variable crash journal; broad compatibility and performance qualification. Source-valid class filtering was the final compiled change and lacks an additional live-client pass. Do not report these as implemented or as missing-file excuses.

Genuine retained source gaps: `NinjaDamageEvent` is referenced by `config2/MythicMobs/Packs/WorldBossPack2/Mobs/WardenMobs.yml:1` but has no supplied definition or specified filename. Only two of five art-direction references were found. Third-party asset redistribution rights remain unestablished; internal artifact delivery does not certify a public redistribution license.

## Build, tests and API boundaries

Local tools: `/workspace/tools/jdk-21.0.2`, `/workspace/tools/gradle-8.8/bin/gradle`. Latest Lib 21 and Skript 24 tests pass. Earlier Items 24 and Mobs 17 pass; converter 5 pass. No full production certification.

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
