# Huyết Ảnh Phi Phong

Particle `BACK` cosmetic for SVFrameMMO Cobblemon Integration 0.1.13+.

## Install

1. Copy the `config/` directory from this example pack into the server root.
2. Restart the server once. This pack adds a Snowstorm particle asset, so Polymer must rebuild the resource pack.
3. `/cosmetics grant <player> back_huyet_anh_phi_phong`
4. `/cosmetics equip back_huyet_anh_phi_phong`

After the first restart, edits to the cosmetic YAML offsets/cadence can be applied with `/cosmetics reload`.
Changes to the Snowstorm `.particle.json` or PNG texture require another restart/resource-pack rebuild.

## Shape

- 9 short-lived particle anchors.
- 3 wide shoulder points, 3 torso points, 2 lower points, 1 tail point.
- Every point uses player-local `BACK` coordinates, so the cloak rotates with player yaw.
- 5-tick refresh and ~0.32 s particle lifetime keep the silhouette attached to the player instead of turning into a movement trail.
