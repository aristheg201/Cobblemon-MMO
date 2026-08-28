package vn.svframe.svframemmo.cobblemon.fusion;

import vn.svframe.svframemmo.skill.runtime.TemporarySkillOverlayRuntime;

import java.util.List;
import java.util.UUID;

/** Immutable fusion identity plus the runtime resources that must be released atomically. */
public record FusionSession(
        UUID playerUuid,
        UUID pokemonUuid,
        UUID deployedEntityUuid,
        FusionTier tier,
        long startedAtTick,
        long expiresAtTick,
        boolean manualUnfuseAllowed,
        boolean originalTradeable,
        List<String> moveIds,
        TemporarySkillOverlayRuntime.Handle overlay
) {
    public FusionSession {
        moveIds = List.copyOf(moveIds);
    }

    public boolean expires() { return expiresAtTick >= 0L; }
    public boolean expired(long tick) { return expires() && tick >= expiresAtTick; }
    public double bonusMultiplier() { return 1.0d + tier.multiplier(); }
}
