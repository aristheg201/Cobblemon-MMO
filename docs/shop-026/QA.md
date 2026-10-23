# Fantasy Shop 0.2.6 — executed QA

2026-10-23, Fabric 1.21.1 / Java 21 / Cobblemon 1.8.2, native SVFrame modules and BEconomy. Tests used a copied QA server/player, not production data. Screenshots are raw captures of the running Minecraft client on Xvfb/Mesa, not mockups.

## Automated builds

- Complete `FantasyHub build`: successful. 243 tests discovered: 237 passed, 6 existing Skript tests skipped, 0 failures/errors.
- Affected modules: Client 57/57 and Core/Bridge 113/113 passed.
- Final client language-resource build: successful after the last Vietnamese wording correction.
- New regression coverage: measured Unicode ellipsis, small-window geometry, three-category filtering, money formatting, both catalog locales and full native ItemStack catalog compression. The enlarged-catalog test failed before the transport fix and passed afterward. Both encoded and decoded size limits remain enforced.
- Build logs and JUnit XML are included in the QA archive.

## Native GUI and server checks

- Cold dedicated-server and client starts, repeated reconnects, existing profile/ownership data loaded successfully. Opened Fantasy HUD → Shop using the actual F8 menu and shop button.
- Three simultaneous CLASS / SHOP / BUNDLE tabs, active/hover states, category selection reset and pagination within each tab.
- English at GUI Scale 3 (1280×720; 427×240 logical) and at 640×480 (320×240 logical, Minecraft clamps the requested scale to 2). Vietnamese at GUI Scales 2 and 3 (1280×720).
- Actual Agito helmet, chestplate, leggings, boots and Trailblazer Blade names/models rendered in both locales. Each row selects its larger native preview; all five item tooltips captured. Long product names expose full hover text; descriptions and equipment scroll while payment buttons remain visible.
- Navigated all 44 bundle offers in the native client: 227 ItemStack previews decoded with valid names and models. `all-bundle-client-previews.json` records each selected offer and its items.
- Native invalid-config probes: missing `fantasyhub:missing_shop_preview`, 1 HC item price and 499 BC item price were rejected/logged and excluded from purchasable offers. The original QA configuration was restored afterward.
- 13 native server commerce scenarios passed: initial free selection, both class currencies, duplicate rejection, owned free switching, cooldown rejection, BeastCoin-only bypass, expired-cooldown free switching, both item-shop currencies, insufficient balance, equipment-only pricing, inventory-full mailbox delivery/recovery and an unowned class bundle granting entitlement without activating the class.
- Actual GUI purchases verified both payment buttons: HunterCoin EXP booster deducted 2 HC and no BC; BeastCoin materials deducted 750 BC and no HC. Navigation and first-click confirmation charged nothing.
- Actual GUI owned-class switch charged nothing. Actual GUI early switch charged exactly 500 BC and no HC. The cooldown view exposes Wait and BeastCoin bypass, with no HunterCoin bypass purchase.
- No shop rendering exceptions, invalid model/translation diagnostics or final packet-encoding failures were found during these checks.

## Reproduction and existing environment notes

The initial full-component catalog exceeded the old 1 MiB JSON bound and reproduced a disconnect. The fixed native catalog was about 1.06 MB uncompressed / 42 KB compressed. Final transport permits up to 4 MiB decoded while retaining the 256 KiB wire cap. Reproduction logs are explicitly named `catalog-limit-reproduction-*`; they are not final success logs.

This offline QA environment logs refused Mojang public-key requests and a nonfatal owo initialization `NullPointerException: player` during login. The same owo trace exists in the preceding 0.2.5 baseline log; clients continue into the game and reconnect. These pre-existing third-party warnings were not represented as shop fixes. Runtime visual QA used software rendering; physical NVIDIA/AMD hardware was not available.

## Evidence

The QA ZIP includes logs, JUnit reports, native transaction results, client observations and screenshots. Principal captures:

- `final-class-vi-scale2.png`, `final-shop-vi-scale2.png`
- `final-bundle-agito-vi-scale2.png`, `final-bundle-agito-scrolled-vi-scale2.png`
- `final-agito-{helmet,chestplate,leggings,boots,weapon}-tooltip-vi.png`
- `bundle-long-name-tooltip-vi.png`
- `class-agito-en-scale3.png`, `shop-en-scale3.png`, `bundle-agito-en-scale3.png`
- `final-{class,shop,bundle-agito}-vi-scale3.png`
- `bundle-agito-lowres-640x480.png`
- `final-class-owned-ready-vi.png`, `final-class-beastcoin-bypass-vi.png`, `final-class-active-vi.png`

No new WorldBoss, Infinity, AI or hardware-specific gameplay claims are made by this scoped GUI release.
