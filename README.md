# Alpha-Encounter

Standalone Fabric 1.21.1 addon for Cobblemon 1.8.0 that turns configured Pokemon into roaming field encounters and shared-HP battle bosses.

## Dependency boundary

Hard dependencies: Fabric API and Cobblemon only. SVFrameLib, SVFrameMMO, and SVFrameMMO-Cobblemon-Integration are intentionally not referenced. Minecraft damage is allowed to flow through the normal entity damage pipeline so other installed combat mods can participate naturally.

## Encounter lifecycle

`IDLE -> HUNT -> BATTLE_PENDING -> BATTLE -> PHASE_RECOVERY/HUNT/DEFEATED`

- Aggressive encounters acquire and chase nearby players in the world.
- Reaching melee range does **not** automatically start a Cobblemon battle.
- While hunting, the Pokemon performs real Minecraft `mobAttack` damage against the player, with configurable damage/range/cooldown/knockback and a semantic attack animation.
- A player damaging a managed Pokemon outside battle deals resolved field damage to shared HP, then provokes a Cobblemon PVE battle on the next server tick.
- Field damage cannot finish the encounter; it clamps shared HP to at least 1 so the battle layer owns the actual defeat.
- Battle damage subtracts the real local HP delta from the shared encounter HP pool.
- Cobblemon `BATTLE_FAINTED`, `BATTLE_FLED`, and `BATTLE_VICTORY` events drive lifecycle decisions instead of guessing knockout state from a disappearing world entity.
- Encounter identity is keyed by the canonical Pokemon UUID. Entity UUID is only the current world shell.
- If a local battle HP bar reaches zero while shared HP remains, the canonical Pokemon enters `PHASE_RECOVERY`; if Cobblemon removed the wild entity, Alpha-Encounter respawns/rebinds a new world entity around the same Pokemon object and returns to `HUNT`.
- Only shared HP reaching zero causes `DEFEATED`.
- Fleeing leaves the encounter alive and hostile; it returns to HUNT and prefers the last player after the re-engage cooldown.
- Optional catch phase exposes the canonical defeated Pokemon at low HP instead of rolling a replacement Pokemon.

## Config layout

Alpha-Encounter now uses a modular folder tree inspired by the organizational approach of raid plugins such as NovaRaids, while using independent implementation/code:

```text
config/alpha-encounter/
  config.json
  behaviours/
    passive.json
    aggressive.json
    apex_hunter.json
  tiers/
    regional.json
    signature.json
    apex.json
  categories/
    <category-id>/
      settings.json
      encounters/
        <encounter>.json
  persistent/
    state.json
  legacy/
    config-v1.json
    state-v1.json
```

`config.json` contains global runtime/spawn cadences only. Behaviours own field AI/combat. Tiers own shared-HP multiplier and bossbar presentation. Category settings can provide common dimension/biome conditions. Each encounter lives in its own JSON file.

### Automatic migration

A legacy monolithic `config/alpha-encounter/config.json` containing `general`, `tiers`, and `encounters` is detected automatically. On first load Alpha-Encounter:

1. backs it up to `legacy/config-v1.json`;
2. writes global settings back to the new `config.json`;
3. splits legacy tiers into `tiers/*.json` and matching behaviour profiles;
4. splits every legacy encounter into `categories/<derived-biome>/encounters/*.json`;
5. moves old `state.json` to `persistent/state.json` and preserves a legacy copy.

No manual rewrite of the existing large config is required.

## Mount Yeager / Godzilla Tyranitar

Default modular config creates:

```text
categories/bestiary_mount_yeager/settings.json
categories/bestiary_mount_yeager/encounters/tyranitar_godzilla.json
```

The encounter uses the exact Cobblemon property string:

```text
tyranitar level=100 alpha=true cosmetic_item-godzilla
```

Dimension: `minecraft:overworld`

Biome: `bestiary:mount_yeager`

The resource-pack resolver matches the aspect `cosmetic_item-godzilla`; `tyranitar_godzilla` is only the model/poser/animation family name. The runtime logs both requested properties and resolved Pokemon/entity aspects when an encounter spawns, and `/alphaencounter inspect` exposes them for testing.

## Shared HP / multi-phase KO

Tier `healthMultiplier` creates a true shared HP pool. Example: native max HP 400 with Apex multiplier 12 gives 4800 shared HP.

A Cobblemon battle still uses a normal local Pokemon HP bar (up to native max HP). Damage subtracts point-for-point from the shared pool. If the local Pokemon faints at 4400 shared HP, it is not defeated: the battle ends, the same canonical Pokemon is restored into another world phase, and the next battle starts with another local HP bar. Final defeat occurs only when shared HP reaches zero.

This is event-driven using Cobblemon battle events so it remains valid even if the fainted wild entity is removed after battle.

## Field combat

Behaviour files expose:

- `aggressive`
- `aggroRadius`
- `leashRadius`
- `chaseSpeed`
- `fieldAttackRange`
- `fieldAttackDamage`
- `fieldAttackCooldownTicks`
- `fieldAttackKnockback`
- `fieldAttackAnimation`
- `reengageCooldownTicks`

Boss attacks call Minecraft's normal damage path. There is no custom player-defense formula and no SVFrame integration layer.

## Performance behavior

- Non-managed Pokemon are immediate no-ops in the damage hook.
- Managed encounters are indexed by both current entity UUID and canonical Pokemon UUID.
- Natural spawn candidates are pre-indexed by dimension + biome on config load.
- Spawn checks remain staggered per player.
- Target/path refresh runs at a configured cadence; attack range/cooldown checks remain responsive.
- Battle polling is restricted to active battle encounters, while faint/flee/victory lifecycle is event-driven.
- Bossbar and persistence updates remain independently throttled/dirty-state based.

## Admin commands

All commands require vanilla permission level 2.

```text
/alphaencounter help
/alphaencounter reload
/alphaencounter save
/alphaencounter debug
/alphaencounter debug reset
/alphaencounter list
/alphaencounter spawn <id> [player]
/alphaencounter inspect <nearest|entity-uuid|pokemon-uuid|id>
/alphaencounter despawn <target>
/alphaencounter defeat <target>
/alphaencounter sethp <target> <1-100>
/alphaencounter battle <target> <player>
/alphaencounter attack <target> <player>
/alphaencounter anim <target> <animation>
/alphaencounter reset <id|all>
```

`attack` is a dedicated smoke-test command for the vanilla field hit. `inspect` prints requested PokemonProperties, shared HP/state, canonical Pokemon UUID, current entity UUID, Pokemon aspects, and entity aspects.

## Recommended smoke test

```text
/alphaencounter reload
/alphaencounter spawn alpha_bestiary_mount_yeager_tyranitar_godzilla <player>
/alphaencounter inspect nearest
/alphaencounter attack nearest <player>
```

Verify that the player takes normal Minecraft damage and no battle starts from the boss attack. Then hit the boss as the player and verify battle starts. During battle, defeat one local HP bar while shared HP remains and verify `PHASE_RECOVERY -> HUNT`, then inspect again to confirm the canonical Pokemon UUID is unchanged even if entity UUID changed.

## Build validation

GitHub Actions builds this branch against Minecraft 1.21.1, Fabric API, Java 21, and Cobblemon 1.8.0 and publishes the remapped JAR as the `Alpha-Encounter` artifact.
