# Cape flight 0.2.2 — focused local QA

Source: branch `feature/superhero-classes-balanced-local`, implementation commit
`40bfe4b888fc7480741126222853eab7182235c2`. Build manifest records that clean source
commit. The delivery documentation may have a later commit without code changes.
Minecraft 1.21.1/Fabric/Java 21, dedicated server plus two actual clients through
local Velocity. No GitHub Actions. Matching Bridge/Client 0.2.2, assets
`heroes-expansion-v3`, exact built SVFrameLib/MMO JARs.

## Verified

| Check | Result | Evidence in delivery `qa/` |
| --- | --- | --- |
| Local core/client/Lib unit tests | 151 passed, zero failures/errors | `../local-build/build-manifest.json`, JUnit XML |
| MMO nativeCoreSmoke | Passed | `../local-build/mmo-build.log` |
| Trinkets 3.10.0 equipment/flight matrix | 18/18 passed | `trinkets/summary.json` |
| Accessories 1.1.0-beta.54 equipment/flight matrix | 18/18 passed | `accessories/summary.json` |
| Real keyboard double-Jump | Flight enabled; second double-tap disabled | `lifecycle/real-keyboard-double-jump.json`, `real-keyboard-double-tap-off-final.json` |
| Death and actual respawn | Class retained, no flight permission/lease leakage | `lifecycle/death-respawn.json` |
| Dimension change during active flight | Class retained, flight lease released | `lifecycle/dimension-change-final.json` |
| Full server restart and two-client reconnect | Class/gear persisted, flight starts off, no permission/lease leakage | `lifecycle/restart-reconnect-final.json` |
| Save while actually flying | Live flight remains on; native saved mayfly/flying are false | `trinkets/flight-save-baseline.json`, `lifecycle/restart-before-final.json` |
| Thor and Strange fallback cape rendering | Inspected actual rendered geometry from second client, airborne actors | `visuals/*-flight-*.png`, `visuals/native-states.json` |
| Third-person Stormbreaker retained | Cutting blade below haft, downward carry | `visuals/stormbreaker-third-person-blade-down.png` |
| Cape inventory textures | Atlas entries and texture bytes verified in final JAR; no corresponding missing-texture warning in final two clients | `artifact-verification.json` |

Each backend matrix covers naked/wrong class or cape, missing individual Thor
pieces, cape held instead of equipped, cape removal, Strange cape without an
armor requirement, class change preserving equipment, native save baseline,
creative permission preservation, no leakage on return to Survival, and toggling
off. Positive flight cases are observed after more than 20 ticks, so zero upkeep
must sustain flight instead of immediately switching off.

Native QA found a real bug in zero-cost resource handling: the native setter
returns false when no value changes. Zero upkeep now succeeds without calling the
setter; a regression test covers it. The client cape item sprites were also added
to the existing imported atlas after a startup warning exposed their omission.
Earlier unsuccessful harness probes are retained and excluded from pass counts.

## Scope and limits

The two QA users are offline profiles. They have no authenticated personal skin
cape, and Mojang profile requests are unavailable in this environment. Personal
cape priority is covered by two client logic tests and inspection of the native
CapeFeature path; authenticated account cape animation was not runtime-certified.
The hero fallback capes were actually rendered and inspected. No generated image
is used as a runtime screenshot.

This report certifies the added cape/flight behavior and the listed regressions.
It does not upgrade all six classes or every earlier Kamen Rider/model/utility
requirement to full production acceptance. Existing broader limitations remain
listed in `superhero-local-validation.md`.
