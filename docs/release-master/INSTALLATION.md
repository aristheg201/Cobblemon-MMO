# SVFrame master 0.2.5 / WorldBoss 0.2.0

Minecraft Fabric 1.21.1, Java 21, Cobblemon 1.8.2. Back up the entire world, `config`, BEconomy data and native SVFrame player profiles before installing. Test against a copied server first.

## Install

1. Stop the server. Replace the existing Lib, MMO, Items, Mobs, Skript, MMO-Cobblemon Integration, FantasyHub Core/Bridge and WorldBoss JARs with `server/mods`. Remove older duplicates, including embedded framework bundles; one mod ID may only load once. FantasyHubBridge is the delivery filename for the existing `fantasyhubcore` module.
2. Keep existing Fabric API, Fabric Language Kotlin, BEconomy and the Cobblemon ecosystem dependencies. BEconomy version `CpGrhJed` was tested using its unchanged official JAR. Cobblemon is pinned to Modrinth `GJ2a1b8O` (1.8.2 Fabric). Dependency links and hashes are in the release manifest.
3. Clients replace FantasyHubClient with the matching 0.2.5 JAR. Keep EMF/ETF and the existing animation dependencies. The asset handshake is `heroes-commerce-v9`. Do not install WorldBoss on a client. ShadowCrates is not required.
4. Merge the provided configuration examples. Preserve existing BEconomy balances and currency IDs. The primary currency must represent HunterCoin; the `secondary` currency must represent BeastCoin. The primary alias follows BEconomy's configured primary currency. Do not copy QA user data or reset currency accounts.
5. `FantasyHub/commerce.json` contains independent prices, all 48 class tiers, complete bundle item lists, limits and enabled offers. `/fantasyshop reload` is operator-only. Item-shop products reject BeastCoin outside 500–3000 and HunterCoin below 2 or fractional values. Class/bundle prices are separate. Disabled or invalid offers are unavailable.
6. Hero defaults and the new `SVFrameMMO/attributes/hero-resources.yml` install automatically. Only untouched generated defaults are upgraded. If `FantasyHub/heroes.json` was customized, merge Sling Ring range 15 and duration 20 manually. Each new mana/stamina attribute caps at 50 points and grants +5 native maximum resource per point. Existing skills and allocations remain in the native class engine. Generated hero armor now uses the existing `standard` SVFrameItems upgrade template. If `SVFrameItems/items/heroes.yml` was customized, merge `upgrade-template: standard` into the armor definitions. Hold eligible RPG gear in the offhand and use the purchased catalyst; ineligible gear never consumes the catalyst.

## Class and shop rules

The initial free class choice remains. Purchases permanently grant the entitlement without activating a class. Owned-class switching is free after cooldown. The only early-switch service costs 500 BeastCoin by default; HunterCoin is never accepted for bypass. Prices and balances are validated on the server. Bundles for an owned class charge the configured equipment-only price. Armor never selects or unlocks a class.

Full inventories deliver through `/fantasyshop claim`. Native player data stores pending delivery and receipt IDs; inventory receipt tags prevent repeated claims after interruption. Uncertain paid transactions are retained for `/fantasyprogress status` reconciliation instead of repeated deductions or automatic ambiguous refunds. Preserve all transaction journals and native profiles when migrating.

## Boss arenas and tickets

Use `/worldboss setarena <id> <radius>` while standing on a safe floor; `/worldboss start <boss> <arena>` runs normal setup, and `/worldboss test start <boss> <arena>` starts immediately for operators. `/worldboss info`, `top`, `join`, `claim`, `reload` and `abort` expose the existing lifecycle. Boss and arena arguments autocomplete. Reload rejects invalid definitions and requires active encounters to be aborted first.

Build arenas and solid cover before registration. The mod never resets or destroys terrain. Use tall pillars for the Thanos cover mechanic. Arena reservation zones must not overlap and require a 16-block buffer. Safe entry positions need solid ground and player-sized clearance. Register custom bosses through `svframeworldboss/bosses.json`; schedules, locations, phases, skills, mitigation, telegraphs and personal multipliers are separate configuration.

