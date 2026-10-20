# Better Combat integration (Fabric 1.21.1)

Better Combat remains a separate installation. FantasyHub does not embed it,
playerAnimator, Cloth Config, EMF, ETF or Xaero, and does not declare a hard
dependency on Better Combat or Xaero. Install the matching Better Combat build
and its dependencies on both server and clients when using Better Combat:

| Mod | Verified local version | Source version |
| --- | --- | --- |
| Better Combat | 2.4.0+1.21.1 Fabric | https://modrinth.com/mod/better-combat/version/JJQpBecL |
| playerAnimator | 2.0.4+1.21.1 Fabric | https://modrinth.com/mod/playeranimator/version/CkedfDp3 |
| Cloth Config | 15.0.140 Fabric | https://modrinth.com/mod/cloth-config/version/HpMb5wGb |

FantasyHub itself can start without Better Combat. A client without Better
Combat cannot join a server that has Better Combat's synced registry installed;
this is separate from FantasyHub's optional integration.

## Weapons and damage

`fantasyhub:stormbreaker` is a real axe item. Its data-driven profile inherits
`bettercombat:axe`: two alternating horizontal swings, a 90-degree hitbox and
the parent's -0.25 range bonus. The profile adds no damage multiplier. Native
and SVFrame-generated Stormbreaker use equivalent +8 attack damage and -3.1
attack speed modifiers; the SVFrame source rule removes vanilla attributes so
the same bonus cannot be applied twice. Other ordinary weapons keep their
Better Combat profiles.

Class abilities continue through SVFrame's server-side damage, resource and
cooldown pipeline. Better Combat owns normal weapon swings and their target
validation. It does not receive a second skill damage implementation. Busy
class channels block unrelated ordinary attacks server-side.

## Animation ownership

Optional mixins observe actual Better Combat attack start/stop events for local
and remote players. A class cast fades its current weapon attack and temporarily
suppresses the local Better Combat idle pose. Detailed Animations yields through
EMF's registered pause-condition API while an attack or class cast owns the
pose; finite attack lifetimes prevent a permanent animation lock. This does not
globally resume animations paused by another mod.

Stormbreaker's third-person grip uses the native held-item transform. Its
ordinary first-person item renderer adds the player's actual skin/suit arm at
the weapon's grip, sharing the item swing/equip matrix. Original first-person
scale is retained. The blade is rolled about the haft so its cutting edge faces
into the scene; third-person geometry rotates about the fist into a relaxed carry: the head
sits below and ahead of the hand, with the cutting edge facing forward in a
vertical plane. Both real and manifested weapons use the
same third-person grip and orientation. During playerAnimator's
third-person-model first-person pass, its own arm remains authoritative and the
item renderer does not add another arm.

## Deployment

Use the matching FantasyHub Bridge and Client 0.2.1 builds, asset revision
`heroes-expansion-v2`, together with the supplied local SVFrameLib and
SVFrameMMO builds. Replace earlier JARs by mod ID; do not keep both versions.
The embedded Detailed Animations and E19 packs remain independently selectable.
EMF/ETF are required separately for the animation pack; Xaero is supplied by
the user's existing client pack and is not installed by FantasyHub.

Builds and native tests run locally. No GitHub Actions or public asset upload
is part of this workflow. Native QA evidence belongs under
`qa-evidence/superheroes/better-combat` in the private workspace.
