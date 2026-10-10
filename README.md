# Cobblemon MMO — Fabric 1.21.1

Five native Fabric ports, targeting Minecraft **1.21.1**, Java **21**, Fabric Loader **>=0.18.4**. Builds are performed locally.

| Mod | Branch |
| --- | --- |
| SVFrameLib | [svframelib-fabric-1.21.1](https://github.com/aristheg201/Cobblemon-MMO/tree/svframelib-fabric-1.21.1) |
| SVFrameMMO | [svframemmo-fabric-1.21.1](https://github.com/aristheg201/Cobblemon-MMO/tree/svframemmo-fabric-1.21.1) |
| SVFrameItems | [svframeitems-fabric-1.21.1](https://github.com/aristheg201/Cobblemon-MMO/tree/svframeitems-fabric-1.21.1) |
| SVFrameMobs | [svframemobs-fabric-1.21.1](https://github.com/aristheg201/Cobblemon-MMO/tree/svframemobs-fabric-1.21.1) |
| SVFrameMMO: Cobblemon Integration | [svframemmo-cobblemon-integration-1.21.1](https://github.com/aristheg201/Cobblemon-MMO/tree/svframemmo-cobblemon-integration-1.21.1) |

Run `./scripts/build-local.sh` with a Java 21 JDK. This builds the modules included on the selected branch; use one current JAR per mod. See [runtime QA and ability setup](docs/QA.md) and the [feature parity audit](docs/PARITY-AUDIT.md).

The current milestone adds Hero’s Entrance: server-controlled ascent, camera-selected particle ring, descent, attributed area damage and knock-up. It also starts SVFrameMobs, adds fourteen native Death Knight/Anti Mage Knight class adapters, and imports user-owned Blockbench models into animated resource-pack displays. **Full upstream plugin parity remains incomplete**; the audit identifies missing systems and unverified behavior.
