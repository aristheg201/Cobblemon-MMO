# Installation

Minecraft Fabric 1.21.1, Java 21, Cobblemon 1.8.2. Stop the server and back up world/player data/config before replacing JARs.

Server: replace the old FantasyHubCore/FantasyHubBridge JAR with `server/FantasyHubBridge-0.2.9.jar`; it still has mod ID `fantasyhubcore`. Replace SVFrameItems with `server/SVFrameItems-0.1.3-fabric.jar`. Keep exactly one of each mod ID. Keep the existing SVFrameLib, SVFrameMMO and integration JARs.

Client: replace FantasyHubClient with `client/FantasyHubClient-0.2.9.jar`. The tested thin client does not install the server's SVFrameItems framework. If your integrated-server client already uses SVFrameItems, replace that copy with 0.1.3 too. Core explicitly requires SVFrameItems >=0.1.3 because the new native template API is needed. Protocol remains 3, assets remain heroes-commerce-v10; use the paired 0.2.9 client/server release.

Do not install a separate Shop mod. Shop and Equipment Upgrade remain nested screens of Fantasy HUD. Existing player preferences, ownership, levels, cooldowns, wallets, mail, items and explicit upgrade templates are preserved. No data reset is required.

An item without an explicit upgrade template now uses `config/SVFrameItems/upgrades.yml` → `standard`. Edit that existing template for the default chance/cost/cap/scaling. Existing explicit templates retain their own behavior. Materials/consumables without combat stats can gain upgrade levels but do not acquire invented stats. A stacked upgrade affects only one unit and needs a free inventory slot. The existing FantasyHub catalyst and any configured native costs apply.

Build from the included source with Java 21, first SVFrameLib, then SVFrameItems, SVFrameMobs, SVFrameMMO and the existing integration as their Gradle dependencies require, then `bash FantasyHub/gradlew -p FantasyHub --no-daemon --max-workers=2 build`. Use the same module versions as the manifest; no binary hotfix is used.

For production comparison: close the HUD; `/fantasyhubperf reset`; run Spark for 60 seconds with `--only-ticks-over 100 --save-to-file`; inspect `/fantasyhubperf`. Idle shopViews/previewGenerations/itemEncodes/pokemonCatalogBuilds should remain zero. Open Shop once, then switch tabs/reopen without changing inventory/config: previews should be reused. Config reload invalidates cached catalogs/previews. Counters measure this bridge only and do not promise every mod stays below 50 ms.
