# Fantasy Hub implementation candidate — 2026-10-10

This milestone is **not a production-complete port**. Local builds and the verified native behaviors below are real. The existing source audit is retained; it was not rerun for this implementation continuation.

## Implemented changes

- Reconnect fix: public StatInstance handles now resolve the current UUID/stat engine generation. Previously the public profile survived logout but its cached native stat instance did not; reconnect mutated a detached instance while the current engine published the default 20 HP. Early PlayerManager login binding runs after saved player NBT loads and before the first normal connection packet. Repeated registration uses stable modifier IDs.
- Actual server/client max health stays synchronized. The previous health-attribute packet mask mixins are disabled. The client HUD renders the actual attributes; no cosmetic health workaround is used.
- Healing uses native healing paths. QA observes actual damage packets, player hurt timers and GameRenderer hurt-camera calls.
- Death Sentence keeps the source 28-tick warmup, 15-tick launch and 60-tick hover, then lands once. Mint body dust (#a2f8cb → #0eaf9b), near-white/green targeting ring (#fefffc → #d0ffb3), wing sequences, blade bone anchors, rupture animation/tints, source sounds and knockup execute in Minecraft.
- A reusable entity adapter protects Pokémon ownership, capture/busy state, fainting and formal battles. Both targets and Pokémon actors are guarded. Type effectiveness comes from the installed Cobblemon Showdown chart; missing charts fail closed.
- Native Mythic-style mob factories create actual PokemonEntity instances from species/form/aspect properties. Entity-in-radius selectors include eligible Pokémon. Encounter stats use persistent modifiers over Cobblemon's intrinsic base attributes, preventing Cobblemon's delegate from resetting configured boss health.
- Class catalogs exclude unconfigured, unowned registry skills; explicit grants and purchased abilities remain visible. Active/passive category formulas are enforced on binding. The source TIMER metadata is preserved, and the public passive containers are now called on the actual server tick with cast-specific parameters.
- The separate client JAR includes authored models, textures, icons and sounds without a separately installed resource pack. A versioned handshake verifies the presentation contract and packaged resource hashes.
- Character, attribute, skill, settings and separate Pokémon shop screens send validated server requests. Attribute pagination, shop filters/scrolling, persistent loadouts, real purchases, cooldown HUD, experience and four authoritative resources are implemented. Native framework translations ship with the client in English and Vietnamese.
- Source helper cubes without textured faces now produce empty render models while retaining animation identities and anchors, eliminating vanilla model parse failures. GUI text is rendered after a single background blur. The duplicate MMO idle action bar is suppressed when the presentation client owns the HUD.

## Verified runtime evidence

| Test | Result and scope |
| --- | --- |
| Original reconnect reproduction | Actual DK max health fell from 28 to 20 on the continuously running baseline server. |
| Fixed reconnect lifecycle | Actual server/client 42.85 max health agrees over three real reconnects on the same server PID 19989. Restart, dimension travel, death/respawn, class changes, equipment and temporary buff expiry were also observed. |
| Healing | Direct, skill, potion, group and regeneration healing increase actual health with no new damage packets, hurt timers or hurt-camera frames in their healing windows. Actual damage still produces a damage packet and hurt-camera frames. |
| Attribute GUI | A real click spends one point, persists Strength 1 and increases actual max health from 42.85 to 43.2785 according to the existing +1% rule. Attack damage is not this attribute's configured stat. |
| Death Sentence | 11 live checks pass, including camera-directed target movement, unskippable source hover, real cow damage/knockup and restored gravity. Dedicated-server and real singleplayer runtime evidence exist. A separate real GUI equips slot 2; standard Controls F9 activates the owned ultimate, consumes mana and applies the configured approximately 48-second cooldown. Repeat input is rejected during that cooldown. |
| Pokémon integration | All 17 checks pass. Real wild Pikachu takes Tackle damage and receives persistent poison. A real party-owned Pokémon is protected. A formal battle is started through Cobblemon's forceBattle API and overworld damage/status/healing are rejected. |
| Class catalog/passives | All 10 live checks pass: seven configured DK skills plus purchased Cut, source TIMER metadata, active/passive slot restrictions, invalid bind with no mutation, manual passive cast rejection, and automatic seal refresh beyond its initial 40-tick lifetime. |
| Fresh default installation | A new server with no initial module configuration installs DK/AMK definitions before userdata attaches, loads nine definitions and zero player records. |
| Client handshake | A real client missing FantasyHubClient is rejected with the correct installation message; a matching presentation client connects. |
| Native Mythic mechanic | A configured timer damage mechanic changes a real configured Pikachu from 100 to 92 health; its actual maximum health remains 100. The Pokémon actor also runs native mechanics. |
| Shop | All 15 currently purchasable adapters have real ownership/equip/cast/cooldown evidence. The initial pass debits the configured 2500 CobbleDollars for each purchase. Duplicate requests do not debit again. Unsupported Flamethrower is not sold. |
| Multiplayer | Two real clients receive separate snapshots and agree with their own server maxima: 43.2785 for Strength 1 and 42.85 for the other account. English GUI scale 3 and Vietnamese GUI scale 2 run simultaneously. The Pokémon type localization key is normalized to Cobblemon's lowercase namespace; a real Vietnamese screenshot now displays Bình Thường rather than an unresolved key. |
| Singleplayer | Core and Client load in the integrated server; real HUD, shop and Death Sentence tests run without an external resource pack. |
| Profiling | A 73-second Minecraft JFR capture with 16 timer-driven vanilla/Pokémon actors and two clients measures average tick 11.86 ms, median 6.94 ms and p99 70.38 ms. This is a synthetic combat workload, not a production capacity guarantee. |
| Build/unit checks | Local Java 21 builds pass. Lib has 18 passing JUnit tests; Items 23; Mobs 5; the visual converter has 5 Python tests. Existing native Core/Integration smoke checks have separate logs. |

The final native harness passes 59 checks across Death Sentence (11), reference adapters (21), Pokémon integration (17) and catalog/passives (10). Its initial Pokémon count included a boolean metadata field alongside the actual battle assertion; the harness now excludes that metadata field while retaining the real battle check. Both initial reports are retained.

The first owned-ultimate observation sampled before the client request arrived and again after mana regeneration. A subsequent short-interval live poll records mana below maximum and cooldown decreasing from 47.799 seconds; the earlier impact/repeat snapshots already record 38.825/37.057 seconds. This was a measurement timing issue, not a missing skill-cost path. The original observations are retained.

The initial class-passive runtime scenario failed because public TIMER containers were never ticked. That failure is retained; the scheduling bridge and cast-specific metadata fix are tested against the same live scenario.

The first shop casting run exposed an incomplete test floor at x=40 and produced two failed target/cast checks while entities fell. That failed report is retained. With the solid test arena, all 15 effects pass. The opt-in QA helper now requires a floor and resets teleport setup velocity; gameplay targeting was not weakened to pass the test.

## Coverage and unfinished work

| Source category | Established audit count | Current coverage |
| --- | ---: | --- |
| Classes | 36 | 9 total loaded baseline definitions; 2 source-specific DK/AMK adapter sets. **0 classes claimed to pass full original-pack behavioral conformance.** |
| Class skill IDs | 237 | 14 explicit native DK/AMK adapters; no claim that their complete original meta-skill graphs are migrated. |
| Meta skills | 4542 unique / 4955 definitions | No full-pack native graph interpreter. |
| Mob definitions | 1079 unique / 1132 definitions | No claim that these are bosses or that the complete pack is migrated. 89 records explicitly configure a BossBar. |
| Full source boss encounters | Source-specific count requires distinguishing VFX/pets from encounters | **0 complete original encounters claimed.** |
| Cobblemon moves | 936 registered runtime moves | **15 player-purchase adapters tested**: 10 single-hit and 5 half-heal moves. Other moves remain available for existing/fusion registries but are not sold as working player skills. |
| Mythic constructs | Full syntax catalog retained in the established manifest | 6 native mechanics, 5 selector families plus aliases, 5 trigger families; no complete conditions/meta-skills/spawners/phases interpreter. |
| Skript | 40 scripts / 28 enabled | **0 enabled scripts pass a native Skript conformance suite. No Fabric Skript runtime is claimed.** The existing Lib YAML ScriptEngine is not Skript. |
| Authored presentation blueprints | 685 | 15 DK-related authored blueprints converted into 66 model/animation IDs, with source texture/sound/icon assets. This is not all-class/all-boss visual coverage. |

Remaining implementation includes the other class identities and progression/unlock rules; complete meta-skill graphs, conditions, phases, drops and spawners; Skript language/addon semantics; move-specific adapters beyond the validated 15; all class/boss models and previews; class selection, talents, bestiary, quests, equipment and other requested screens; richer animation events and casting/buff HUD; exhaustive economy crash recovery across providers. These are unfinished implementations, not missing-source excuses.

The existing source audit records a missing `NinjaDamageEvent` definition referenced by `config2/MythicMobs/Packs/WorldBossPack2/Mobs/WardenMobs.yml:1`; the reference does not specify a filename. The source `Extras/vfx_earthquake_rupture_1.bbmodel` does contain the required skill2 animation and is selected explicitly; that animation is not a missing-source blocker.

Purchase duplicate protection and durable skill saving are implemented, but a process-crash transaction journal coordinating every external economy provider is not. BEconomy/Impactor were not installed for this runtime suite. No 1:1 economy, Skript or MythicMobs compatibility certification is asserted.

## Reproducible local build and QA

Build the five framework modules locally, then see `FantasyHub/README.md` for the client asset conversion and bundled Core build. Do not install the raw framework JARs alongside a conflicting bundled version. External Fabric/Cobblemon/Polymer/Kotlin/Architectury/Accessories/owo dependencies remain required.

The environment variable `SVFRAME_RUNTIME_QA=1` enables administrator-only `/fantasyhubqa`, `/svframelib deathsentenceqa`, `/svframelib referenceqa`, `/cobblemonentityqa` and `/cobblemonshopqa` plus observation logs. Leave this disabled on public servers. The shop scenario uses real configured prices and requires a funded QA account and a solid floor at (40,64,0).

Source inputs stay outside Git; asset conversion preserves their original blueprint files locally. The client candidate contains the supplied DK assets for local inspection. Vendor plugin JARs, worlds, player records, credentials and proprietary source archives are not pushed as source code. This milestone does not establish redistribution rights for the supplied art/sounds; exact selected source paths are recorded in the packaged source-choices resource.
