# FantasyHub 0.3.0 performance release

Minecraft Fabric 1.21.1, Java 21, Loader 0.18.4+, Cobblemon 1.8.2.

1. Back up the server world, player data and configuration. Stop the server/client.
2. Replace the existing **FantasyHubBridge/Core** with `FantasyHubBridge-0.3.0.jar` on the server. Keep one Bridge/Core JAR only.
3. Replace `FantasyHubClient` with `FantasyHubClient-0.3.0.jar` on every client. The client and Bridge embed the same **FantasyHubProtocol 0.3.0, protocol 4**. Do not install an additional standalone protocol JAR.
4. Keep the existing compatible SVFrameLib, SVFrameMMO, SVFrameItems, SVFrameMobs, Cobblemon Integration, WorldBoss and BEconomy. No replacement class/economy system is introduced.
5. Start and check the version handshake, then open F8 → Fantasy HUD, Skills, Shop and Upgrades. Old protocol-3 clients must upgrade; a mixed installation is rejected explicitly.
6. `config/FantasyHub/sync.json` is created on first start. Its worker/queue/budget settings are applied at server startup. Keep the defaults initially. After changing native class names/icons/definitions, also run `/fantasyshop reload` to refresh the shop presentation cache. `/fantasyshop reload` invalidates catalog snapshots without altering configured prices.
7. Use `/fantasyhubperf reset`, reproduce activity, then `/fantasyhubperf`. Preserve the output with player count and the sampling window.

Player UUIDs, ownership, skill progression, balances, mailbox, cooldown journals and transaction formats are unchanged. Sync caches are transient; no player-data migration or currency conversion is required. Preview references are presentation data, not new item registry IDs.

Cooldown bypass remains BeastCoin only. Existing owned-class switches remain free after cooldown. Purchases still validate current provider balances and server prices, using the existing durable transaction implementation.

The **QA Load Probe is a test tool, not a production dependency**. Never put it in a production installation. It deliberately requires `SVFRAME_RUNTIME_QA=1` and records test tick/allocation data.

Build from the source bundle with Java 21:

```sh
python3 FantasyHub/tools/build_master_release.py --root .
```

For an existing compatible framework checkout with dependency JARs already built:

```sh
bash FantasyHub/gradlew --no-daemon --max-workers=2 -p FantasyHub build
```

The source baseline is canonical commit `9dd9b12`, following source release `d21ae77` (0.2.9). Previous ASM/binary hotfixes are not used as the implementation baseline.
