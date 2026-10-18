# Current model review

The user rejected the first Ryuki/Knight/Decade model pass. It is retained under `rejected-card-riders-v1/` and must not be distributed as accepted work. `blockbench/` now contains the refined v5 candidate: closed faceted masks, corrected Ryuki silver chest armor, wrapped Knight grille/jaw, integrated Decade barcode, painted contract deck crests and articulated S-shaped dragons/Darkwing. These are real Blockbench exports, not native gameplay screenshots. User visual acceptance is still pending.

Candidate full local build passes44tests(Core26+Client18); see `build/card-riders-v5-build.log` and retained XML. The native dedicated server boots with43classes/1334skills and the actual2client fixture is being reconnected for visual/behavioral checks. No0.1.11release has been published.

`runtime/shining-caliber-grip/review.json` supersedes the earlier width-only Caliber assessment: native both-hand contact, real Shining cast, crouch/swing and first-person views pass, and real inventory was preserved. The previous floating grip evidence remains failed.

The old belt QA failure was traced to the fixture retaining a stale Trinkets inventory after `update()` replaced it. The v5 fixture corrects that; native revalidation remains pending. Do not use an admin-set belt as proof of player equipment permissions.

Earlier evidence and limitations follow.

# Rider model and equipment checks — 2026-10-18

This is presentation acceptance, not acceptance of either playable class. The full port release gate still fails. No 0.1.11 release has been published.

## Actual builds and environment

Java 21.0.2, Minecraft 1.21.1, Fabric Loader 0.18.4, Fabric API, Cobblemon 1.8.1, supplied Filament and Tough As Nails. Dedicated raw Bridge layout behind Velocity with two real Minecraft clients. Client resources are bundled in its JAR. No separate model resource pack was installed.

Core 23 tests and Client 18 tests pass. `build/runtime-implementation-build.log` and the XML reports preserve the actual results. The MMO change also passes its native smoke runner (72 skill-bar mappings); this is not a recast gameplay acceptance claim.

## Editable assets and native evidence

14 original editable Blockbench 5.2.2 projects, seven character/summon geometries, 24 armor icons, five weapon models and two belt models. The 512px painted atlas has 23 materials. There are 31 SVFrameItems definitions: 24 armor pieces, five weapons, two belts. Class-specific gameplay and optional belt backend behavior are separate acceptance requirements.

`blockbench/` contains editor renders. `runtime/` contains raw Minecraft X11 captures. These are deliberately distinguished. The native gear runner generates actual SVFrameItems stacks and its reports explicitly set `classGameplayVerified: false`.

The six armor sets were captured front, rear and both sides. Agito Ground and Zolda also have native mask close-ups from front, side and rear. The player head is sealed by the helmet and restored after removal. Native Ground walking and crouching show the geometry attached to animated bones. Native armor evaluates to 14. Tough As Nails Thermal Tuning was observed on generated armor; this does not replace the outstanding enchantment/backend matrix.

Magnugiga's initial floor/facing/scale failures were retained in `runtime-before-fix/`. The native renderer now compensates the item origin and facing, while the server supplies the complete vanilla display transform with scale 1.6. `runtime/magnugiga-front.png` and `magnugiga-rear.png` show the corrected summon display with the human actor for scale. This does not verify the Final Vent summon timeline.

## Shining Caliber correction requested by the user

The first blade was too broad and thick and obscured the handle when dual wielded. It is retained as `runtime-before-fix/shining-thick-dual-blades.png`. The corrected editable model has a narrow swept silver edge, a thin red spine, a ribbed black handle and a red/silver guard. Blade thickness is 0.85 model pixels. Its third-person scale is 0.52; first-person scale is 0.55.

`runtime/shining-caliber-correction/{front,side,rear}.png` are actual dual-wield Minecraft captures of the corrected model. `commands.json` contains the native equipment and camera commands and actual stack data. `blockbench/shining-caliber-profile.png` is the separate editor profile. The corrected model's native geometry and display transforms were written from the real Blockbench desktop, not an image mock-up.

## Still incomplete

The width-only Caliber renders failed the subsequent user grip review: the handle was outside the glove. See `runtime/shining-caliber-correction/REVIEW-FAILED.json`. The next Blockbench calibration moves all three form weapon handles into their actual authored gloves through vanilla held-item matrices. `RiderGripTest` passes both hands for all three weapons; final native grip screenshots are still pending.

New Rider gameplay is undergoing native QA. The new runtime and two class configurations compile, but neither complete class is certified. Giga Cannons, Magnu Defender and Strike Vent gauntlet presentation, full Magnugiga docking/barrage animation, held form weapons, exact kick movement, original authored sounds and Cobblemon VFX still require implementation or integration checks. No synthesized cue is represented as a supplied film recording. Progression, purchases, multiplayer lifecycle, optional accessory backend permutations and combat load acceptance remain pending.

The seven-byte-identity checks in `preserved-kabuto.json` predate new Rider runtime integration and protect the delivered Kabuto fixes; they do not certify all new shared-code regressions.
