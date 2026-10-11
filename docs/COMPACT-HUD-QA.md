# Vanilla-position RPG HUD

The resource panel has been replaced with 81-pixel bars anchored to the vanilla status area. Actual client health and maximum health render at the old heart position; authoritative stamina renders immediately above health, mana above food, and RPG level and experience replace the vanilla level and XP presentation. Armor moves above stamina, and oxygen moves above mana. Food, hotbar, ability slots, and gameplay attributes remain functional. Component visibility restores vanilla hearts when custom health is disabled. The distinct magic resource uses a small charge jewel between the two columns.

Locally built with Java 21, Gradle 8.8, Minecraft 1.21.1, and Fabric Loader 0.18.4. Ran the built presentation mod on two real clients connected to a dedicated Fabric/Cobblemon 1.8.0 server. The second client installs the presentation mod without server gameplay modules.

- `qa/compact-hud/hud-en-scale3.png`: English, GUI scale 3, level 30 and actual maximum health 137.35.
- `qa/compact-hud/hud-vi-scale2.png`: Vietnamese, GUI scale 2, same resource positions, with Cobblemon enabled.

Both images were captured directly with X11/FFmpeg. These changes and captures verify this HUD adjustment; they do not certify the unfinished complete framework or source compatibility suite.
