# FantasyHub 0.2.7 — complete Rider bundles

The shop is part of Fantasy HUD (F8 → Fantasy Shop). No separate Shop mod was added. The release updates the existing FantasyHubClient/FantasyHubBridge pair.

Correct the six shipped Kamen Rider bundles to include their registered RPG belts and authored weapons, replacing the generic Trailblazer Blade:

| Class | Belt | Weapons |
|---|---|---|
| Agito | Alter Ring | Flame Saber, Storm Halberd, Shining Caliber |
| Kabuto | Zecter Belt | Kunai Gun |
| Decade | Decadriver | Ride Booker Sword, Ride Booker Gun |
| Ryuki | V-Buckle / Card Deck | Drag Saber |
| Knight | V-Buckle / Card Deck | Dark Visor, Dark Blade |
| Zolda | V-Buckle / Card Deck | Magna Visor, Giga Launcher |

Each bundle retains its four existing armor pieces. Full-class and owned-class equipment-only offers use the same corrected equipment definitions. Prices, payment methods, class ownership and activation rules are unchanged.

An idempotent catalog migration repairs only the exact old shipped loadouts. It saves `commerce.json.pre-v2.bak` before writing catalog version 2. Custom equipment definitions and all pricing fields are preserved. No competing item registry or weapon system is introduced.

Repair the shipped v1 Rider class-policy omission so Kabuto appears in CLASS/BUNDLE. The policy migration backs up `class-switch.before-kabuto-roster.yml`; deliberately restricted administrator rosters remain unchanged.

Complete the existing English/Vietnamese tooltip labels for attack damage and mana regeneration, including Kunai Gun/Zecter Belt.
