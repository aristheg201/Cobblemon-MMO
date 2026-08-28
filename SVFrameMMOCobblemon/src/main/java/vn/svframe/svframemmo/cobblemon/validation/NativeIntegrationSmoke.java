package vn.svframe.svframemmo.cobblemon.validation;

import vn.svframe.svframemmo.cobblemon.fusion.FusionCooldowns;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.cobblemon.fusion.FusionTier;
import vn.svframe.svframemmo.cobblemon.move.BattleStat;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;
import vn.svframe.svframemmo.cobblemon.move.MoveSemantic;
import vn.svframe.svframemmo.cobblemon.move.MoveSemanticRegistry;

import java.util.UUID;

public final class NativeIntegrationSmoke {
    public static void main(String[] args) {
        MoveSemanticRegistry registry = new MoveSemanticRegistry();
        MoveSemantic swordsDance = registry.resolveKnown("swordsdance");
        require(swordsDance.stages().stream().anyMatch(c -> c.target() == MoveSemantic.Target.SELF && c.stat() == BattleStat.ATTACK && c.stages() == 2), "Swords Dance must be Attack +2");
        MoveSemantic lick = registry.resolveKnown("lick");
        require(lick.status() == MoveSemantic.Status.PARALYSIS && close(lick.statusChance(), 0.30), "Lick must preserve 30% paralysis");
        MoveSemantic bite = registry.resolveKnown("bite");
        require(bite.status() == MoveSemantic.Status.FLINCH && close(bite.statusChance(), 0.30), "Bite must preserve 30% flinch");
        require(FusionService.DEFAULT_DANCE_DURATION_TICKS == 12_000L, "Fusion Dance must be exactly 10 minutes");
        require(close(FusionTier.DANCE.multiplier(), 0.10), "Dance multiplier");
        require(close(FusionTier.BASIC.multiplier(), 0.25), "Basic Potara multiplier");
        require(close(FusionTier.LEVEL_2.multiplier(), 0.50), "Level 2 Potara multiplier");
        require(close(FusionTier.ADVANCEMENT.multiplier(), 0.75), "Advancement Potara multiplier");
        require(close(FusionTier.GOD.multiplier(), 1.00), "God Potara multiplier");
        require(CobblemonMoveSkillAdapter.canonicalId("Thunder Bolt").equals("COBBLEMON_MOVE_THUNDERBOLT"), "canonical Cobblemon skill id");
        require(close(BattleStat.multiplier(2), 2.0), "+2 stage multiplier must be 2x");
        require(close(BattleStat.multiplier(-1), 2.0 / 3.0), "-1 stage multiplier must be 2/3x");
        FusionCooldowns cooldowns = new FusionCooldowns();
        UUID id = UUID.randomUUID();
        cooldowns.markPotara(id, 10);
        require(cooldowns.potaraRemainingMillis(id) > 0L, "Potara cooldown must be timestamp based");
        cooldowns.markDance(id, 15 * 60);
        require(cooldowns.danceRemainingMillis(id) > 14L * 60L * 1000L, "Fusion Dance cooldown must support 15 minutes");
        System.out.println("SVFRAMEMMO_COBBLEMON_NATIVE=PASS");
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 1.0e-9; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
