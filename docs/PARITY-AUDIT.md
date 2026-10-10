# Fabric port audit — 2026-10-10

Target: Minecraft **1.21.1 only**, Java 21, Fabric Loader **>=0.18.4**. Builds and QA run locally; no GitHub Actions builds were requested or dispatched.

This is a verified implementation milestone, **not a claim of complete MMOCore/MMOItems/MythicLib/MythicMobs parity or of having fixed every bug**.

| Component | Current evidence | Remaining scope |
| --- | --- | --- |
| SVFrameLib 1.7.2-fabric | 15 unit tests; native damage attribution, velocity packets, dimension-aware particles/projectiles; Hero’s Entrance; native animated item-display bones, fourteen native reference class adapters | Exhaustive behavior comparison for all 90 upstream skill handlers and all scripting mechanics; Bukkit API compatibility is not provided |
| SVFrameMMO 1.13.2-fabric | Native core smoke; existing classes/professions/attributes/skill trees/persistence; HERO_ENTRANCE grant and binding | Party, guild, quest, waypoint systems are absent; all upstream commands, GUIs and experience sources need individual parity checks |
| SVFrameItems 0.1.1-fabric | 23 unit tests, native core smoke, real server item smoke; equipment/upgrade/socket/loot/crafting; shared skill cost/cooldown owner | Full upstream item stat/type corpus, crafting station/editor workflows, and external addon APIs remain unverified/incomplete |
| SVFrameMobs 0.1.0-fabric | New fifth mod; 4 parser tests; real spawn, configured death drop, atomic reload, save/restart persistence | Foundation only: not a complete MythicMobs engine; meta-skills, conditions, advanced targeters, spawners, boss bars, threat tables, pathfinder customization, full model integration remain |
| Cobblemon integration 0.1.17 | Native integration smoke; production dedicated server registers 936 move skills; production client starts | Every move, battle lifecycle, fusion/disguise and optional Mega Showdown combination still needs scenario coverage |

## Fixes in this milestone

- Reconciled shared source copies across branches. The Items branch carried an older MMO implementation; Integration lacked current player identity/XP ownership fixes. Retained the optional LuckPerms bridge and the current health sentinel behavior.
- Core entrypoints now load in both dedicated servers and integrated singleplayer (`environment: "*"`); Minecraft dependency is exactly 1.21.1.
- Disconnect cleanup checks the current player instance, preventing stale sessions from removing a replacement session.
- Skill parameters supplied as complete values no longer receive upstream player/item defaults a second time.
- Item abilities check mana/stamina and a cooldown shared with class skills, charge once after a successful cast, and reject invalid costs.
- Native skill damage preserves its caster and damage types through the registered damage manager. Native player velocity is sent to the client.
- Position-based particles/projectiles use the caster’s dimension. Projectiles use swept collision and block raycasts rather than checking only their final point.
- Particle YAML accepts raw maps and `particle` keys; native effects recognize common Bukkit particle names and parameterized dust/block/item particles.
- Gradle resource processing tracks the release version so incremental builds cannot produce an old `fabric.mod.json` inside a newly named JAR. Wrappers are checked in and their distributions have SHA-256 checksums.
- Removed tracked build caches and runtime worlds from the index; local QA files remain on disk and are ignored.

## Reference and visual scope

Public upstream references inspected:

- MMOCore: https://gitlab.com/phoenix-dvpmt/mmocore — commit `3fb474e`.
- MythicLib: https://gitlab.com/phoenix-dvpmt/mythiclib — commit `9a37da4`.
- MMOItems: https://gitlab.com/phoenix-dvpmt/mmoitems — commit `3020601`.
- User resource listing: https://minevn.net/resources/mmocore-premium.3568/.
- Death Knight reference: https://youtu.be/4cagZwdjvdU; public feature description: https://samusdev.com/product/rpg-class-legends-death-knight/.

The user supplied the reference folder at https://drive.google.com/drive/folders/1mwt23RfskwG6KG6TTYBCPfqcVfqKfqWT. All four archives were downloaded and inspected, including the larger config archive through its public Drive download endpoint after the connector's 256 MiB cap rejected it. No Bukkit plugin JAR is installed into Fabric.

The plugin archive contains MMOCore 1.13.1-SNAPSHOT, MMOItems 6.10.1-SNAPSHOT, MythicLib 1.7.1-SNAPSHOT, MythicMobs 5.6.2, ModelEngine 4.0.5, and Skript 2.13.2. The supplied Death Knight file contains 69 meta-skills, 28 different mechanics, 10 targeters and 6 condition types. `docs/qa/reference-audit.json` records the inventory and archive hashes without publishing private player data or vendor assets.

An explicit native adapter now registers all fourteen Death Knight/Anti Mage Knight class skills. It is **not** a generic interpreter for those 69 MythicMobs meta-skills or for Skript. The AMK v17 executable behavior takes precedence over contradictory older lore: `overload` is the taunt/guard and `hitme` is another jump-slam. Gust, clap, surge and meteor use the Skript's 4/7/8/10 damage values; the larger unused wrapper modifiers are retained in imported metadata but do not multiply the script damage. Native class and item casts own costs/cooldowns. Passive timers are normalized to one second so their short native activation windows refresh safely.

Native behavior coverage: curse stacks and Weakness, a four-step strike combo with grapple, charge, chain pull/Slowness, barrier absorption/healing, whirlwind hits/final heal, camera-aimed Death Sentence with the supplied full-seal damage bonus, AMK shielding/empowered attack, travelling gust/clap, dash/throw-back, taunt/guard stored-damage release, and jump-slams. **Timing, chain stun/caster locking, projectile/model frame swaps, and exact multi-hit geometry still differ from the original scripts and need further parity work.** PvP/creative/spectator/team checks are present, but party/guild-friendly targeting cannot match the pack until those MMO systems exist. The 21 runtime adapter checks cover core combat behaviors, not every phase of all fourteen skills.

Both visual paths were exercised: native particles and original user-supplied animated models through vanilla item displays. `import_blockbench_visuals.py` imports cube hierarchies, embedded PNGs, numeric position/rotation/scale keyframes and linear/Catmull-Rom animation into bounded native poses. Three blueprints (Soul Blade, Death Strike, Death Wings) produced 44 cube displays and 13 animation IDs; the Soul Blade was inspected in-game. Meshes, expressions, Bezier curves, full ModelEngine state machines, bone swaps/tints, and the rest of the VFX corpus are not supported/verified. The renderer caps concurrent displays at 512 and model lifetimes at 1200 ticks.

The original demonstration `SVFrameVisuals-1.21.1.zip` remains freely included. Converted vendor visuals and sounds remain local for the user's inspection, outside the Git repositories. Import tools are committed so the user's owned assets can be converted again. Texture paths use Minecraft's item atlas directory; runtime captures caught and corrected an initial black/missing-texture render.

The Cobblemon development `runClient` encountered a Kotlin reflection lookup for intermediary `net.minecraft.class_2960` under named mappings. Production-mapped client and server startup succeeded. This is documented as a development launch limitation, not silently declared fixed.
