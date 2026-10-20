# Hero gameplay and presentation 0.2.3

This local release responds to the reported weak/absent effects, jump cancellation, incorrect equipment presentation and item durability. Video evidence is catalogued in `superhero-video-reference-notes.md`.

## Final behavior

- Batman's Jump input no longer replaces upward velocity with downward velocity. Web Swing and Thunderous Slam now land at the validated target rather than passing over it to the end of the aim ray. A physical Batman cape in functional Back, with Batman selected, starts native Elytra flight only while descending and Jump is held. Release, landing, water, crowd control, equipment removal or class/session change ends the cape lease. Native Elytra handling for other players is retained.
- Batman now uses the verified JMCT Comics gray/black suit and cape atlases. The Batman Back cape always replaces skin cape art. Thor and Strange keep personal-skin-cape priority.
- Hero item model selection accepts `fantasyhub:item/heroes/*`; fixed suit icons retain their original colors rather than vanilla leather tint.
- Stormbreaker appears only as a real held item. Selecting Thor or casting with empty hands never inserts a permanent virtual axe. Existing physical Stormbreaker grip frames, including the downward cutting blade, remain intact.
- Hero RPG equipment is unbreakable on generation/rebuild. Join migration marks previously issued hero gear without rewriting instance IDs, rolls, stats, upgrades or existing damage. Stormbreaker defaults are unbreakable as well. This is scoped to hero equipment.
- EMF uses its vanilla-model condition for actual Better Combat attacks and class skill poses, rather than freezing a segmented model midway through an animation. Idle weapon stances retain Detailed Animations. Hero suit layers use the actual EMF player parts, including their custom child joints, rather than copying only the outer vanilla arm/leg angles. Detailed Animations still requires the separate EMF/ETF mods and the built-in resource pack to be enabled. Reload logs report these actual prerequisites.
- Skills retain server-confirmed projectile, impact, hit, trail and zone cues, rather than dropping impact geometry after one packet. Client effects are cosmetic; collision, damage, targeting, costs, cooldowns and target/cast budgets stay on the server.

## Visual language

Spider-Man uses braided tethers, a target cocoon, expanding web lattice and airborne combo poses. Strange uses counter-rotating glyph circles, two hand mandalas, paired portals, mirror shards, projection bands and green time seals. Thor uses branching vertical and radial lightning, electrical spin, prism Bifrost, an awakened electrical aura and impact shockwaves. Iron Man uses charge rings, emissive beam cores, projectile trails, orbiting drone silhouettes and orbital targeting/impact. Flash uses electrical running trails and layered wind rings. Batman uses batarang silhouettes, cables, smoke/radar outlines and a Batwing flyby marker.

Mjolnir Throw uses Soulslike Weaponry 1.4.8's actual Bedrock model, diffuse texture and glowmask, decoded with bone and cube pivots/rotations and per-face UVs. The reference archive exceeded the upload tool's 32 MiB limit, so the matching official Modrinth version was downloaded and checked against its published SHA512. Only the three model/texture data files are adapted; no Soulslike classes or mod archive is bundled. The physical axe remains Stormbreaker. Mjolnir outbound and return damage happen at the visible travel endpoints.

Custom native formats remain in `native_geometry/`, preserving StartupFix1 compatibility with GeckoLib and AzureLib. The new weapon model also uses this private renderer directory.

## Limits and settings

Geometry is bounded to 128 retained cues, 33 nearby sustained actors, 40/64-block effect view distance by quality, and bounded native particle bursts. Effects Density 0 disables client hero effects. Low quality reduces segments, branches and distance. Different skills retain different silhouettes/timing rather than requiring enormous particle counts.

The four supplied clips are visual references from Marvel Future Fight. This adaptation does not reproduce that game's cinematic cameras or every animation frame. The work preserves Minecraft controls and the existing SVFrame combat balance.

## Install

Use the complete release ZIP: client, server and config are separate folders. Replace older FantasyHubClient/FantasyHubBridge copies, including any FantasyHubCore distribution serving the same core mod ID; avoid duplicate versions. If running the core on the client for an integrated server, update that copy from server/mods too.

EMF and ETF in client/mods are ordinary separate official mods, not nested in FantasyHubClient. Keep already installed compatible Fabric API, Cobblemon, Accessories/Trinkets and Better Combat dependencies. No Xaero mod is bundled or declared as a dependency. Enable **SVFrame · Detailed Animations** under Resource Packs if an earlier failed reload disabled selected packs. Press **F** (default swap-hands skill-bar input) to open the bound skill bar.

Thor flight: Thor class + chest/legs/boots full unmasked set + Thor Cape in Back, double-tap Jump. Strange flight: Sorcerer Supreme class + Cloak of Levitation in Back, double-tap Jump. Batman is a descending glider, with the Back cape and Jump held.

The owned config installer upgrades only untouched defaults and preserves edited files. Review/merge the supplied config if you customized hero definitions or rules; this release adds HERO_BATMAN_CAPE and changes Stormbreaker's default unbreakable rule. Do not replace player data or the rest of your server config.
