# Alpha-Encounter

Standalone Fabric 1.21.1 addon for Cobblemon 1.8.0 that turns configured Pokemon into roaming field encounters and battle bosses.

## Dependency boundary

Hard dependencies: Fabric API and Cobblemon only. SVFrameLib, SVFrameMMO, and SVFrameMMO-Cobblemon-Integration are intentionally not referenced. If another mod changes Minecraft damage before it reaches a Pokemon entity, Alpha-Encounter consumes the resolved damage from the normal entity damage pipeline.

## Encounter lifecycle

`IDLE -> HUNT -> BATTLE_PENDING -> BATTLE -> HUNT/DEFEATED`

- Aggressive tiers proactively acquire nearby players and chase them.
- Field hits use resolved Minecraft damage but cannot reduce shared encounter HP below 1. This prevents one-shot bypasses before Cobblemon battle starts.
- Player field hits mark participation and queue a Cobblemon PVE battle on the next server tick.
- Shared encounter HP is projected into Cobblemon battle HP when battle starts and synchronized back while the battle is active.
- If the player leaves/flees a battle and the encounter still has HP, the encounter returns to `HUNT`, keeps the last player as its preferred target, and may re-engage after the configured cooldown.
- If battle HP reaches zero, the encounter is defeated.
- Optional catch phase can leave/spawn the configured Pokemon at low HP for a short capture window after defeat.
- Optional reward commands run for tracked participants on a legitimate defeat.
- Boss bars show shared HP and are visible to participants plus nearby players.
- Active encounters and remaining global cooldowns are saved to `config/alpha-encounter/state.json` and restored after restart.

## Performance behavior

- Normal entities and unconfigured Pokemon are immediate no-ops in the damage hook.
- Managed Pokemon are indexed by entity UUID.
- Natural spawn checks are staggered per player, avoiding synchronized bursts.
- Hunt/path requests run at a configurable cadence instead of every tick.
- Boss bars update at a separate configurable cadence.
- Persistence is dirty-state based and written on an interval or explicit admin save.
- No SVFrame/MMO polling or dependency calls are present.

## Config

First run creates:

`config/alpha-encounter/config.json`

The config contains:

- global spawn/hunt/bossbar/persistence cadence
- reusable tiers (`regional`, `signature`, `apex` by default)
- HP multiplier
- aggressive mode
- aggro/leash radius
- chase speed
- battle trigger distance
- re-engage cooldown
- bossbar range/color
- encounter PokemonProperties
- dimension + biome restrictions
- natural spawn weight/distance/cap/cooldown
- lifecycle animations
- catch phase
- participant reward commands
- spawn/defeat/catch announcements

### Default Mount Yeager encounter

The generated config contains:

- ID: `mount_yeager_godzilla`
- Pokemon: `tyranitar level=95 alpha=true cosmetic_item=godzilla`
- dimension: `minecraft:overworld`
- biome: `bestiary:mount_yeager`
- tier: `apex`
- natural max-active: 1
- long global cooldown
- aggressive hunt behavior
- boss bar
- catch phase after defeat

`cosmetic_item=godzilla` is intentional: the resource-pack resolver consumes the resulting `cosmetic_item-godzilla` aspect. `tyranitar_godzilla` is the model/poser/animation family name, not the PokemonProperties aspect key.

## Animation bridge

Encounter JSON contains semantic lifecycle animation names only (`aggro`, `hit`, `battleStart`, `battleEnd`, `defeat`). It does **not** contain hard-coded attack timing such as `hitAtTick`.

This keeps encounter logic independent from resource-pack animation lengths. Mount Yeager uses the Godzilla resource-pack animation family as a test case, including `special` for the large battle-start spectacle.

## Admin commands

All commands require vanilla permission level 2.

- `/alphaencounter help`
  - prints the command reference.
- `/alphaencounter reload`
  - reloads `config.json` without restarting the server.
- `/alphaencounter save`
  - immediately writes persistent encounter/cooldown state.
- `/alphaencounter debug`
  - prints lightweight runtime counters.
- `/alphaencounter debug reset`
  - clears runtime counters only.
- `/alphaencounter list`
  - lists active encounters with state, shared HP, and entity UUID.
- `/alphaencounter spawn <id>`
  - admin-spawns the encounter at the command source.
- `/alphaencounter spawn <id> <player>`
  - admin-spawns the encounter at a player's position.
  - ignores natural spawn cooldown/cap so it is useful for testing.
- `/alphaencounter inspect <nearest|uuid|id>`
  - prints state, HP, location, preferred target, last battle player, participant count, and re-engage timer.
- `/alphaencounter despawn <nearest|uuid|id>`
  - removes the encounter without rewards/catch phase.
- `/alphaencounter defeat <nearest|uuid|id>`
  - forces encounter defeat. Rewards are suppressed by default unless `rewardOnAdminDefeat=true` for that definition.
- `/alphaencounter sethp <nearest|uuid|id> <1-100>`
  - sets shared HP percentage and updates battle HP when applicable.
- `/alphaencounter battle <nearest|uuid|id> <player>`
  - queues a PVE battle with the selected encounter.
- `/alphaencounter anim <nearest|uuid|id> <animation>`
  - manually invokes an animation for resource-pack/poser testing.
- `/alphaencounter reset <id|all>`
  - clears natural-spawn global cooldown for one definition or all definitions.

Admin targeting accepts an exact active entity UUID, an encounter definition ID (nearest matching active instance), or `nearest`.

## Operator test flow

A practical Mount Yeager smoke test is:

1. `/alphaencounter reload`
2. `/alphaencounter spawn mount_yeager_godzilla <player>`
3. `/alphaencounter inspect nearest`
4. walk into aggro range and verify chase + bossbar
5. hit the boss in the field and verify shared HP decreases but never below 1
6. verify Cobblemon battle begins on the next tick
7. `/alphaencounter sethp nearest 25`
8. flee the battle and verify the encounter returns to HUNT/re-engages
9. `/alphaencounter anim nearest special`
10. defeat the boss and verify catch phase / reward behavior
11. `/alphaencounter save`, restart, and verify active-state/cooldown restoration

## Build validation

GitHub Actions builds this branch against Minecraft 1.21.1, Fabric API, Java 21, and Cobblemon 1.8.0. The workflow publishes the remapped JAR as the `Alpha-Encounter` artifact on successful runs.
