# Local Rider builds

Builds and tests run on the development machine. GitHub Actions is not part of this workflow.

Requirements: Java 21, Gradle 8.8 (the FantasyHub wrapper pins it), the existing canonical SVFrameLib/SVFrameMMO projects, the compatible SVFrameItems JAR, and the authored/imported resources already used by FantasyHub. This does not fetch private reference photos or replace missing assets with placeholders.

From the repository root:

```bash
JAVA_HOME=/path/to/jdk-21 FantasyHub/tools/build_local.sh
```

For an installed Gradle and populated offline cache:

```bash
JAVA_HOME=/path/to/jdk-21 \
GRADLE_BIN=/path/to/gradle-8.8/bin/gradle \
RIDER_BUILD_OFFLINE=1 \
FantasyHub/tools/build_local.sh
```

`RIDER_BUILD_OUTPUT` selects the output folder; the default is `artifacts/local-riders`. `RIDER_BUILD_INIT_SCRIPT` optionally supplies an existing local repository/cache configuration. The script builds the canonical Lib and MMO locally, runs MMO nativeCoreSmoke, builds/tests Bridge and client, copies JUnit reports, and writes a SHA-256 build manifest. It fails on build/test errors and refuses to package a framework-bundled Core as Bridge.

Install the resulting **FantasyHubBridge**, **SVFrameLib** and **SVFrameMMO** on the dedicated server with the compatible existing SVFrameItems/SVFrameMobs/Cobblemon integration. Replace the Lib/MMO JARs as well: this expansion uses their shared defense, profile and recast extensions, even where their base version number matches an older installation. Install **FantasyHubClient** on each client. Do not install Bridge together with a framework-bundled FantasyHubCore. The matching client/Bridge include their Protocol JAR and authored presentation assets. Keep existing server configuration and player data.

A successful build is a build result. Native gameplay, multiplayer, persistence and visual checks have separate evidence and acceptance records. Candidate JARs must not be presented as fully accepted solely because this script succeeded.
