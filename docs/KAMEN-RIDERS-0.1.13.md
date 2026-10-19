# Kamen Riders 0.1.13 — armor reference correction

The rejected 0.1.12 Ryuki and Decade armor is rebuilt from the five latest user references. Accepted skill balance and all 21 active implementations are retained. This is an armor update to the existing SVFrame expansion, built locally with Java 21 / Gradle 8.8 for Minecraft 1.21.1 Fabric. No GitHub Actions.

## Models and equipment

| Asset | Correction |
| --- | --- |
| Ryuki normal | Red suit, gloves and legs; black shoulder frames; paired silver chest plates; silver V-shaped cage over full red lenses; sealed black helmet with side rails and vented jaw. |
| Ryuki V-Buckle | Separate silver housings/hinges, release switches, black Card Deck with original pixel-painted forked gold dragon crest. |
| Ryuki Survive | Separate automatic wearable model: black thighs, crimson/gold dragon cuirass, pointed shoulders, gold vambraces/greaves, silver abdominal ribs, red deck. Helmet has thin gold antennae and swept gold crown around both temples to rear fins. |
| Decade | Stepped green lenses, seven integral barcode fins, individual silver mouth teeth, magenta plates, white/black chest frame, white rectangular knee/shin panels. |
| Decadriver | White receiver/locks, physical icon frame, nine distinct small pixel Rider glyphs, red center and magenta switches. |

The editable source contains **44 Blockbench projects**, including three new Survive body/helmet/belt projects. There are 60 material tiles in the original 512px atlas. The first 41 existing tiles are preserved; 19 pixel materials are appended. Forty armor icons cover ten wearable forms; SVFrameItems defaults provide 55 item definitions. Other existing Rider/creature geometry and weapon grips are preserved.

Latest reference provenance and observed details: `FantasyHub/assets-source/riders/reference-to-asset-manifest.json`. Original user photographs remain private and are not published to the repository. No photograph or generated illustration is used as wearable armor.

The latest user request supersedes the initial keep-base-Ryuki-Survive appearance: switching Survive now routes `ryuki` → `ryuki_survive` automatically. Switching it off restores `ryuki`. Knight behavior is retained. Decade Complete always uses `decade`; no borrowed Rider becomes a wearable Decade model. Inventory items are not overwritten during these visual changes.

## Build and verification

Local JUnit: **98 tests**, zero failures/errors (Core 45, Client 23, SVFrameLib 30), plus existing SVFrameMMO 72 skill-bar mapping cases and preference codec/native-core smoke checks. Model identity, routing, unchanged Decade identity and the final merged resource index/model manifest are covered. Exact commit, JAR hashes, test XML and build logs are in the delivered `build-manifest.json` / `verified-local-build`.

Native evidence and assessment: [armor review](qa/armor-reference-v13/REVIEW.md). Blockbench previews are labeled separately from native Minecraft screenshots. Earlier 0.1.12 full 21-skill/multiplayer/persistence evidence remains in its existing delivery; this update reruns focused armor/form and affected summon checks. Unit tests do not measure visual fidelity.

Two packaging regressions were caught before delivery: stale checksums and a truncated Rider model preload list. Build now derives both from the final merged resources; summon models remain loaded after targeted armor exports. Original failure reports are retained, separate from final native evidence.

## Deployment

1. Stop server and clients. Replace the old **FantasyHubBridge** with `FantasyHubBridge-0.1.13.jar` on the server, and **FantasyHubClient** with `FantasyHubClient-0.1.13.jar` on every client. Keep one version of each mod.
2. Keep the exact SVFrameLib/MMO and compatible dependency set supplied in the 0.1.12 release. Their hashes are unchanged and included in the manifest. Do not install bundled FantasyHubCore alongside Bridge.
3. Preserve worlds, player data and custom configuration. Bundled migration adds missing Rider defaults while keeping custom values. The four optional physical Survive armor definitions are included; skill transformation does not require manually switching equipment.
4. Assets are inside the client JAR. No separate Rider resource pack is needed. F toggles Henshin/skill bar. Use the seven existing bound skill keys; Survive/Complete are indefinite toggles.

Local rebuild: `JAVA_HOME=/path/to/jdk21 GRADLE_BIN=/path/to/gradle8.8/bin/gradle FantasyHub/tools/build_local.sh`. The private source bundle includes legacy imported inputs and unchanged Items/Mobs module sources; Maven/Loom dependencies require network access or an existing cache. Authoring uses actual Blockbench 5.2.2 through `run_blockbench_authoring.py`.

Visual limitations: voxel adaptation necessarily changes curved outlines; small belt glyphs are original pixel interpretations. Automated checks and screenshots cannot certify the user's requested “100%” likeness. This delivery provides the rendered assets for direct review, without claiming aesthetic approval on the user's behalf. Existing sounds/skills are retained; this correction does not introduce new film audio.
