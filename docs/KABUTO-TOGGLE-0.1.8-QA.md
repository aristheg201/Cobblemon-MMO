# Kabuto transformation toggle —0.1.8

Requested behavior: explicitly closing the F skill bar returns Kabuto to human, and casting Henshin while already transformed also returns to human. Henshin can be used again to transform. Forced/post-cast closure is not treated as a manual F close; Scroller quit-on-cast is deliberately excluded so CastOff is not immediately undone. Cancelled close events leave the form intact.

Server removes Masked/Rider modifiers deterministically, cancels Clock/Hyper/leap, releases the actor's freeze leases, and publishes inactive form state. No inventory stacks are replaced. Current health is bounded by the final maximum, never filled by the toggle; cooldowns, weapon mode and energy/gauge are not reset/refilled. Client hides the form and clears its animation/effect timelines. Normal gear returns; physical Kabuto suit presentation is dormant while human, with its actual equipped stacks retained. The Zecter belt remains visible.

A preliminary loose Client0.1.8 was supplied on request before runtime finished. The inherited Dragonborn hat feature still drew the Kabuto helmet while human; the final **FantasyHubClient-0.1.8-human-fix.jar** removes that fallback. Core/Bridge0.1.8 are unchanged. The corrected client was cold-launched on both real clients and connected through Velocity. Remove the earlier client JAR, do not install two copies with the same mod ID.

Build:13Core+13Client tests PASS.33Mobs tests are retained from its unchanged baseline. Raw Bridge and bundled Core classes and all5nested framework hashes match. Actual tested binaries match the uploaded local JAR hashes recorded in KABUTO-0.1.8-DELIVERY.json; Drive metadata readback confirms name/size/folder, not remote checksums.

Real runtime: Minecraft1.21.1, Java21, FabricLoader0.18.4, Cobblemon1.8.0, Trinkets, dedicated server and2real clients through Velocity. **18focused checks PASS**: native F on/off, actual HP/armor/knockback/movement/attack attributes, native bound CastOff, native Henshin off/on, HyperClock active before cancellation, mob position actually pinned before cancellation and movable afterward, all resources/modifiers restored, finite effects, full InventoryNBT identical, and damage feedback compared immediately around each deactivation. Human appearance was manually inspected from actual screenshots on actor and observer, including the helmet.

An intermediate test used a disposable Clock fixture cow after its scheduled removal, then was corrected to use a separate persistent QA cow. Another overbroad damage-counter assertion included the preceding movement skill sequence; the final runner compares feedback around the actual deactivation actions. The unsuccessful intermediate result is retained, not counted as a pass. No production damage/hurt feedback was suppressed and no gameplay setting was disabled.

Evidence: docs/qa/kabuto-toggle-0.1.8/verification.json and observations.json, paired screenshots for Masked/Rider/human; unedited2-client videos remain /workspace/qa-evidence/kabuto-toggle-0.1.8/toggle-final/{native-toggle,observer-toggle}.mp4. Commands and runner read local RCON credentials without distributing them.

Reproduce after connecting a fresh untransformed QA Kabuto with real belt, weapon and Henshin/CastOff bound1/2 (visible keys2/3):

```bash
python FantasyHub/tools/run_kabuto_toggle_qa.py --server SERVER --output QA --display :99 --observer-display :101 --xdotool xdotool
```

No configuration reset is needed. Update Client plus either Core or Bridge to0.1.8; keep existing Lib/MMO/Items/Mobs/Integration/Skript/config. Do not combine Core with Bridge or duplicate its5bundled modules. The source/default asset pack is unchanged; the server sends authoritative form state. Full original plugin parity, all classes, upstreamSkript/Mythic, comprehensive PvP/permission and load-profile acceptance remain incomplete.