Mark a distinct arena with `/worldboss setpersonal <id>`. A standard/elite ticket starts its configured personal boss when used inside that arena; `/worldboss personal <boss> <arena>` selects an eligible boss while holding the matching ticket. Only its owner can participate. Personal stats are copied per encounter: HP .55, damage .70, mitigation .75, heavy cooldown 1.30, telegraph 1.35 by default. The ticket is consumed only after initialization succeeds. Disconnect grace, owner death, abort and restart recovery clean up actors/reservations and return the owner. Scheduled arenas cannot be used for personal reservations.

Victory drop chances remain 15% for an eligible Stone and an additional 20% Gauntlet roll for Thanos. Personal tickets never improve these probabilities. Rolls and delivery receipts persist. Failed or aborted encounters do not grant victory loot. Class Sigil compatibility data remains available to existing systems.

## Infinity and SNAP

Hold a Stone to use it; right-click activates its ability. With Gauntlet in hand and Stones in inventory, sprint-right-click cycles the selected Stone, right-click uses it, and sneak-right-click starts SNAP. SNAP requires all six Stones and has a 30-minute persisted cooldown. Its carousel ports the supplied ShadowCrates shift/deceleration behavior directly and uses a custom Fantasy client skin; it does not load ShadowCrates code at runtime.

All ten outcomes remain, including deaths, Pokédex loss, both party/PC knockouts and wild dust. Knockouts set HP to zero and preserve UUIDs, ownership and slots. Normal Cobblemon healing remains in effect. SNAP Erasure/Sacrifice override the Time Stone revival and Infinity immunity so their lethal outcomes remain meaningful when all six Stones are equipped. Default monetary rewards use BEconomy Secondary; `infinity.json` can explicitly provide an administrator money command.

`svframeworldboss/snap-backups` contains recoverable pre-mutation snapshots. `infinity-state.json` records token, outcome and cooldown before resolution. PENDING outcomes resume once; APPLYING/FAILED outcomes require investigation and are not automatically rerolled or replayed. Preserve these files. To restore a Pokédex snapshot, also remove that player's matching entries from `pokedex-snap-exclusions.json`; exclusions only prevent startup scans of the exact already-owned Pokémon from undoing the loss. New catches/scans can discover species normally. Restore backups on a stopped, copied server before applying to production.

## Wild Pokémon

Merge `SVFrameMMOCobblemon/config.json` gameplay settings. Friendly Pokémon never retaliate; Neutral Pokémon retaliate against the actual attacker until timeout; Hostile Pokémon pursue within the configured range, leash and attacker caps. Alpha defaults to Hostile; explicit overrides can match species, form, level, nature, dimension, region and Alpha status. Existing administrator overrides are preserved. Boss minions use a separate controlled lifecycle, capped at 12 per encounter, and respect Soul peace.

RPG health is a temporary entity modifier for player RPG gear and skill damage: `min(1200, (BaseHP*3 + 24 + level*3) * AlphaMultiplier)`, with Alpha 1.5 by default. Native Pokémon battle stats, IVs, EVs, party/PC HP and level are not rewritten. Owned Pokémon, scripted minions and bosses are excluded. The modifier is suspended for battles/capture and removed on expiry/unload.

## Rebuild

Extract the full source archive, including `src/imported/resources`. Run `python FantasyHub/tools/build_master_release.py --root <source-directory>` with Java 21. It uses Gradle 8.8 for Lib/MMO/Items/FantasyHub and Gradle 9.2.1 for Mobs/Integration. Dependency versions are pinned in Gradle; internet access to Mojang, Fabric, Maven Central and Modrinth is needed on a cold machine. No DEV ZIP or binary patch is a build input. Read `BASELINE.md`, the manifest and QA report for provenance and validation limits.
