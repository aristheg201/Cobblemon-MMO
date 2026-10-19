# Skill bar native review — 0.1.14

Minecraft1.21.1 / Fabric0.18.4 / Java21; actual dedicated server and an actual QAPlayer client using Cobblemon1.8.1, Trinkets, Tough As Nails and Filament. Software OpenGL, display :99. PNGs are raw FFmpeg captures at the actual client window dimensions.

The original issue is visible in `../armor-reference-v13/runtime/decade-first-person.png`: seven identical belt icons inside ornate frames. The first HUD revision and its small-window action-message collision are preserved under `review-v1`; the uncapped large-GUI draft is retained under `review-v2`. The final bar caps its rendering at GUI scale2 and keeps its bottom anchor above vanilla messages. Final evidence lives only under `runtime`.

Final result: **11/11 native HUD scenarios PASS**. `runtime/results.json` records normal/elevated Ryuki, Knight and Decade bars, a real Ryuki cooldown, a real Decade recast, F hiding the bar, a 960×540 native window, and Kabuto gauges sharing the skill-row anchor. Per-case client observations record the actual rendered slot count and acknowledged bindings. These fixtures grant test progression/equipment and call actual server skill APIs; they do not simulate casts. One client is sufficient for this client-renderer regression; the earlier multiplayer gameplay acceptance remains in previous deliveries.

Reviewed visual requirements: distinct skill glyphs; separate readable key footers; Rider color identity; visible cooldown/recast/form states; correct alternate icons in Survive; compact class/passive caption; no overlap with vanilla action messages or health/mana/stamina/thirst rows; no clipping at the smaller native window. Aesthetic approval is not asserted on the user's behalf.

Reproduce in an isolated test world with opt-in `SVFRAME_RUNTIME_QA=1`, the real QAPlayer client on :99, ffmpeg and xdotool:

```sh
FANTASYHUB_QA_RCON_PASSWORD=... python3 FantasyHub/tools/run_skillbar_native_qa.py \
  --server /path/to/test-server --output /path/to/evidence --xdotool /path/to/xdotool
```

The script teleports/equips the test player, spawns tagged test zombies and temporarily resizes the native game window, restoring its dimensions at the end. Keep the password out of logs and shell history.

104 local JUnit tests pass, plus existing MMO72 mapping and preference codec/native-core checks. Final JARs are checked against the bytes installed during native testing before delivery. No CI is invoked. Existing Mojang public-key/network and external Polymer-pack warnings are environmental and are not Rider HUD render failures.

The initial Kabuto fixture still wore Decade’s belt and correctly failed to transform. The HUD count alone was not treated as gauge acceptance. The fixture now clears that occupied slot via the real Trinkets command before equipping Kabuto’s belt through the existing API; the native gauge case was then retested successfully. Its original failed observation remains under `fixture-failures`.
