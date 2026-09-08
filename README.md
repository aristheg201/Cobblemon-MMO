# Alpha-Encounter

Standalone Fabric 1.21.1 addon for Cobblemon 1.8.0 that turns configured Pokemon into roaming field encounters and shared-HP battle bosses.

## Dependency boundary

Hard dependencies: Fabric API and Cobblemon only. SVFrameLib, SVFrameMMO, and SVFrameMMO-Cobblemon-Integration are intentionally not referenced. Minecraft damage is allowed to flow through the normal entity damage pipeline so other installed combat mods can participate naturally.

## Encounter lifecycle

`IDLE -> HUNT -> BATTLE_PENDING -> BATTLE -> HUNT/DEFEATED`

- Alpha encounters attack players in the world with vanilla Minecraft damage and do not auto-start a battle by proximity.
- A player damaging a managed Alpha queues Cobblemon PVE battle on the next server tick.
- Shared HP is mapped proportionally to one Cobblemon battle HP bar. There is no multi-phase KO or phase respawn.
- A local battle KO is the encounter defeat. If the battle ends before KO, remaining local HP maps back to shared HP and the encounter returns to HUNT.
- Optional catch and reward phases run after the single final defeat.

## Text and integrations

- All encounter messages and bossbar titles use MiniMessage through bundled Adventure Fabric.
- Text Placeholder API (`placeholder-api`) is a soft dependency. If installed, Alpha-Encounter registers global placeholders and parses placeholders from other mods in its configured messages.
- Message files live under `config/alpha-encounter/messages/<language>.json`.
- Bossbar profiles live under `config/alpha-encounter/bossbars/*.json`.
- Encounter files reference `messageProfile` and `bossBarProfile` instead of carrying user-facing text.
- Internal MiniMessage tags include `<ae_name>`, `<ae_id>`, `<ae_tier>`, `<ae_state>`, `<ae_hp>`, `<ae_max_hp>`, `<ae_hp_percent>`, `<ae_species>`, `<ae_level>`, `<ae_aspects>`, `<ae_dimension>`, `<ae_biome>`, `<ae_distance>`, and `<player_name>`.
- Placeholder API exports `%alpha_encounter:active_count%`, `%alpha_encounter:cooldown_seconds <id>%`, and target-aware placeholders such as `%alpha_encounter:name nearest%`, `%alpha_encounter:hp_percent nearest%`, `%alpha_encounter:species nearest%`, and `%alpha_encounter:distance nearest%`.

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
