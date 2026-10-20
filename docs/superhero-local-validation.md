# Local superhero validation — 2026-10-20

This records observed results, not full production acceptance. Kamen Rider
work remains paused. Builds and native tests were local; no GitHub Actions or
public publishing were used.

## Automated and dedicated-server checks

- 141 JUnit cases passed: 71 core, 34 SVFrameLib, 36 client.
- SVFrameMMO native smoke passed 72 skill-bar mappings and resource preferences.
- Real dedicated server: six selectable classes, all 42 active casts accepted,
  exact initial resource costs, three supported free recasts. Damage-bearing
  casts recorded real target damage; utility behavior needs the additional
  checks below.
- Eight native passive scenarios passed: Batman combo, Sorcerer Mystic stacks
  and empowered burst, Iron Man Overdrive, Thor Static Charge/resource gain,
  Spider-Man dash instinct, Strange emergency shield, Batman emergency escape,
  Flash poison/wither/nausea immunity.
- Two-client Tao Mandalas counter test reduced frontal incoming damage and
  inflicted reflected damage on the attacking player.

Private runtime evidence:

| Area | Workspace path |
| --- | --- |
| All active casts | `/workspace/qa-evidence/superheroes/integrated-native-probe/` |
| Passive scenarios | `/workspace/qa-evidence/superheroes/passives/` |
| Counter interaction | `/workspace/qa-evidence/superheroes/multiplayer/mandala-counter.json` |
| Armor/pack integration | `/workspace/qa-evidence/superheroes/client-hud-packs/` |
| Better Combat and current real-item grip | `/workspace/qa-evidence/superheroes/better-combat/` |

## Better Combat and current Stormbreaker correction

Actual Better Combat 2.4.0, playerAnimator 2.0.4 and Cloth Config 15.0.140
loaded on the Fabric 1.21.1 dedicated server and both clients. Native checks
passed: normal swings damaged the target, both peers saw attacks, EMF hook
registered, Detailed Animations yielded, a class cast stopped the weapon
animation, the skill damaged its target, and the attack state expired.
`native-combat-probe.json` contains observations and checks; two real videos
capture local and observer views.

Native comparison of empty hand, iron axe, plain Stormbreaker and SVFrame
Stormbreaker confirmed equivalent axe damage/attack speed, with no doubled
native attributes. Actual first-person right/left grip screenshots and a
third-person skill frame were inspected. A later user correction specifically
required the cutting edge to face forward, without shrinking the axe. Native
first-person and side-view images in `direction-*.png` supersede earlier grip
images for orientation acceptance. Geometry tests now verify both fists, an
upright haft, a forward cutting edge, identical real/manifested poses, and
first-person blade direction on both sides. First-person scale remains 0.68. The built-in hand follows the same
item transform as the shaft; no static image is substituted for geometry.

The client also started without Better Combat. Joining a Better Combat server
without its mod fails its synced registry requirements, as expected; this is
not a supported mixed-mod multiplayer configuration.

## Remaining acceptance work

- Full native lifecycle matrix: death/respawn, dimensions, disconnect/reconnect,
  restart, and repeated equipment changes without leaked buffs.
- Detailed utility behavior: actual allied/enemy portal traversal, Spider-Man
  wall crawl, flight/glide transitions, Flash movement and projectile catching,
  safe phase recovery, and full PvP/friendly-team/crowd-control budget cases.
- Visual polish/acceptance of all six classes and every skill; current clone
  actors use temporary equipped ArmorStands rather than full skin projections.
- Remaining balance/API follow-up: configured Flash movement cap, Thor force
  gain from blocks, and integration of shield destruction with non-hero shield
  providers. Hero absorption's aggregate ceiling is currently a fixed 35% HP.

No claim is made that those remaining cases passed. The targeted Stormbreaker
grip and Better Combat fixes are separately verified above.
