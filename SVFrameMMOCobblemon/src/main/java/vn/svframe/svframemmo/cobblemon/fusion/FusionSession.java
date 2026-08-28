package vn.svframe.svframemmo.cobblemon.fusion;

import vn.svframe.svframemmo.skill.runtime.TemporarySkillOverlayRuntime;

import java.util.List;
import java.util.UUID;

/** Immutable fusion identity plus runtime resources that are released atomically. */
public record FusionSession(
        UUID playerUuid,
        UUID pokemonUuid,
        UUID deployedEntityUuid,
        String speciesId,
        String pokemonName,
        FusionTier tier,
        long startedAtTick,
        long expiresAtTick,
        boolean manualUnfuseAllowed,
        boolean originalTradeable,
        boolean autoDeployed,
        List<String> moveIds,
        TemporarySkillOverlayRuntime.Handle overlay
) {
    public FusionSession { moveIds = List.copyOf(moveIds); }
    public boolean expires() { return expiresAtTick >= 0L; }
    public boolean expired(long tick) { return expires() && tick >= expiresAtTick; }
    public long remainingTicks(long tick) { return expires() ? Math.max(0L, expiresAtTick - tick) : -1L; }
    public double bonusMultiplier() { return 1.0d + tier.multiplier(); }
    public boolean dance() { return tier == FusionTier.DANCE; }
    public String typeName() { return dance() ? "dance" : "potara"; }
}
