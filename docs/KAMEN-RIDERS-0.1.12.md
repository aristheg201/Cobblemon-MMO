# Ryuki / Knight / Decade — local build 0.1.12

Repository: `aristheg201/Cobblemon-MMO`. Branch: `feature/kamen-rider-ryuki-knight-decade`. The delivery's `build-manifest.json` records the exact source commit and SHA-256 of all four updated JARs. Production branches were preserved. All builds and tests ran locally; GitHub Actions was not used.

## Install the Bridge distribution

1. Stop the dedicated server. Retain existing world, player data and customized configuration.
2. Replace **FantasyHubBridge**, **SVFrameLib** and **SVFrameMMO** with the three server JARs in `server-update/`. Replace Lib/MMO even if the filename/version matches the installed one: their shared damage, preference and recast APIs changed.
3. Keep the compatible SVFrameItems 0.1.2, SVFrameMobs 0.1.2, SVFrameMMO Cobblemon Integration 0.1.18 and SVFrameSkript 0.1.0. The exact unchanged runtime dependencies are included separately in `server-dependencies/`.
4. Install FantasyHubClient 0.1.12 on **every client**, replacing the previous client. Client and Bridge each include their matching Protocol. Minecraft 1.21.1, Java21 and Fabric Loader >=0.18.4 are required. Preserve the existing Fabric API, Cobblemon and accessory/Filament/TAN dependencies.
5. Do not install a bundled FantasyHubCore alongside this Bridge. No unrelated external Rider resource pack is needed; original model/texture/particle/sound assets are bundled in the client.

Select the Rider through the existing class interface (F8 character menu). Learn/bind skills using the existing progression interface and equip that Rider's belt. F enables the existing skill bar and Henshin; F again returns to human. Use the seven displayed skill bindings (the current default skill-bar mapping uses number keys2–8). Survive / Complete toggle indefinitely; press their slot again to leave the stance. Decade's Heisei Spirit recasts cycle Kuuga/Faiz/Ryuki within the displayed selection window. Slash & Blast permits one free gun recast inside its finite window while sharing the initial cooldown.

Server balance is in `config/FantasyHub/riders.yml`. Missing fields in old partial sections inherit defaults; existing custom values are preserved. A one-time class-roster migration adds the five native Riders to old `additional-native-classes` lists, preserves all other values and creates `class-switch.before-card-riders.yml`. Once marked version1, later administrator removals remain respected. Upkeep defaults to zero. Skill damage/cost/CD, PvP scaling, friendly fire, target/projectile/summon limits, ranges, shields, CC, healing reduction and stance multipliers are configurable. Client visual quality has low/medium/high budgets and distance limits.

## Assets and reference work

Both user archives were safely extracted and every one of their18 images opened. The reference-to-asset manifest includes hashes, Rider/form/equipment/summon categories and the design features used. Supplementary official equipment references are also recorded. Original photos and previously imported user packs remain private.

[Editable inventory](qa/rider-models/model-inventory.json): **41 real Blockbench5.2.2 projects**, original512px UV atlas with41 materials, native game exports and animation hierarchies. The three base full-body models have144 Ryuki,142 Knight and179 Decade authored elements, including shaped/layered face cages, distinct chest/shoulder silhouettes, rear plates, recognisable belts, arm/leg guards and fitted equipment. There are three separate helmet projects. Body projects have12 animation definitions each.

Wearable Rider models: Ryuki, Knight, Decade, plus retained Zolda and Agito Ground/Storm/Flame/Trinity/Shining. Additional assets: Dragreder, Dragranzer, Darkwing, Magnugiga; Dragvisor/Claw/Visor-Z; two dragon shields; Knight wing cloak; real3D card planes; K-Touch; Kuuga Ultimate, Blade King and Kiva Emperor projections. These Ultimate projections are deliberately excluded from the wearable-player-model list. Individual equipment includes all three accurate belt/deck devices, Drag Saber, Dark Visor/Dark Blade, Ride Booker sword/gun and retained Agito/Zolda equipment. There are51 SVFrameItems equipment definitions.

Ryuki/Knight Survive preserve the base wearable armor and change aura, equipment and combat presentation. An alternate Survive wearable armor is optional in the specification and is not supplied here. Decade **always** preserves his own model, including Complete and borrowed Rider attacks.

