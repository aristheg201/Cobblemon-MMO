# Fantasy Hub (Fabric 1.21.1)

This is an implementation candidate, not a production-complete migration of the supplied plugin pack. The audit discovers 36 classes and 28 enabled Skript scripts. The current runtime does not implement their full behavior. Read the migration and QA reports before deploying.

The Core bridge uses SVFrameMMO/SVFrameLib as the single owner of stats, resources, progression, cooldowns and loadouts. The Client provides packaged assets, a HUD, character/attribute/skill/settings screens, and a separate Cobblemon player-skill shop. Requests are validated by server APIs; client health observations exist only in the explicitly enabled QA mode.

Requires Minecraft 1.21.1, Java 21, Fabric Loader >=0.18.4, Fabric API 0.116.9+1.21.1 and Cobblemon 1.8.x. The integration's existing runtime dependencies (Fabric Language Kotlin, Polymer, Architectury, Accessories and owo) remain required. Economy configuration is inherited from the integration; no exchange rate is introduced.

## Local build

Build the existing Lib/MMO/Items/Integration/Mobs projects on their authorized branches. Supply the resulting Lib and MMO JARs to the sibling projects' existing `build/libs` dependency paths. No GitHub Actions build is necessary.

Convert the locally supplied source assets before building this project:

```sh
python3 tools/package_reference_assets.py --references /path/to/audited/user-packs
./gradlew build
```

The asset tool uses the source archives extracted by `SVFrameLib/tools/audit_fantasy_hub.py`, including the `Extras` rupture variant containing the required `skill2` animation. Vendor inputs and generated assets are intentionally excluded from Git. A client built without those inputs cannot satisfy the presentation handshake.

To produce a single server JAR with the five framework mods nested, place their production JARs in a local directory and run:

```sh
./gradlew build -PframeworkBundle=/absolute/path/to/framework-jars
```

Expected artifacts: `core/build/libs/FantasyHubCore-0.1.0.jar` and `client/build/libs/FantasyHubClient-0.1.0.jar`. Core and Client each nest the versioned common protocol. Install the server bundle plus external dependencies on the server; install Client plus Cobblemon/Fabric dependencies on clients. The client JAR supplies resources automatically. Do not also install a conflicting old framework version.

## Controls

F8 opens the character menu. Skill keys are configurable through Minecraft Controls and intentionally start unbound. `/fantasyhub attributes`, `/fantasyhub skills` and `/fantasyhub shop` open the respective client screens. Purchases grant persistent player abilities, not Pokémon move changes. Server validation governs allocations, respecs, upgrades, binding and casting.

Client settings are stored atomically in `config/fantasyhub-client.json`. The default HUD uses four distinct authoritative resources (health, mana, stamina and MMO stellium presented as configurable magic). It does not alter health packets. The presentation carrier has its own item namespace and does not replace Minecraft's paper models.

The local asset pipeline also packages the supplied DK and AMK class configurations. Core installs them only when absent, reloads definitions before saved characters attach, and preserves existing server edits. This makes the candidate's nine class definitions available on a fresh installation; it does not establish full behavioral compatibility for the 36 source classes.

`/fantasyhub inspect <player>` is an administrator-only inspection of the actual server snapshot, catalog, allowed slots, attributes and loadout. The skill catalog includes declared current-class skills and explicitly granted/owned skills. Class slot category formulas are enforced by the server; passive slots can equip their configured passives, which execute through the existing native trigger engine.

For reproducible native runtime checks, set `FANTASYHUB_QA_RCON_PASSWORD` locally and run:

```sh
python3 tools/run_runtime_qa.py --server /path/to/qa-server --player QAPlayer --output /path/to/evidence
```

Use `--catalog` with a high-level DK account that has purchased Cut to verify class filtering, active/passive slot restrictions and automatic TIMER execution. `--shop` makes real purchases against the configured provider and needs the funded account and arena described in the QA report. The harness copies actual mod-generated results and fails on missing/incomplete checks.

## Verification and limits

`SVFRAME_RUNTIME_QA=1` enables administrator-only lifecycle and Cobblemon tests plus actual client damage-packet/hurt-camera observations. Do not enable this on a public production server.

Only 15 explicit single-hit/half-heal move adapters are currently offered for purchase. The complete move registry remains available to fusion and existing saves; registration is not a claim of behavioral coverage. Economy/provider crash recovery, exhaustive class/boss/asset migration, the supplied Skript language/addons and the complete requested UI suite remain unfinished.
