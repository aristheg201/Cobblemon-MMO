# SVFrameWorldBoss

Separate Fabric 1.21.1 / Java 21 server encounter mod. Install the built
SVFrameWorldBoss JAR beside the updated FantasyHubCore, SVFrameLib, SVFrameMMO,
and native Cobblemon integration. Class NPC visuals require matching FantasyHubClient.
There are no nested framework or third party runtimes. NovaRaids was inspected for
phase/scheduling mechanics; its All Rights Reserved code is neither copied nor bundled.

First startup writes `config/svframeworldboss/`:

* `bosses.json`: ordered native Pokemon/class NPC rotation. Defaults: Charizard, Thor and
  Spider-Man Web Guardian. Pokemon uses species, moveset, scale and native HP (maximum 1024).
* `locations.json`: empty until an operator saves an arena. No blocks change.
* `schedule.json`: Asia/Ho_Chi_Minh daily 12:00/18:00/21:00; warnings 300/60s,
  setup 60s, fight 600s. HH:mm triggers within that minute; HH:mm:ss triggers at that second. Downtime never backfills.
* `rewards.json`: 12/3 banked Class Sigils for win/timeout; requires 0.5% actual HP
  damage and normally 30 seconds alive within the arena after first contribution.
  For victories shorter than 30 seconds, contributors must remain for the available
  time after their first hit; a late hit in a longer fight still requires 30 seconds.
* `state.json`: fsynced atomic occurrences, encounters and per-player mailbox. Back
  this up with the world and player data. Do not delete to reset a running encounter.

Player commands:

```
/worldboss
/worldboss info
/worldboss top
/worldboss join <arena>
/worldboss mailbox
/worldboss claim
```

Operator commands (permission level 2):

```
/worldboss setarena <arena> [radius:8..128]
/worldboss start <boss> <arena>
/worldboss abort
/worldboss test start <boss> <arena>
/worldboss test win
/worldboss test loss
/worldboss reload
```

Stand on a solid floor with two air blocks before saving an arena. Provide enough
space for the scaled Pokemon and a safe combat area. Test start skips setup;
test win/loss preserves real eligibility requirements. Abort/restart do not issue
loss rewards. Reload requires all encounters stopped. Previously earned damage and
presence survive player death/disconnect; offline rewards wait for login/claim.
Sigils are banked native RPG material, so full inventories cannot drop or lose them.
Native player settlement markers prevent repeated grants after a crash/reconnect.

## Verification

JUnit verifies state transitions, qualification, timeout versus abort, timezone,
no schedule catchup/duplicate, durable mailbox replay, per-player settlement closure
and restart recovery. Native client/server evidence is supplied in the delivery QA folder. The user's exact
289-mod Windows pack remains unverified. Automated tests are not visual evidence.

## Native QA

1. Create `/worldboss setarena qa 32`. With two survival players and an observer,
   run `/worldboss test start charizard qa`. Verify real Pokemon, no capture/formal
   battle, health/time/phase bossbar, visible 2-second circle plus native telegraph,
   and actual player HP loss. Move outside the circle to demonstrate a dodge.
2. Damage with melee/projectile/hero skill/owned Pokemon. Compare actual HP loss
   and `/worldboss top`, including lethal damage. Deal at least 5 default boss HP
   and remain alive/in range for 30 seconds. An AFK second account earns nothing.
3. Use `/worldboss test win`: eligible players receive exactly 12; reconnect and
   repeat claim without additional credit. Test loss for exactly 3, then actual
   boss death and actual fight timeout.
4. Disconnect an eligible account before finish. Verify pending mailbox, reconnect
   and one delivery. Repeat with full inventory: bank balance increases, no drops.
5. Restart during setup/fight: no loss reward, actor removed, forced arena chunk
   released, same occurrence never starts twice. Restart after settlement before
   delivery: pending rewards remain and native markers prevent replay.
6. Run `/worldboss test start thor qa`. Verify player-shaped suit/weapon actor,
   real navigation and class casts, phase changes under 60%/25% HP, health damage
   and control. Repeat attribution/rewards. Verify no fake player data is created.
7. Set the schedule to a wall time 1-2 minutes ahead and shortened durations,
   reload, verify exactly one start per arena across reload/restart, then restore
   production settings. Record clips, HP values, balances and logs. Mark all
   unrun tests, especially the Windows pack, as unverified.

## Add or tune bosses by editing configuration

Every ID in `bosses.json` is data. New bosses require no Java changes. The shipped
sample includes a third `web_guardian` class boss with its own rotation, phase and
18/4 reward overrides. Copy an entry, choose a unique lowercase ID and name, then
edit the native Pokemon properties or class ID. Existing files remain compatible;
missing optional fields receive defaults. Restart is not required: stop encounters
with `/worldboss abort`, then `/worldboss reload`.

Class boss example to append to the `bosses` array:

```json
{
  "id": "web_guardian",
  "name": "Spider-Man — Web Guardian",
  "type": "class",
  "classId": "SPIDER_MAN",
  "health": 800,
  "windupSeconds": 3,
  "attackSeconds": 5,
  "telegraphColor": "33CCFF",
  "skillIds": ["WEB_SHOOTER_BARRAGE", "WEB_BLITZ", "COCOON_SLAM"],
  "cooldownMultiplier": 1.2,
  "damageMultiplier": 0.8,
  "winReward": 18,
  "lossReward": 4,
  "phases": [
    {
      "healthBelow": 0.5,
      "skillIds": ["WORLD_WEB_TRAP", "AIR_COMBO", "WEB_BLITZ"],
      "cooldownMultiplier": 0.65,
      "damageMultiplier": 1.4
    }
  ]
}
```

