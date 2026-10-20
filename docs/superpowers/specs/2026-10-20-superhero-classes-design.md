# Six superhero classes: SVFrame integration and balance

The user's 2026-10-20 technical specification is the gameplay brief: Spider-Man, Sorcerer Supreme, Iron Man, Thor, The Flash and Batman, seven active skills each and the listed passives. Kamen Rider work is paused on its own branch. Keep Minecraft 1.21.1 / Fabric / Java 21 and build locally. This is an internal integration; do not publish it or bundle the FiskHeroes mod JAR inside another mod.

## Integration

Use existing SVFrameMMO classes, seven-slot skill runtime, recast windows, cancellable cast/resource events, player records and cooldowns. Use SVFrameLib damage metadata, damage registration, targeting/friendly-fire and stat modifier owners. Energy is existing Mana with a class-specific label for Sorcerer, Arc, Lightning and Speed; Spider-Man/Batman use existing Stamina. Do not create a second saved player resource system. Transient combo, heat, clones and casts reset on death/logout/class change; saved class/loadout stays with SVFrameMMO.

There is no Henshin skill or form prerequisite. Class selection exposes powers. Equipped set pieces give ordinary SVFrameItems stats; equipping a full suit gives its appearance through equipment rendering. Removing armor removes only its equipment stats/appearance, and never forces a Rider-style transformation state machine.

## Compatibility evidence

Modrinth project `eFDDpt8w` (FiskHeroes) currently provides Forge 1.7.10 only, latest inspected 2.4.0. It cannot be loaded into this Fabric server/client. Inspect and convert the required JSON/texture/VFX resources to native SVFrame presentation. Copy no Forge classes, nested JARs or Fisk gameplay runtime. Preserve source/converted asset inventory and hashes. This is an adaptation, not binary compatibility.

The requested `fiskheroes:spiderman_brand_new_day` and Thor are absent from that base JAR; exact requested appearances remain dependent on the user's heropack. Known available equivalents are `spider_man`, `doctor_strange`, `iron_man`, `the_flash`, `batman_dceu`. The user subsequently authorized another Spider-Man and Thor variant. Use available classic Spider-Man and identify a real Thor heropack asset; do not label another character as Thor. Gameplay and balance can progress while the Thor resource is identified.

## Balance policy

Retain the user's PvE scaling, cooldowns and costs as explicit data, with server-overridable defaults. Damage scaling means a multiple of effective Physical/Magic Attack, not a multiple applied again to the engine's skill damage bonuses. Shared attack/critical/defense calculations still run once in SVFrameLib.

PvP has an explicit profile:

- Regular damage multiplier 0.65, with a per-cast/per-target ceiling of 25% target maximum HP after the canonical damage pipeline. All projectiles, delayed hits, recasts and damage-over-time belonging to that activation share the ledger.
- Ultimate total pre-defense coefficient is normalized to at most 3.0 effective Attack per target, with a 40% target maximum HP ceiling. Spider-Man's 7.8 coefficient cannot receive the same multiplier as a 4.2 Batman ultimate and remain balanced.
- True damage bypasses armor, not targeting, PvP scaling, cast budgets or shields unless the ability explicitly destroys shields.
- Hard control has a shared diminishing-return category (stun/root/freeze/air lock): duration cap 1.5s on players, then 50%, 25%, then immunity during a ten-second chain window. Silence cap 2s and movement slow cap 65%. PvE durations follow the brief except boss adapters/resistance.
- Forced movement stays within loaded chunks and collidable world geometry; maximum ordinary displacement 5 blocks. No "edge of map" displacement, world-wide freeze or spectator mode used as phasing.
- Phasing uses a bounded validated path and returns to the last safe position; no unloaded chunk travel, protected-region bypass or ending inside blocks. Flight is server-validated and has per-class energy/upkeep rules.
- Maximum 12 targets per regular AoE and 20 per ultimate. Friendly-fire is off by default. Projectiles/summons have owner limits, TTL and cleanup.
- Cooldown reduction is capped at 50%; recast windows do not recharge resources or create a second damage budget. No passive triggers itself through reflected damage.

Resources: Spider-Man/Batman Stamina 100 with 8/sec out of combat, 4/sec in combat. Sorcerer Mana 500 with 25/sec out of combat, 12.5/sec in combat. Arc 100 regenerates 10/sec idle, 5/sec in combat, zero while channeling/flying; flight costs 1.5/sec. Lightning 100 gains 8 per eligible hit (0.5s internal cooldown), 4 per block/parry and 4/sec out of combat. Speed 100 gains 8/sec while moving and 4/sec while idle with toggle off; speed toggle upkeep 2/sec, with automatic clean deactivation at insufficient energy. All values configurable.

## Server mechanics

Use bounded spatial queries, collision-aware projectile traces and scheduled action phases owned by a session generation. Cancellation/owner invalidation prevents delayed damage. Existing skill hotbar opens casting mode only; it must not Henshin or replace equipment. Successful hits pass through the existing combat pipeline with physical/magic/lightning/true metadata. New status restrictions integrate at the shared skill/teleport/movement boundaries and respect existing entity/boss adapters.

Each ability has executable preparation/action/impact/recovery, target checks, cost/cooldown and its specified recast/phase behavior. Controls use compact client input intent with server validation (double-jump, hold-jump, sneak), never client-supplied position/damage. Saved data uses the existing dirty/debounced persistence owner.

## Presentation

Adapt actual Fisk suit textures and compatible attachments to standard animated Minecraft body parts. Match hands, forearms, capes, eyes and chest anchors. Import only verified data resources; renderer scripts are inspected as references, not blindly executed. Per-class skill icons/localization, energy labels and status indicators use existing HUD. VFX use converted Fisk geometry/textures where available with native effects; no competing mod loader and no automatic Forge installation.

## Verification

Test all 42 definitions, resource spending/upkeep, class-independent suit stat removal, DR, recast shared budgets, true-damage handling, repeated projectiles, target eligibility, safe movement, cleanup and persistence lifecycle. Run local builds and actual dedicated server/client tests. Record exactly which visuals are verified and which remain blocked by missing heropack resources. Never report compilation or a model preview as completed runtime acceptance.
