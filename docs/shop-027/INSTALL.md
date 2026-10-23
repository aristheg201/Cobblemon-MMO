# Install FantasyHub 0.2.7

Fabric Minecraft 1.21.1, Java 21, Cobblemon 1.8.2; use the existing SVFrame and BEconomy modules.

1. Stop the client/server and back up configuration/player data.
2. Replace the old FantasyHubClient JAR with `client/FantasyHubClient-0.2.7.jar`.
3. Replace the old FantasyHubCore/FantasyHubBridge server JAR with `server/FantasyHubBridge-0.2.7.jar`. Keep exactly one server JAR with mod ID `fantasyhubcore`.
4. Start both sides. Client/server versions must match. The protocol is bundled; do not add another protocol or Shop mod.
5. Open F8 → Fantasy Shop → BUNDLE. Scroll the equipment panel to select/preview belts and weapons.

Existing canonical v1 Rider bundle definitions migrate automatically, with a backup at `config/FantasyHub/commerce.json.pre-v2.bak`. Do not overwrite the live catalog with the reference file: that could replace customized prices. Kabuto is also restored to the canonical Rider class roster, with a separate `class-switch.before-kabuto-roster.yml` backup. Intentionally restricted class policies remain under administrator control. Custom loadouts remain under administrator control; the included `config-reference/commerce.json` shows the complete canonical sets.

The migration changes future bundle contents. It does not charge players, activate a class or retroactively replace items from earlier purchases.

The six Rider classes use existing SVFrameItems definitions. Buying equipment never assigns the player's class. Previously unlocked skills/forms retain their normal progression requirements.

Rebuild from the complete source bundle with Java 21 (`JAVA_HOME`):

```
python3 FantasyHub/tools/build_master_release.py
```

For a checkout where native dependencies are already built:

```
./FantasyHub/gradlew -p FantasyHub --no-daemon --max-workers=2 build
```
