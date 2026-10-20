# Thor / Sorcerer Supreme cape flight — 0.2.2

Minecraft 1.21.1, Fabric, Java 21. Use matching FantasyHub Bridge and Client
0.2.2, asset revision `heroes-expansion-v3`, and the supplied SVFrameLib/MMO
builds. Builds and tests run locally; no GitHub Actions or public upload.

## Equipment and controls

| Class | Required armor | Functional Back slot |
| --- | --- | --- |
| Thor | `HERO_THOR_CHESTPLATE`, `HERO_THOR_LEGGINGS`, `HERO_THOR_BOOTS` | `HERO_THOR_CAPE` / `fantasyhub:thor_cape` |
| Sorcerer Supreme | No armor requirement | `HERO_SORCERER_SUPREME_CAPE` / `fantasyhub:cloak_of_levitation` |

Thor's existing unmasked outfit consists of three pieces. Classes remain
independent of equipment; equipping a cape never changes the selected class.
A cape in a hand, the normal inventory, or a different slot does not satisfy
the flight condition. Both Trinkets `chest/back` and Accessories `back` use
their actual equipment APIs. The cape items accept the Back slot rather than
the separate Cape slot.

Double-tap Jump to take off or stop flying. While flying, use normal Minecraft
flight movement: Jump ascends, Sneak descends, and movement keys steer.
Removing required gear, changing class, or movement control effects revoke
the class's flight grant. A short Slow Falling grace accompanies an airborne
revocation. Creative flight and pre-existing flight permission are preserved.
Flight starts off after reconnect/respawn; equipped items and class use their
existing native persistence. Temporary flight permission is excluded from
vanilla player saves, including autosaves during flight.

Server-side rules live in `config/FantasyHub/hero-flight.json`:

- `doubleTapTicks`: input window, default 7 ticks.
- `landingGraceTicks`: Slow Falling grace, default 100 ticks.
- Each class rule has `enabled`, `capeItem`, `requiredArmor`, `speed`, and
  `upkeepPerSecond`. Default upkeep is zero; speed is 0.06 for Thor and 0.05
  for Sorcerer Supreme. Optional upkeep uses that class's existing resource.

Item definitions are in `config/SVFrameItems/items/heroes-capes.yml`. Capes
add no extra combat stats to the existing equal-budget armor sets.

Examples (operator):

```text
/svframeitems give <player> hero_thor_cape
/svframeitems give <player> hero_sorcerer_supreme_cape
```

## Cape appearance

A visible personal cape supplied by the player's Minecraft skin/profile takes
priority. Otherwise the equipped hero cape or hero outfit supplies the Thor
or Doctor Strange fallback. Minecraft's Cape visibility setting is respected.
Accessories' automatic item renderer is disabled for these two items, so it
cannot draw a second cape over the personal cape. Personal cape appearance
does not replace the required physical Back-slot item for flight.

The Thor cape uses the verified JMCT source texture; Doctor Strange uses the
verified Fisk texture. Both remain private data adaptations, with no Forge or
third-party mod executable embedded. The client retains Detailed Animations
and E19; EMF/ETF and the chosen equipment mod remain separate installations.
Xaero is neither embedded nor declared as a dependency.

The current client also includes the corrected third-person Stormbreaker:
relaxed downward/forward carry, with the cutting blade underneath the haft.

## Installation

Replace old JARs by mod ID; do not keep two versions of FantasyHubClient or
FantasyHubCore/Bridge. Install the server JARs under server `mods`, the client
JAR on every client, and use the provided hero-specific config directory for a
new installation. On an existing tuned installation, retain custom values and
merge the new flight/cape files. The mod automatically installs missing files
and only upgrades defaults it owns; it preserves edited configuration.
