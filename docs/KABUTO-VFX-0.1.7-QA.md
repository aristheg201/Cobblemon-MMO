# Kabuto native VFX and Kunai Gun correction — 0.1.7

Scope: the requested richer Kabuto VFX and gun grip/orientation corrections. Full ecosystem parity remains incomplete.

Implementation: semantic S2C KabutoFx events, bounded client timelines, Minecraft particles plus the installed Cobblemon 1.8 ParticleStorm API. No gameplay authority is moved to the client.

First-person gun orientation is preserved as confirmed by the user. Third-person hand transforms are corrected independently, using actual Blockbench 5.2.2 editable source. The gun-fire pose is now a server-confirmed GUN_FIRE cue rather than relying on private item NBT that Polymer need not send to remote observers. The black receiver/pistol handle stays at the hand, the silver cylinder/red foreguard point forward, and the blade remains below the grip.

Native texture compatibility: two Cobblemon 1.8 descriptors reference unavailable sprite identifiers. Private descriptor instances normalize explosion_burst_flash to cobblemon:vanilla/flash and metal_sparks to cobblemon:generic/orb/scaling using the native Snowstorm parser. Shared Cobblemon repository entries and its assets are never modified/copied. All selected sprite PNG resources are checked before emission.

Server settings: config/FantasyHub/kabuto-vfx.yml, reloaded with /kabuto reload. Client Settings cycles effect density 0/50/100/150%; native Cobblemon effects can also be disabled in fantasyhub-client.json. Turning presentation off does not alter resources, cooldowns, collision or damage. Maximum 32 timelines/client, 384 Minecraft particle requests/tick at 100%, four native emitters/tick, with reduced vanilla particle settings respected. Server broadcast defaults to tracking players within 48 blocks and 96 recipient events/tick. These bounds are not a claim of full load-profile acceptance.

Final acceptance on the actual packaged Core/Bridge/Client 0.1.7 builds: PASS for this narrow request. Java21/FabricLoader0.18.4/Minecraft1.21.1/Cobblemon1.8.0, dedicated server and two real Minecraft clients through Velocity; Trinkets native belt API. No external resource pack.

| Scenario | Result | Retained evidence |
| --- | --- | --- |
| Native F/key3, Masked65.8HP, Rider, inventory NBT unchanged | PASS,5checks | qa/kabuto-vfx-0.1.7/native-f-final/verification.json |
| Native skill gameplay, CastOff landing, Clock/restore, Kick kill reset, projectile, bleed, Hyper | PASS | qa/kabuto-vfx-0.1.7/native-skills-final/summary.json and kabuto-runtime.json |
| Both actual particle engines on actor and observer;11native emitters, phase deltas, finite cleanup | PASS,60checks | qa/kabuto-vfx-0.1.7/visual-verified/verification.json |
| Density0 via real Settings button persists, own effects stop, observer effects and actual damage continue;100% restored | PASS,8checks | qa/kabuto-vfx-0.1.7/settings/verification.json |
| Gun direction and grip in first/third person while firing | PASS,manual right-hand frame review | qa/kabuto-vfx-0.1.7/gun-verified/verification.json and two gun-firing PNGs |
| Local Core12+Client13 tests | PASS,25tests | Packaged JUnit XML/build logs |
| Unchanged Mobs33tests | Retained baseline PASS | Previous unchanged module result XML; not a fresh Mobs rerun |

The two simultaneous gun videos and the VFX/native-F/native-skills videos are in the private delivery archive, with their actual runtime logs. Screenshots are direct screen/video captures, never generated images. Editable Blockbench exports are source evidence, not game screenshots.

An earlier gameplay fixture read its gun result before its75-server-tick callback executed under software-rendering load. The runner now waits for authoritative callback fields with a bounded timeout; the rerun passes. The unsuccessful fixture summary is retained. A separate draft build referenced a nonexistent gun accessor; it was corrected to the server-confirmed GUN_FIRE semantic cue before final builds/tests. These intermediate failures are not counted as passes.

Not certified here: left-hand runtime visuals, every camera pitch/GUI scale, combat-load profiling, full Kabuto progression/protection/death/crafting policies, or complete original ecosystem parity. Prior focused health reconnect and healing fixes remain documented in their own reports; this release does not invent new coverage for them. The machine-readable whole-port gate remains FAIL.

Reproduce in an isolated opt-in QA environment after connecting the two real clients:

```bash
python FantasyHub/tools/run_kabuto_casting_qa.py --server SERVER --output QA/native-f --display :99 --xdotool xdotool
python FantasyHub/tools/run_kabuto_qa.py --server SERVER --output QA/native-skills --display :99
# Restore Gun Mode after the previous sequence, then:
python FantasyHub/tools/run_kabuto_vfx_qa.py --server SERVER --output QA/vfx --display :101
# Native editor, already open on local DevTools9223:
python FantasyHub/tools/run_blockbench_authoring.py --script tools/fix_kabuto_gun_grip.js
```

F fixture needs a freshly connected, untransformed Kabuto with its real belt/weapon and CastOff bound in slot2 (visible key3). The skill runner requires Gun Mode at baseline. QA commands read local RCON credentials; none are distributed. Server authority, cooldown/resource checks and real tick callbacks are exercised, not replaced by fake GUI actions.
