# Proxy handshake hotfix 0.1.2

The login-query handshake in 0.1.1 worked for direct Fabric connections but rejected an installed client through Velocity. This was reproduced with the same valid client JAR and 235 verified assets. The server treated all failures as a missing client, making the message misleading.

The handshake now uses a Fabric configuration task and configuration custom payloads. It verifies the client, protocol, and packaged assets before allowing entry to the world. The channel check runs in BEFORE_CONFIGURE so missing clients receive a useful error before registry synchronization. Client resource lookup uses its own mod container. Errors distinguish an unregistered channel, a failed asset/protocol check (including the actual failing file), and an unsolicited reply. Verification was not removed.

A separate QA failure showed the periodic NativeStatEngine cleanup reading MAX_HEALTH through a handler requiring an online player after disconnection. Registered handlers are now excluded before evaluating their native base attributes. An isolated library build contains only this fix, with pending Skript/API changes excluded.

Plain Velocity also failed to decode Cobblemon custom command arguments: `AvailableCommandsPacket`, `Argument type identifier 58 unknown`. The package therefore supplies the original MIT-licensed CrossStitch 0.1.6 server-only mod as a separate proxy support dependency. Do not install a duplicate if an appropriate CrossStitch version is already present. No production authentication/forwarding settings are changed. Proxy tests use loopback-only offline fixtures; those settings are not deployment instructions.

Local Gradle/Java 21 validation: 11 client tests and 26 library tests pass, including bad/missing assets, stale revision, F-state/catalog ordering, native key mappings, and offline native-stat cleanup. Core/client and separate bridge builds passed locally. The F HUD fixes from 0.1.1 remain present.

Runtime evidence and a check matrix are supplied in QA/verification.json. Negative tests confirmed a client without the presentation mod is rejected with FH_CHANNEL_MISSING and an intentionally stale asset revision is rejected with FH_VERIFY_FAILED before gameplay. Test clients with corrupted assets are not distributed.

Installation: replace the player's FantasyHubClient with 0.1.2 and the Fabric backend's Core OR Bridge with 0.1.2. For separate mods, also replace SVFrameLib with the included patched 1.7.2-fabric JAR; bundled Core already contains it. Keep existing config, character data, Skript, and other framework mods. The shared Protocol 0.1.2 is nested in all three hub artifacts. Neither the proxy nor the Fabric backend needs FantasyHubClient.

This is a focused connection/HUD and stat-cleanup hotfix, not certification that the broader RPG/Skript/class/boss migration is complete. Proxy plugins that consume or drop these configuration channels and production player-forwarding setups need their own deployment verification.
