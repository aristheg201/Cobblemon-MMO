# Alpha-Encounter

Standalone Fabric 1.21.1 addon for Cobblemon 1.8.0 that turns configured Pokemon into roaming field encounters and battle bosses.

## Dependency boundary

Hard dependencies: Fabric API and Cobblemon only. SVFrameLib, SVFrameMMO, and SVFrameMMO-Cobblemon-Integration are intentionally not referenced. If another mod changes Minecraft damage before it reaches a Pokemon entity, Alpha-Encounter naturally consumes the resulting damage through the normal entity damage pipeline.

## Current runtime

- Config-driven encounter definitions and tiers.
- Dimension + biome + weight + distance + active-cap + cooldown spawn controls.
- Spawn checks are staggered per player to avoid synchronized tick spikes.
- Managed Pokemon are indexed by UUID; normal entities/Pokemon are an immediate no-op.
- Out-of-battle field damage is redirected at `LivingEntity.applyDamage -> setHealth`, after vanilla damage modification, so the raw Pokemon entity is not killed and no heal-back trick is needed.
- Player opening hits are applied to encounter HP and queue a Cobblemon PVE battle on the next server tick.
- `/alphaencounter reload` reloads config.
- `/alphaencounter debug` prints lightweight runtime counters.

## Default example

The first generated `config/alpha-encounter/config.json` includes a Mount Yeager encounter:

- `cobblemon:tyranitar`
- `alpha=true`
- `cosmetic_item=godzilla` (resolves the resource-pack `cosmetic_item-godzilla` aspect)
- dimension `minecraft:overworld`
- biome `bestiary:mount_yeager`
- Apex tier

## Design constraints

Animation timing is not intended to be authored in encounter JSON. Alpha-Encounter should reuse Cobblemon poser/move animation lifecycle where possible, with optional integration metadata only for exceptional resource-pack attacks.

Performance work should remove duplicate work, allocations, polling, and unnecessary path recomputation. It must not make active encounters sluggish or reduce gameplay cadence merely to lower CPU usage.
