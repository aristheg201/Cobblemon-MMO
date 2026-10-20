# Client HUD resource packs (private distribution)

The FantasyHub client includes two selectable built-in resource packs. They are
enabled by default on first discovery and remain independently configurable in
Minecraft's Resource Packs screen. The client does not overwrite a player's
Xaero configuration, radar categories or pack ordering preferences.

| Built-in pack | Pinned source | Integration |
| --- | --- | --- |
| SVFrame · Detailed Animations | Cymock, Detailed Animations Reworked V1.15, Modrinth `dFBidLNv` | Player, slim player, cape and elytra EMF animations |
| SVFrame · E19 Cobblemon Minimap Icons | E19 1.21.1-1.5.2, Modrinth `MAumPyhV` | Xaero Pokémon icon definitions and sprite textures |

Sources:

- https://modrinth.com/resourcepack/detailed-animations/version/dFBidLNv
- https://modrinth.com/resourcepack/e19_cobblemon_minimap_icons/version/MAumPyhV

Install the client JAR in the existing Fabric 1.21.1 client's `mods` directory.
Xaero is already supplied by the server's client pack: it is **not embedded,
declared as a dependency, downloaded or installed by FantasyHub**. E19 is inert
when Xaero is absent. Enable entity icons in Xaero's radar settings if that
client profile currently displays dots instead of icons.

Detailed Animations requires the separate client mods Entity Model Features
(EMF) and Entity Texture Features (ETF). Local QA uses EMF 3.3.12 and ETF 7.2.6
for Fabric 1.21/1.21.1. They are not nested inside FantasyHub. A resource pack
alone cannot animate players without its model loader. Do not enable competing
player animation packs at the same time without checking their priority.

## Compatibility adaptations

E19's archive also contains legacy Cobblemon models and resolvers. These are
deliberately not installed: an unmodified import fails on Cobblemon 1.8.1 with
`Unable to load model cobblemon:pikachu_male_female.geo`. The minimap namespace
contains mappings for current numbered texture paths as well as legacy paths;
the original icon artwork is retained. Pokémon geometry stays owned by the
installed Cobblemon/addon resources.

Detailed Animations V1.15 treats several booleans as numbers. The importer uses
explicit boolean casts for EMF 3's typed animation compiler and guards three
velocity/sprint denominators against zero. This prevents non-finite player
transforms during repeated rendering of the same frame. Locomotion, jumping,
climbing, flight, cape and elytra animation equations are otherwise preserved.

## Reproducible local import

```sh
python3 FantasyHub/tools/import_client_hud_packs.py --archives /path/to/archives
```

The importer pins archive SHA-512 values, validates paths and decompression
bounds, and records source versions and adaptations in
`assets/fantasyhub/client-hud-packs.json`. Imported third-party files stay in the
ignored private resources directory. The local build packages them in the
client JAR and includes every file in the SHA-256 asset index; startup verifies
the index, including both built-in pack trees.

Detailed Animations is All Rights Reserved; E19 is MPL-2.0 and credits
MBCMechachu and its contributors. Retain original credits. This integration is
for the requested internal distribution; no assets or compiled client have
been published to GitHub or GitHub Actions.
