# FantasyHub 0.2.8 installation

Fabric Minecraft 1.21.1 / Java 21 / Cobblemon 1.8.2. This is a source-built update of the existing FantasyHub modules.

1. Stop the server and client. Back up player data and the world.
2. Replace the old FantasyHubCore/Bridge server JAR with `server/FantasyHubBridge-0.2.8.jar`. Keep one `fantasyhubcore` mod only.
3. Replace FantasyHubClient with `client/FantasyHubClient-0.2.8.jar`. Both sides must match.
4. Keep SVFrameLib, SVFrameMMO, SVFrameItems, BEconomy and existing configurations. No standalone Shop mod or separate Protocol JAR is needed.
5. F8 opens the full Fantasy HUD. Attributes and Skills remain top-level HUD tabs; open Shop from Profile. Back/Escape in Shop returns to its originating HUD/class screen.

BeastCoin resolves to BEconomy's registered `beastcoin` ID. Existing installations that actually register their BeastCoin wallet as `secondary` remain supported. If both exist, `beastcoin` is authoritative; balances are never transferred, merged or selected according to available funds. HunterCoin continues to use BEconomy's configured primary currency. Missing BEconomy/wallets reject transactions. Cooldown bypass remains BeastCoin only.

Mailbox data now lives in `<world>/fantasyhub-mail/<player UUID>/<SHA-256>.json`; the canonical MMO preference contains a short `mail:v1:` reference. Back up this directory together with MMO player data and `<world>/fantasyhub-transactions`. Retain old blobs for transaction recovery. Do not delete referenced blobs or copy preferences without their mailbox directory. Legacy inline mailbox entries migrate on the next successful mailbox update. A missing/corrupt blob blocks item purchases without charging; restore it from backup rather than resetting the player's preference.

This fixes compatibility with the older SVFrameMMO 1.13.4 preference limit (160 characters), without requiring another MMO/Lib update. Previously rejected purchases that were refunded have no new equipment entitlement; a new purchase is required. Do not repeat purchases with an unresolved transaction journal: reconcile those through the existing administrator workflow first.

Existing pricing, custom catalogs, Rider belts/weapons, ownership, progression and skill requirements are retained. The reference catalog is for review; do not overwrite customized live prices.

Build with Java 21:

```
python3 FantasyHub/tools/build_master_release.py
```

With the existing native dependency artifacts already built:

```
./FantasyHub/gradlew -p FantasyHub --no-daemon --max-workers=2 build
```

## HUD equipment upgrades

Profile → Equipment Upgrade lists actual SVFrameItems equipment in the player's inventory, including armor slots. Select a piece to see its native upgrade template, current/max level, success chance, destruction risk and configured costs. Each attempt consumes one existing `fantasyhub:rpg_upgrade_catalyst`, in addition to the native template's costs. Previewing/confirmation does not consume anything. Equipment without a configured upgrade template remains visible with an explanation; this update does not invent a template or alter equipment balance.

The server binds confirmations to the exact item/slot/template and accepts each quote once. Item changes, rule changes, expiry and duplicate requests cancel the old confirmation. Server-side SVFrameItems validates costs and rolls the result. A failed roll may consume costs or destroy the item exactly as displayed by its native template.

Back up `<world>/fantasyhub-upgrades` together with player data. A record remaining after an uncertain attempt locks further upgrades for administrator reconciliation and includes the inventory snapshot and template. Do not automatically replay the attempt or delete an unresolved record. Committed records matching the player's `fantasyhub:last-item-upgrade` receipt clear safely on the next view. Native third-party cost providers retain their own persistence/recovery responsibilities.

## Flash movement

Water walking and one-block auto-step are active-class passives, independent of armor, skill points, speed stacks and mana/stamina costs. Water collision is predicted on the client and checked against the active class on the server. Sneak or swim to dive; submerged players retain normal swimming. The passive does not solidify terrain, freeze water or grant lava walking. The temporary step-height modifier is removed on class exit, disconnect, death, world change and server stop.
