# Install / migrate

Stop the server and client, back up the world/config/player databases and replace old FantasyHub Bridge/Core and Client JARs. Install the paired FantasyHubBridge 0.3.1 and FantasyHubClient 0.3.1, plus the supplied SVFrameMMO Cobblemon Integration 0.1.21 wherever that integration is installed. Do not retain duplicate old JARs.

Protocol 5 is embedded identically in Bridge and Client. Do not install a separate protocol JAR. Earlier clients must be updated; the existing explicit version handshake rejects incompatible releases. Core rejects Cobblemon Integration versions below 0.1.21 when installed, because they lack bounded catalog capture.

Keep the unchanged compatible foundations: SVFrameLib 1.7.2-fabric, SVFrameMMO 1.13.4-fabric, SVFrameItems 0.1.3-fabric, SVFrameMobs 0.1.2-fabric and SVFrameWorldBoss 0.2.0. The release does not replace these with alternative engines.

Existing player, ownership, transaction, cooldown, mailbox, shop, arena and Infinity persistence formats are preserved. Do not delete userdata or commerce/WorldBoss configuration. Back up and retain pending transaction journals; never discard them to clear a purchase error.

`config/FantasyHub/sync.json` remains compatible; absent new fields receive their safe defaults. Review the supplied reference file. Default worker count remains two. `catalogPageSize` bounds each Pokémon capture; do not compensate for overload by increasing queues or workers indiscriminately.

After changing native class descriptions/names/icons, run the existing native reload and `/fantasyshop reload`. After changing the Pokémon move adapter/configuration, its revision invalidates the active catalog stream. Ordinary shop purchases and class/equipment mutations invalidate their relevant views automatically.

Validate on a staging copy first using the release QA scripts. `/fantasyhubperf reset` followed by `/fantasyhubperf` reports the candidate's own timing. The separate load-probe JAR requires `SVFRAME_RUNTIME_QA=1` and is test-only; do not put it in production.

Reproduce the complete source build with Java 21:

```sh
python3 FantasyHub/tools/build_master_release.py --root .
```

Pinned Gradle wrappers are included. `.github/workflows/fantasyhub-performance.yml` performs the same source build and native smoke checks using pinned unchanged Items/Mobs source commits. Runtime load/graphical tests require the documented disposable Minecraft fixture and are distinct from CI compilation.