`skillIds` is an ordered class ability rotation. Both keys such as `WEB_BLITZ` and
full IDs such as `SPIDER_MAN_WEB_BLITZ` work; skills from another class are rejected.
An empty list uses the class's default rotation. A phase's empty list inherits the
boss's base rotation. All classes and abilities must already exist in the active
hero catalog; a new boss ID does not define a new hero class or skill implementation.

Phase thresholds must descend strictly between 0.01 and 0.99. Entering a lower HP
phase switches the rotation and applies that phase's multipliers to the boss's base
multipliers. Thus base damage 0.8 with phase damage 1.4 produces 1.12 times native
damage. Cooldown multipliers below 1 attack faster. Defaults transition at 60%/25%
with cooldown factors 0.85/0.70 and damage factors 1.15/1.30. `phases: []` disables
phase transitions. Multipliers and skill rotations are validated before installing
any replacement configuration. Unknown species/classes/skills, duplicate boss IDs,
invalid thresholds and missing fields report their configuration path; a rejected
reload keeps the previous working configuration.

For another Pokemon, append a native species entry. The same HP phases, cooldown
and damage multipliers apply, while its native moveset supplies its attacks:

```json
{
  "id": "sky_guardian",
  "name": "Rayquaza — Sky Guardian",
  "type": "pokemon",
  "properties": "rayquaza level=100 uncatchable=true",
  "health": 1000,
  "scale": 2.5,
  "windupSeconds": 3,
  "attackSeconds": 6,
  "telegraphColor": "FFAA33",
  "winReward": 24,
  "lossReward": 6,
  "phases": [
    {"healthBelow": 0.4, "cooldownMultiplier": 0.5, "damageMultiplier": 1.5}
  ]
}
```

Use only installed native species/properties. Class `skillIds` do not apply to
Pokemon; their moves remain native Pokemon data. Capture/formal battle protection
is always forced by the encounter, even if omitted from the property string.

To select specific bosses and arenas by time, add `entries` to `schedule.json`:

```json
{
  "timezone": "Asia/Ho_Chi_Minh",
  "setupSeconds": 60,
  "fightSeconds": 600,
  "warningsSeconds": [300, 60],
  "entries": [
    {"time": "12:00", "bossIds": ["charizard"], "arenaIds": ["main"]},
    {"time": "18:00", "bossIds": ["thor", "web_guardian"], "arenaIds": ["main"]},
    {"time": "21:00", "bossIds": ["web_guardian"], "arenaIds": ["main"]}
  ]
}
```

Create `main` with `/worldboss setarena main 32` first. Nonempty `entries` replaces
legacy `times`. Each slot selects deterministically from its `bossIds` using the
persisted occurrence rotation; a single ID always selects that boss. Empty boss or
arena lists mean all configured bosses or arenas. With `entries: []`, legacy daily
`times` and the global rotation continue working. A location still runs at most one
encounter. If multiple entries target the same arena at the same time, the last
entry takes precedence.


## Schedule precision and fast victories

Both `HH:mm` and `HH:mm:ss` are supported in legacy `times` and schedule entry
`time` fields. `12:00` may trigger once during the current 12:00 minute;
`12:00:30` triggers only during that exact wall-clock second. Neither precision
backfills missed slots after downtime. Duplicate aliases such as `12:00` and
`12:00:00` are rejected. Second precision is useful for short QA schedules.

The fight clock starts when the setup phase actually changes into a spawned fight.
On a victory, required participation seconds are
`min(configured minimumParticipationSeconds, finishAt - fightAt)`. Recorded
participation only accrues alive and in the arena after the first real damage;
credited time is also capped by `finishAt - firstDamageAt`. A genuine instant win
can therefore reward contributors with zero elapsed seconds, while an AFK player
or someone below the damage threshold still receives nothing. A ten-second victory
requires ten credited seconds; a late hit cannot borrow time from before that hit.
Losses retain the configured participation requirement, normally 30 seconds.


## Telegraph, impact and recovery

Every damaging class cast and Pokemon strike announces its fixed target area before
impact. `windupSeconds` (1..30, default 2) sets the warning delay. Repeated particle
circles mark the impact footprint; beams also mark corridor edges. Moving out of
the marked area before impact avoids that attack. `telegraphColor` is six hexadecimal
RGB digits. `attackSeconds` sets the recovery period, scaled by phase cooldown.
Class attacks finish their native damage phases before this recovery begins. The
bossbar shows HP, phase, WINDUP/ATTACK/RECOVERY, attack countdown and encounter time.

Pokemon boss HP stays at the configured native maximum (1..1024); Cobblemon's normal
stat updates cannot overwrite that maximum or heal encounter damage. Ordinary
Pokemon retain their native battle/stat health synchronization.

## Installing the addon delivery

Install the class 0.2.4 full package first. Replace the existing Bridge and Cobblemon
Integration JARs with the two matching updates from this addon, and add
SVFrameWorldBoss on the dedicated server. Replace FantasyHubClient on clients with
the addon update: it guards Better Combat player-only animation calls for humanoid
bosses. Keep one JAR per mod ID; the filenames keep the base version. Use the
delivery SHA256 manifest to identify the matched build. Do not put the standalone
WorldBoss JAR on clients. The addon does not replace existing player progression.

Copy the four sample JSON files only for initial setup. Preserve customized files
on upgrades. `locations.json` intentionally ships empty: create an arena in your
world with `/worldboss setarena main 32` before enabling its schedule assignment.
No QA coordinates, accounts, journals or shortened QA timings are included.
