# FantasyHub 0.2.7 — Rider bundle QA

Executed on 2026-10-23 using a copied Fabric 1.21.1 / Java 21 / Cobblemon 1.8.2 server with native SVFrame modules and BEconomy. Screenshots are captures of Minecraft running on Xvfb/Mesa.

- Full source build passed: Client 57/57 and Core/Bridge 118/118 tests passed. Across FantasyHub: 248 tests discovered, 242 passed, 6 existing Skript tests skipped, no failures/errors.
- Regression tests first reproduced incomplete Rider loadouts, the omitted Kabuto roster entry and missing weapon/belt tooltip stat translations. Final tests cover all six canonical loadouts, old-catalog migration, optional count fields, custom equipment preservation, policy migration and both locale keys.
- Native v1 commerce configuration migrated to v2 with `commerce.json.pre-v2.bak`. Prices/class tiers were unchanged; reloading again left the catalog hash unchanged.
- Native class-policy migration created `class-switch.before-kabuto-roster.yml`, added Kabuto and preserved all other policy fields. Kabuto now appears in both CLASS and BUNDLE.
- Opened Fantasy HUD with F8 and entered its Fantasy Shop button. There is no separate Shop mod: packaged mod IDs remain `fantasyhubclient` and `fantasyhubcore`, with the existing bundled protocol.
- Navigated all six Rider bundles at Vietnamese GUI Scale 2, previewing 41 real armor/belt/weapon ItemStacks. Every preview had a valid model and display name. Captures include each belt and each class's final weapon tooltip.
- Purchased Agito's full bundle through the production transaction handler, including a duplicated request: only one 4 HC charge and one copy of Alter Ring, Flame Saber, Storm Halberd and Shining Caliber. Buying the bundle preserved the active class.
- Purchased Kabuto through the native GUI: first confirmation charged nothing, final purchase charged 16,000 BC and no HC. Zecter Belt and Kunai Gun were delivered as their real registered items. The active class remained unchanged.
- The six sets use existing item definitions, models, stats and gameplay handlers. No fake item registry, new weapon engine or competing class system was added.

Native QA initially found the hidden Kabuto entry and two raw tooltip stat keys. These are documented reproduction stages, not final success evidence. The final client includes translations for attack damage and mana regeneration in English/Vietnamese. Existing offline Mojang public-key warnings and the nonfatal owo login initialization trace remain as recorded in the 0.2.6 baseline.

The 0.2.6 GUI layout tests/screenshots remain the baseline for English/Vietnamese, GUI Scale 2/3 and 640×480. This patch changes catalogs, policy migration and two language entries; it does not claim a new complete gameplay QA pass for unrelated systems or physical GPUs.

Evidence includes native catalog/policy migration results, six bundle observations, actual purchase/delivery results, JUnit XML, build logs and raw screenshots. The catalog migration does not retroactively replace items from previous purchases.