Actual native front/back/both-side/three-quarter/helmet/belt/grip images are in [final-models](qa/rider-models/runtime/final-models). Earlier Blockbench previews are labelled separately. The user's rejected first pass is retained under `rejected-card-riders-v1`. These newer models have been rendered and inspected; aesthetic approval remains the user's decision.

## Executable skill matrix

Each row has server gameplay, native sound/VFX/animation events, resource/CD handling and a real-server acceptance report. `pipeline-skills` records the current damage-pipeline integration rerun; branch/recast/passive tests are separate. Temporary actors are bounded and finite.

| Class | Slot | Ability | Implemented gameplay |
|---|---:|---|---|
| Ryuki |1|Strike Vent|Explosive fire punch; Survive fires three independently colliding projectiles.|
| Ryuki |2|Sword Vent|Two timed cuts and burning bleed; Survive swept dash with scoped50% armor penetration.|
| Ryuki |3|Guard Vent|Two orbiting shield meshes, absorption and25% eligible melee reflection.|
| Ryuki |4|Advent|Moving/animated Dragreder manifestation, spiral hits, launch and finite fire zones.|
| Ryuki |5|Survive|Indefinite stance;20% damage/speed, alternate attacks/passive, configurable upkeep.|
| Ryuki |6|Shoot Vent|Targeted missile barrage, collision/explosion,50% slow and healing reduction.|
| Ryuki |7|Final Vent|Airborne Dragon Rider Kick; Survive Dragranzer charge with bounded path collision.|
| Knight |1|Sword Vent|Piercing thrust/35% slow; Survive two independently timed dash passes.|
| Knight |2|Nasty Vent|Directional Darkwing sonic cone,30tick stun/60tick blindness.|
| Knight |3|Trick Vent|Three finite attacking visual actors and50tick player invisibility.|
| Knight |4|Guard Vent|Animated enclosing wing cloak,80% reduction for40ticks and nearby repel.|
| Knight |5|Survive|Indefinite stance, capped attack-speed multiplier, lifesteal and20% cooldown reduction.|
| Knight |6|Blast Vent|Mobile pulling tornado with repeated collision-validated sword hits.|
| Knight |7|Final Vent|Aerial cloak drill; Survive Darkwing line assault with swept collision/afterimages.|
| Decade |1|Slash & Blast|Three sword cuts; single-use free four-bullet retreating gun recast.|
| Decade |2|Illusion|Two side holograms, corresponding validated attack damage and finite cleanup.|
| Decade |3|Clock Up / Invisible|Finite hidden matrix, collision/loaded-chunk-safe placement behind enemy, critical slash.|
| Decade |4|Heisei Spirit|Finite recast selection: Kuuga shield, Faiz speed or Ryuki explosive attacks.|
| Decade |5|Complete|Indefinite30% damage/CC immunity and orbiting holographic cards; same armor model.|
| Decade |6|Ultimate Summon|Rotating distinct Kuuga Ultimate/Blade King/Kiva Emperor2second projections with attacks.|
| Decade |7|Dimension Kick|Sequential5–7 actual3D cards, charge, validated swept kick and True Damage.|

Passives: Ryuki10-stack resonance restores5% max Mana and explodes; Survive changes to bounded fire projectiles/burning. Knight rear or blinded/controlled target eligibility forces crit and restores Stamina with an internal cooldown. Decade Attack Ride arms the next normal hit once, producing exactly three real True Damage projectiles.

## Local build and automated checks

[Local build procedure](qa/rider-models/LOCAL_BUILD.md) builds the canonical Lib/MMO and raw Bridge/client. Java21.0.2/Gradle8.8. **92 JUnit tests pass**: Core44, Client18, Lib30. MMO nativeCoreSmoke also passes72 skill-bar mappings and preference snapshot-codec checks. XML and complete local build logs accompany the JAR manifest.

The native21-skill runner is `FantasyHub/tools/run_card_rider_native_qa.py`. It requires an isolated test server with `SVFRAME_RUNTIME_QA=1`, real connected QAPlayer/QAAttacker clients, X11/xdotool/ffmpeg, RCON password provided through `FANTASYHUB_QA_RCON_PASSWORD`, and `--server`/`--output`. It exercises real skill handlers and records native reports/video; it does not simulate damage. QA prepare commands intentionally grant test progression/equipment and clear fixture cooldowns, so these are not purchase-economy acceptance claims.

