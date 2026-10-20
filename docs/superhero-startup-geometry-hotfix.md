# FantasyHubClient 0.2.2 — StartupFix 1

Implementation commit: `c39d6ae79a809bc48b3215228fd17c2e8664ff16`.
Private/local build on `feature/superhero-classes-balanced-local`; no Actions or push.

## Root cause

The supplied failing log identifies GeckoLib 4.9.3 and AzureLib 3.1.21 scanning
`assets/fantasyhub/geo/heroes/*.json`. Five files there contain native Fisk mesh
or Tabula data, rather than Bedrock `minecraft:geometry`. Both libraries scan the
shared `geo/` directory across namespaces. Their GeometryTree loaders index an
empty geometry list, throw ArrayIndexOutOfBoundsException, and fail the reload.
Minecraft retries with selected packs disabled, but these files belong to the
mod's base resources, so the retry fails again and leaves the Mojang loading
screen visible. The later shutdown error is downstream of the failed reload.

This is a FantasyHub asset packaging defect. The earlier cape QA environment
had neither library, so it missed this integration case. The new test environment
retains both libraries; users do not need to remove them.

## Change

Move `cape_default`, `cape_batman`, `tao_mandala`, `mjolnir`, and `stormbreaker`
from `geo/heroes/` into `native_geometry/heroes/`. Update the native weapon loader
and importer/provenance paths. The importer removes the five legacy exports so
rerunning it cannot leave the offending assets behind. The build rejects
non-Bedrock JSON under FantasyHub's reserved `geo/` directory.

All five geometry payloads and every existing PNG are byte-for-byte identical.
The remaining five Hunter Bedrock files stay in `geo/`. No model, UV, texture,
weapon orientation, class, flight rule or server config was redesigned.

Client metadata and protocol remain 0.2.2 to match existing 0.2.2 servers. The
hotfix is identified by the filename `FantasyHubClient-0.2.2-startupfix1.jar`
and `custom.svframe-startup-patch = native-geometry-isolation-1`. Server JARs are
unchanged, verified by hash. Server configuration is unchanged.

## Actual verification

| Check | Result |
| --- | --- |
| Regression before fix | Two resource isolation/provenance tests fail |
| Original delivered JAR + GeckoLib 4.9.3 + AzureLib 3.1.21 | Same GeometryTree stack traces reproduced; inspected actual stuck Mojang screen |
| New client, same two libraries, with EMF/ETF | Initial reload completes; actual title-screen screenshot inspected |
| New client, same libraries, without EMF/ETF, both builtin packs enabled | Cold-start and connection to existing dedicated server pass; protocol/asset handshake verified |
| In-world native weapon and armor | First/third-person actual Stormbreaker screenshots inspected; no missing native weapon or model parse failure |
| Equipment-gated Thor flight | Actual keyboard double-Jump still enables server-authoritative flight |
| Resource migration | 16 checks pass: old paths absent, identical geometry/PNG bytes, real Bedrock exports retained |
| Local build/tests | 154 passed, zero failures/errors; three new regression tests |

Delivery evidence is in `qa/startup-geometry-fix/`, including real before/after
screenshots, logs, asset migration checks and native world results. The client
was tested in software rendering on Linux; this is not a complete reproduction
of every one of the user's 289 Windows mods. The exact two model-loading
libraries from the failing stack were installed, and the failure was reproduced
before the change and absent after it. An existing non-fatal owo early-join
player-context exception remains visible in the world-connection log; it did not
prevent connection and is separate from this resource parsing defect.

## Installation

For an existing 0.2.2 installation, replace the old Client JAR with StartupFix 1.
Keep one FantasyHubClient JAR. The full package also supplies matching unchanged
server JARs and 13 hero config files; existing servers need no config reset.
Do not overwrite customized server values merely to apply this client hotfix.

Minecraft's failed reload may have disabled previously selected resource packs.
After reaching the menu, re-enable the packs you normally use, including the two
SVFrame builtin packs if desired. No third-party executable is bundled into the
client; GeckoLib/AzureLib, EMF/ETF, Better Combat and Xaero remain separate mods.
