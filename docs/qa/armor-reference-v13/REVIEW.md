# Armor reference correction — native review

Source branch: `feature/kamen-rider-ryuki-knight-decade`. Exact commit and locally built JAR hashes accompany the build manifest. Minecraft 1.21.1 / Fabric 0.18.4 / Java 21; dedicated server plus two actual clients, Cobblemon 1.8.1, Trinkets, Tough As Nails and Filament present. Software OpenGL on Xvfb; screenshots are raw 1280×720 FFmpeg captures. They are not concept art or edited composites. No CI or GitHub Actions.

## Reference comparison

All five latest references were individually opened. The manifest records their hashes, categories and observed features. Native images and actual Blockbench geometry are reviewed separately.

| Reference | Rebuilt feature | Native views |
| --- | --- | --- |
| `1000391826.jpg` | Normal Ryuki's red limbs/gloves, black shoulder frames, silver V cage/pectorals and black deck | `runtime/ryuki-normal-{front,rear,left,right,three-quarter,helmet-front,helmet-side,helmet-rear,belt}.png` |
| `1000391827.jpg`, `1000395528.jpg` | Distinct Survive black suit/crimson-gold chest, raised shoulders, silver ribs and gold greaves | `runtime/ryuki-survive-{front,rear,left,right,three-quarter,belt}.png` |
| `1000395522.jpg` | Thin antennae, front gold crown plus side/rear temple sweep, red lenses, wrapped silver rails and jaw | `runtime/ryuki-survive-helmet-{front,side,rear}.png` |
| `1000391841.jpg` | Decade's green lenses, integral barcode, magenta/black armor, white diagonal frame, rectangular shin panels and Decadriver | `runtime/decade-{front,rear,left,right,three-quarter,helmet-front,helmet-side,helmet-rear,belt}.png` |

The back of each model includes shaped helmet seams/nape, torso yoke/spine/flex detail and rear arm/leg panels. Crouch/walk/swing/grip/first-person images are captured for all three forms. Weapon exports and existing grips are byte-preserved; inventory preservation is checked by the real running server.

During the first native review, the Survive crown was visibly too flat from the side. It was extended around the temples with rear fins, and silver rails were lengthened. Final native captures use the corrected geometry.

The art has recognizable design features adapted into voxel geometry; small belt symbols are original pixel interpretations. Aesthetic resemblance is a visual judgment, not a JUnit assertion. The user rejected 0.1.12; **no user acceptance or literal 100% likeness is claimed** for this revision.

## Actual runtime checks

Final result: **9/9 runtime scenarios PASS**, and both clients remained connected after the previously crashing summon attack. `runtime/results.json` is the authoritative scenario result list. Per-scenario JSON includes real cast results, form state, damage events, inventory snapshots and cleanup. The fixture uses real SVFrame class/equipment/skill APIs and actual F key input; it grants test progression/resources, so it does not repeat the earlier paid-class-menu acceptance test.

The focused scenarios cover normal Henshin, distinct Ryuki Survive routing, toggle-off restoration, unchanged Decade Complete model, F returning to human, three Survive attack branches and Decade Dimension Kick. The original 21 active skills, passives and persistence acceptance evidence remains in the earlier 0.1.12 delivery; balance/combat logic is retained in this correction.

Reproduce in an isolated test world with two real clients named QAPlayer and QAAttacker, X displays :99 and :101 and opt-in `SVFRAME_RUNTIME_QA=1` on server/clients:

```sh
FANTASYHUB_QA_RCON_PASSWORD=... python3 FantasyHub/tools/run_armor_reference_native_qa.py \
  --server /path/to/test-server --output /path/to/evidence --xdotool /path/to/xdotool
```

The harness teleports and equips test players and spawns tagged test zombies. It does not simulate gameplay. Keep the password out of logs and shell history. Final captures use a bounded existing arena; actor rotation is used for rear photos.

## Packaging regressions caught and fixed

1. Stale imported checksum index rejected the first client handshake. `processResources` now hashes the actual final merged assets. A real-resource verifier test checks them. Original failure is retained as `runtime/asset-index-failure-v1.json`.
2. A targeted armor export truncated the preload manifest, removing the existing dragon display model. Both clients crashed while rendering a native Dragranzer item-display; original reports remain under `failures/`. Build now generates the preload manifest from every exported Rider model, and a test verifies all exported models, including all four creatures, are registered. Corrected final JARs preload 60 models and verify all 507 indexed asset hashes. These failures are not counted as successful QA.

Existing environment warnings about blocked Mojang public-key fetch and Polymer/Filament's external vanilla-pack generation are retained in logs. Bundled Rider geometry/textures use the client JAR; external pack fallback is not a Rider asset acceptance result.

## Local automated checks

98 JUnit tests (Core 45, Client 23, Lib 30), no failures/errors; plus 72 MMO mapping cases and preference codec/native-core checks. `asset-inventory.json` independently verifies unchanged geometry for the other 21 forms, byte-identical first 41 atlas tiles, unchanged eight existing weapon exports and correct final indexed bytes. Test XML and build logs are included in the private source package.
