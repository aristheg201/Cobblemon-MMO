# Local build and runtime QA

Use a Java 21 **JDK**, not a JRE. From the selected branch:

```bash
export JAVA_HOME=/path/to/jdk-21
./scripts/build-local.sh
```

The script uses Gradle 8.8 for Lib/MMO/Items/Mobs and the integration’s Gradle 9.2.1 wrapper for its newer Loom. It does not use GitHub Actions. Each branch carries a synchronized snapshot of its common dependencies. Build only one current version of each mod; remove older JARs from the runtime `mods` folder.

Install Fabric API 0.116.9+1.21.1. The integration also needs Cobblemon 1.8.0, Polymer Core/Resource Pack 0.9.19+1.21.1 and their dependencies. Cobblemon itself requires its client installation; the SVFrame gameplay mods use server logic and vanilla packets. Install all five locally for singleplayer.

## Hero’s Entrance

Administrator setup, replacing `Player`:

```text
/teachskill Player HERO_ENTRANCE 1
/svframemmo skill bind 1 HERO_ENTRANCE
/svframemmo skill castslot 1
```

Binding/casting commands run as the player. Alternatively, `/svframelib cast HERO_ENTRANCE` is an administrative raw cast. Look at the ground to aim the gold landing ring. Sneak or use `/svframelib hero confirm` to commit; otherwise the aim timer commits. `/svframelib hero cancel` aborts safely. Ceiling obstruction, unsafe ground, unloaded terrain and world borders reject the movement. Damage uses the registered skill damage pipeline and excludes the caster; creative/spectator players, teammates and disabled PvP are respected by this ability.

`config/SVFrameLib/skill/hero_entrance.yml` contains range, radius, height, damage, knock-up, aim/descent time and cost parameters. The Items example `hero_entrance_focus` demonstrates USE activation. It shares the skill cooldown with the class cast.

## Explicit runtime harness

Set `SVFRAME_RUNTIME_QA=1` before starting the server/client process, then run as an administrator:

```text
/svframelib hero qa
/svframelib nativeqa
/svframelib referenceqa
```

These commands **alter the QA world**: the hero test builds a stone platform at y=64, moves the player, and spawns temporary cows. Use a disposable world. The native test creates temporary targets, a temporary wall, and temporarily loads a Nether chunk. Reports go to `qa/hero-entrance-runtime.json` and `qa/native-skill-runtime.json` in the process working directory. Keep gameplay focused/unpaused for captures and let each test finish before starting another.

The hero test checks launch, duplicate rejection, camera targeting, range boundaries, damage, knock-up, landing, single impact, gravity restoration, restart and cancellation. Native QA checks wall/swept collision, caster exclusion, dimension ownership, model expiration, and item resource/cooldown behavior. Items’ existing real server smoke is enabled with `SVFRAMEITEMS_CI_RUNTIME_SMOKE=1`.

## Resource-pack visuals

Build the optional original demonstration pack with `python3 SVFrameLib/tools/build_visual_pack.py`. Install/select `SVFrameVisuals-1.21.1.zip` as a normal client resource pack; on a dedicated server, host it and configure Minecraft’s usual `resource-pack` server properties.

`/svframelib visual soul_blade` renders a temporary animated blade. `/svframelib visual reload` reloads `config/SVFrameLib/visual-models.yml`. Each model has up to 32 native bones, each selecting an item, CustomModelData, start/end scale, offset, end-y and spin. Models have bounded lifetimes and disappear on owner death/dimension change/server shutdown.

A custom Lib script can use:

```yaml
blade_demo:
  public: true
  mechanics:
    - 'display_model{model=soul_blade;ticks=80;follow=true}'
```

Put it under `config/SVFrameLib/script`, then reload Lib. This simple YAML format remains available. Imported animated models live in `config/SVFrameLib/visual-models/*.json`; they can contain up to 128 cube displays per model and 241 affine pose frames per cube. The overall display cap is 512.

### Supplied class references and original visuals

Install PyYAML for the class importer (`python3 -m pip install PyYAML`). Extract the supplied configuration archives locally, then run:

```bash
python3 SVFrameLib/tools/import_reference_classes.py /path/config2 /path/amk /path/server
python3 SVFrameLib/tools/import_blockbench_visuals.py /path/config2/ModelEngine/blueprints \
  --model vfx_soul_blade --model vfx_death_strike_1 --model vfx_death_wings_1 \
  --sounds-assets /path/config1/ItemsAdder/contents/death_knight/resourcepack/assets \
  --output /path/imported
```

The class importer preserves existing destination files unless `--replace` is supplied; it copies only the two class configurations and fourteen skill definitions. Copy `reference-models.json` into the server's `config/SVFrameLib/visual-models/` and select the generated `SVFrameReferenceVisuals-1.21.1.zip` client pack. Restart after class/skill imports; `/svframelib visual reload` reloads visual definitions. `/svframelib visual vfx_soul_blade` previews the original animation; `/svframelib cast CLS_DEATH_KNIGHT_DEATH_SENTENCE` and `/svframelib cast CLS_ANTI_MAGE_KNIGHT_METEOR_LANDING` preview the landing abilities. These administrative casts bypass class costs; ordinary class/item casts apply costs.

`/svframelib referenceqa`, enabled by the same QA environment flag, temporarily moves the player, damages/heals them and spawns a cow. It writes `qa/reference-classes-runtime.json` after 21 checks. Run it in a disposable QA world and wait for the report before another test. The class adapters are explicit native implementations; importing these configs does not enable arbitrary MythicMobs or Skript files. See the audit for remaining timing/visual differences.

Validate the converter with `python3 -m unittest discover -s SVFrameLib/tools -p 'test_*.py'`. The converter rejects unsupported model types and expressions instead of silently exporting a static approximation.

## SVFrameMobs

```text
/svframemobs list
/svframemobs spawn HeroTrainingDummy
/svframemobs spawn StormSentinel
/svframemobs reload
```

Definitions live in `config/SVFrameMobs/mobs`. Supported fields: Type, Display, Health, Damage, Armor, MovementSpeed, Options (NoAI/Silent), Equipment, Drops, Skills. Supported skill mechanics: damage, velocity, potion, message, effect:particles, skill (registered Lib skill). Targeters: self, target, trigger, PlayersInRadius/PIR; triggers: onSpawn, onDeath, onDamaged, onAttack, onTimer:N. Drops supplement vanilla loot. Persistent vanilla entity tags preserve the definition identity across save/restart. Unsupported definition fields/mechanics fail reload; previous definitions stay active.

## Inspection evidence

See `docs/qa/` for JSON reports and screenshots, and `dist/` for locally built artifacts and checksums. `PARITY-AUDIT.md` states the remaining upstream feature gaps; a passing build does not prove complete plugin parity.