## Native QA evidence

- [Native class-selection menu](qa/rider-models/runtime/class-selection/results.json): all three selected through the actual client GUI and existing paid class transaction, with the configured currency charge.
- [Current21-skill regression](qa/rider-models/runtime/pipeline-skills/results.json): **21/21 PASS**, accepted casts, actual applicable hit events, resource costs, cooldowns and preserved real inventory. Includes21 actual MC screenshots and21 short videos.
- [Combat details](qa/rider-models/runtime/event-combat/results.json): **8/8 PASS**, Knight80% guard, Ryuki25% melee reflection/no arrow reflection,20→10→20 armor during/after piercing, rear forced crit/stamina once, front exclusion, enemy PvP and protected same-team PvP.
- [Form damage measurements](qa/rider-models/runtime/event-combat/form-damage-multipliers.json): Ryuki18.4→22.08 (1.2x); Decade Dimension Kick48→62.4 per target (1.3x).
- [Branches/recasts/resources](qa/rider-models/runtime/local-variants/results.json): five Survive attack branches, free single-use gun recast, reliable three-spirit selection and no-resource rejection.
- [Extra two-client evidence](qa/rider-models/runtime/final-extra/results.json): three distinct Ultimate projections, configured card count, actual keyboard casts, Complete CC policy, solid-wall rejection, exactly three passive projectile spawns observed by both clients, simultaneous two-player casting.
- [Actual process restart](qa/rider-models/runtime/final-restart/restart.json): class, level, seven bindings, belt, inventory, spirit/projection preferences and remaining saved cooldown restored; temporary form/actors reset. A600second test-only cooldown proved durability; production defaults were restored.
- [Additional lifecycle checks](qa/rider-models/runtime/final-lifecycle/results.json): repeated toggles, dimension transfer, actual death/respawn, disconnect with a live summon and real client resource reload. Persistent loadout and temporary hidden casting UI are checked separately.
- [Indefinite stance duration](qa/rider-models/runtime/local-models/results.json): all three remain elevated beyond1200 actual server ticks, keep their model and return to human through real F.

Failures discovered during QA are preserved with their superseding fixes, including the original Dimension Kick inside-box collision, oversized catalog transport, duplicate damage-return mixins and invalid fixture assumptions. They are not hidden by deleting failed evidence.

## State and performance

Gameplay state remains main-thread-owned, UUID-based and server-authoritative. Persistent progression/loadout uses existing SVFrameMMO. Bounded Rider preferences store selected spirit/projection and absolute cooldown deadlines in that existing record. Dirty-record batching snapshots immutable data before the existing backend writes asynchronously; disconnect/shutdown flush. No alternate Rider player-data store was introduced. Form effects are intentionally transient and reset to human on death/logout/dimension/restart; saved preferences/cooldowns persist.

Spatial queries, target/projectile/summon caps, finite generations/actor cleanup, distance-based presentation LOD and compact spawn/effect packets bound combat work. There is no world-wide per-tick entity scan or combat-triggered full synchronous player save. A small shared final-damage event replaces the conflicting RETURN injectors; canonical attack events now run once while preserving vanilla mitigation, metadata and cancellation.

## Limits of verification

Two real software-rendered clients and one dedicated server were exercised. Large-player-count load profiling and every optional accessory backend permutation were not performed. Current visual assets are an inspected candidate, not a claim of user aesthetic approval. Sound assets are original synthesized cues plus verified native Minecraft events; supplied film voice recordings were unavailable. Headless capture uses a null audio output, so videos do not contain an audible film-sound comparison.

The existing Filament/Polymer external-pack builder logs a missing Minecraft client-resource/trim-atlas failure in this network-restricted QA environment; the new bundled Rider assets render without that external pack. Upstream public-key fetch errors also remain environmental. These logs are retained, and no such failure is silently described as a clean external-pack build.

This report covers the three-class expansion. It does not assert that the unrelated full legacy FantasyHub migration has passed its global release gate.
