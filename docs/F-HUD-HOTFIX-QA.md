# F HUD hotfix 0.1.1 — 2026-10-11

Pressing the vanilla Swap Item With Offhand binding (default F) changes the native SVFrameMMO casting mode. The client skill HUD now receives that authoritative mode, bindings, resource values, and cooldowns through a small `fantasyhub:casting_v1` packet. It no longer depends on a full GUI/shop catalog arrival. A later catalog snapshot cannot overwrite the latest acknowledged casting state. Session state clears on disconnect.

The prior build also hid the skill row whenever any screen was open, including chat. That disappearance was reproduced in a real client while the server still reported active SKILL_BAR mode with two bindings. Chat now retains the row; other menus retain their existing behavior. The screenshot provided by the user does not by itself prove which of these paths occurred on their installation.

## Validation

Local Java 21 / Gradle 8.8 builds: `:client:build :core:build` with the five unchanged baseline framework JARs bundled, then a separate bridge build without the bundle. No GitHub Actions build was used. Seven client unit tests pass, including catalog-before/after casting delivery, session reset, and native compressed key mappings.

Actual Minecraft 1.21.1, Fabric Loader 0.18.4, Cobblemon 1.8.0 client connected to a continuously running dedicated server:

| Check | Result | Evidence |
| --- | --- | --- |
| Correct hotfix and registered casting channel | PASS | `/fantasyhub hudstatus`: build `0.1.1-f-hud-hotfix`, channel true |
| F opens native casting HUD | PASS | Two drawn slots, Lick/Bite; `04-hotfix-f-enabled.png` |
| Opening chat keeps slots drawn | PASS | Two drawn slots; `05-hotfix-chat-bar-visible.png`, client observation |
| Second F hides row and disables native casting | PASS | Zero drawn slots; `06-hotfix-second-f-off.png`, observation |
| HUD key casts server-authoritative Bite | PASS | Controlled cow health 10.0 → 3.1227999; server cooldown 0.888 seconds; `07-hotfix-bite-cast.png` |
| Changing held vanilla hotbar slot preserves native input mapping | PASS | Held slot 5: Lick input 1 / Bite input 2; `08-hotfix-held-slot-remapping.png` |
| Reconnect clears casting without server restart | PASS | Same server PID 79727; inactive/zero drawn slots and server/client actual max health 32 |
| F still opens HUD after reconnect | PASS | Active/two drawn slots; `10-hotfix-reconnect-f-enabled.png` |

Screenshots and video come directly from the running Minecraft client. The controlled cow and a temporary vanilla Resistance effect were used only in QA; both were cleaned up. Damage magnitude includes the existing RPG combat calculation and is not a new balance change.

## Installation

Replace FantasyHubClient 0.1.0 with **0.1.1** on the playing machine. Replace either the bundled FantasyHubCore or the separate FantasyHubBridge with **0.1.1** on the server; choose one server layout, never both. These three artifacts carry the identical nested Protocol 0.1.1 JAR. Keep existing configuration, player data, separate framework mods, and Skript. Singleplayer needs both client and the chosen server layout.

This is a focused HUD hotfix. Pending Skript/combat API changes are excluded. It does not certify the broader incomplete class, boss, Skript, GUI, or RPG migration requirements.
