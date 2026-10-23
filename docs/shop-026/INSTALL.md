# Install Fantasy Shop 0.2.6

Minecraft Fabric 1.21.1, Java 21, Cobblemon 1.8.2. This is a source-built update to the canonical 0.2.5 release.

1. Stop the client and server, back up player data and configuration.
2. Replace the old client JAR with `client/FantasyHubClient-0.2.6.jar`.
3. Replace the old FantasyHubCore/FantasyHubBridge server JAR with `server/FantasyHubBridge-0.2.6.jar`. Install only one server JAR for the `fantasyhubcore` mod ID.
4. Keep the existing SVFrameLib, SVFrameMMO, SVFrameItems, WorldBoss and BEconomy modules/configuration. Do not reset ownership, balances, receipts or mailbox files.
5. Start both sides. The bundled protocol and `heroes-commerce-v10` asset handshake require the matched 0.2.6 pair.
6. Open Fantasy HUD → Shop or use `/fantasyshop`.

The Bridge update is required because each bundle now transmits complete registered ItemStacks instead of server-resolved name strings. Protocol is bundled; do not install another standalone copy.

Language assets are embedded in the client and supplied separately for inspection. Existing `commerce.json` prices, item IDs, rewards, purchase limits and currency bindings remain unchanged. No player-data migration or new currency database is introduced.

Build from the supplied source checkout with Java 21:

```
./FantasyHub/gradlew -p FantasyHub --no-daemon --max-workers=2 build
```

For a clean checkout, first build the native dependencies with `python3 FantasyHub/tools/build_master_release.py` (Java 21 in `JAVA_HOME`). That checked-in helper builds the source modules in dependency order. The source ZIP includes the complete canonical modules and authored resources; no binary hotfix baseline is required.
