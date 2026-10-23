# FantasyHub 0.2.8 validation

Target: Fabric Minecraft 1.21.1, Loader 0.18.4, Java 21, Cobblemon 1.8.2. Dedicated server and graphical Minecraft client on disposable copies of the existing SVFrame runtime; the production server was not modified.

## Automated

`./FantasyHub/gradlew -p FantasyHub --no-daemon --max-workers=2 build`

256 tests: 250 passed, 6 existing Skript tests skipped, no failures/errors. Client 57, core 126, WorldBoss 34, Skript 39 (including the six skips). The final incremental build also passed. Unchanged module checks correctly used Gradle's existing results.

New checks cover large mailbox persistence with a legacy-compatible short reference, immutable rollback references, inline-mail migration, missing/corrupt/cross-player references, actual/legacy BeastCoin IDs, and single-use/player-bound/stale/expired upgrade confirmations. The original mailbox stub failed all four tests before implementation; new upgrade confirmation tests initially failed compilation before their implementation existed. Both red logs and final build output are included.

## Reproduced bugs

- The previous Bridge rejects the Kabuto bundle with `IllegalArgumentException: Invalid player preference` against the older 160-character-limit MMO and refunds the debit.
- Registering BeastCoin under `beastcoin` rather than literal `secondary` reproduces the grey bypass button. `before-grey-bypass.png` and `legacy-beastcoin-unavailable.json` record this state.
- `runtime-compatibility.json` identifies the old MMO JAR used for native validation. It does not contain the expanded `PreferenceLimits` implementation. No MMO/Lib replacement is required by this fix.

## Native transactions and GUI

- Six Rider bundles delivered 41 actual items, including authored belts/weapons. Agito tested as an unowned class + equipment purchase; Kabuto tested using HunterCoin; the other bundles used BeastCoin. Class identity remained Batman throughout purchases.
- Duplicate purchase tokens produced one grant/debit. Insufficient BeastCoin did not fall back to HunterCoin. An intentional mailbox storage failure rejected the purchase before debit.
- Full inventory retained all 41 items in mail. Restart preserved them; subsequent claims delivered exactly 36 + 5 unique delivery IDs. Repeated claims did not duplicate items.
- Actual GUI bypass changed Batman → Thor and charged exactly 500 BeastCoin; HunterCoin stayed unchanged. Free switching and bypass requests after cooldown incurred zero fees. An insufficient-BeastCoin bypass was rejected despite a positive HunterCoin balance.
- F8 opened the full HUD, with Attributes and Skills accessible. Shop and Equipment Upgrade were nested HUD screens; Back/Escape returned to the parent HUD.
- Native GUI upgrade showed real items/chance/risk, required a second confirmation, and changed a Trailblazer Blade from +0 to +1. Preview/first confirmation spent nothing; the completed attempt consumed one catalyst and no unconfigured currency cost.
- Missing catalyst was rejected. A duplicate native upgrade request consumed only one catalyst; the attempted +1 upgrade rolled a normal failure and retained the item, as displayed by the native template.
- Mana allocated from 49 to 50 through the HUD; Stamina reached 50. GUI and server rejected additional allocations without consuming attribute points. Skills remained present.
- English/Vietnamese native screens and GUI scales 2/3 were inspected; smaller-window captures cover the scrollable detail area, pagination, pinned controls and native tooltips. These are Minecraft captures, not mockups.

## Native Flash movement

Without activating a skill, wearing a suit or consuming resources, Flash traversed the water fixture above Y=70.80. Sneaking allowed normal diving. Actual walking crossed a one-block obstacle; the native step-height attribute was 1.0. Switching to Thor restored 0.6 and normal water collision. Flowing-water support was also observed at Y=71.77777779102325 on the flowing-water fixture. Command/position samples and screenshots are included.

## Limits

Upgrade availability, costs, chance, maximum level and destruction remain controlled by existing SVFrameItems templates. Equipment without a template is explicitly disabled with a message; this release does not invent upgrade balance for those definitions. Third-party cost-provider failure semantics remain their responsibility; uncertain upgrade attempts retain an inventory snapshot and block replay for administrator reconciliation.

Existing external-auth/update-network warnings remain visible in the offline QA logs. No production data, destructive SNAP scenarios, unrelated WorldBoss gameplay or full-server performance benchmark was tested for this scoped release. GUI screenshots captured before the final title/scrollbar polish are retained as intermediate evidence; final screenshots are named `final-*`.

The final restart also preserved the upgraded Blade, two remaining catalysts, Mana/Stamina allocations, class identity, wallets and class-switch deadline. No unresolved upgrade journal remained. Native English GUI scale 3 captures were taken after the final preview-title/scrollbar changes; small-window captures use the actual 640×480 Minecraft window.
