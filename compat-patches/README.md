# Private compatibility patches

These narrow local patches apply only to the SHA-256-verified user-supplied CobblePass server-gui-5 and Guild Progression MASTER-SPAWN-ICCE-FIX binaries. They do not vendor the original mods' source. Original copyright and licenses remain applicable. Patched binaries are provided privately to the user; this repository contains the patch tooling and newly authored helpers.

Run with Java 21 and Gradle 8.8 libraries:

```sh
python compat-patches/build.py --cobblepass /path/to/original-cobblepass.jar --guild /path/to/original-guild.jar --contracts /path/to/contracts.json --output /path/to/output --jdk /path/to/jdk21 --gradle-lib /path/to/gradle-8.8/lib
```

The command builds both binaries, validates original input hashes, preserves unrelated entries, and executes 26 standalone regression checks against the patched GuildEngine and storage helper. `patch-build.json` records exact artifact hashes. Runtime QA is separate: `fantasyhubqa compat_quests`, `compat_mining` require the installed original dependencies, an operator, and explicit `SVFRAME_RUNTIME_QA=1`; never enable this destructive fixture runner on a production server.

Quest saves recreate the directory, force a uniquely named temporary file, replace atomically where supported, retain dirty data for retry after failure, and never let periodic persistence errors terminate the server tick. Reward claims require a durable save and fail closed. CobblePass explicitly declares its existing GooeyLibs dependency.

Guild keeps exact block IDs separate from tags. Known legacy singular ore tags map to their real plural tags, including contracts already accepted. Other filter predicates remain intact. A valid configured economy ID is preserved. Only an absent `beastcoin` display alias resolves to an existing `secondary` API currency; no exchange rates or prices are changed.
