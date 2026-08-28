package vn.svframe.svframemmo.cobblemon.cosmetic;

import java.util.List;
import java.util.Locale;

/** Cosmetic-only VFX definition. It never modifies damage, cooldown, stats or progression. */
public record CosmeticDefinition(String id, String skillId, String name, String particleId,
                                 List<Phase> phases, Fallback fallback, boolean hideWithoutResourcePack,
                                 String permission) {
    public CosmeticDefinition {
        id = normalize(id);
        skillId = normalizeSkill(skillId);
        if (id.isBlank() || skillId.isBlank()) throw new IllegalArgumentException("Cosmetic id/skill must not be blank");
        if (particleId == null || !particleId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || particleId.contains(".."))
            throw new IllegalArgumentException("Invalid Snowstorm particle id: " + particleId);
        name = name == null || name.isBlank() ? id : name;
        phases = phases == null ? List.of() : List.copyOf(phases);
        fallback = fallback == null ? Fallback.none() : fallback;
        permission = permission == null || permission.isBlank() ? "svframemmo.cobblemon.cosmetic.use." + id : permission;
    }

    public List<Phase> phases(Trigger trigger) { return phases.stream().filter(phase -> phase.trigger() == trigger).toList(); }

    public record Phase(Trigger trigger, Anchor anchor, int delayTicks, int repetitions, int intervalTicks,
                        double offsetX, double offsetY, double offsetZ, double broadcastRadius, int maxViewers) {
        public Phase {
            if (trigger == null) trigger = Trigger.CAST_SUCCESS;
            if (anchor == null) anchor = Anchor.CASTER;
            if (delayTicks < 0 || delayTicks > 1200) throw new IllegalArgumentException("Invalid cosmetic delay");
            if (repetitions < 1 || repetitions > 32) throw new IllegalArgumentException("Invalid cosmetic repetitions");
            if (intervalTicks < 1 || intervalTicks > 1200) throw new IllegalArgumentException("Invalid cosmetic interval");
            if (!Double.isFinite(broadcastRadius) || broadcastRadius <= 0d || broadcastRadius > 64d) throw new IllegalArgumentException("Invalid cosmetic radius");
            if (maxViewers < 1 || maxViewers > 128) throw new IllegalArgumentException("Invalid cosmetic viewer limit");
        }
    }

    public record Fallback(String particleId, int count, double spread, double speed) {
        public Fallback {
            particleId = particleId == null ? "" : particleId;
            if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid fallback count");
            if (!Double.isFinite(spread) || spread < 0d || spread > 8d) throw new IllegalArgumentException("Invalid fallback spread");
            if (!Double.isFinite(speed) || speed < 0d || speed > 8d) throw new IllegalArgumentException("Invalid fallback speed");
        }
        public static Fallback none() { return new Fallback("", 0, 0d, 0d); }
        public boolean enabled() { return !particleId.isBlank() && count > 0; }
    }

    public enum Trigger {
        CAST_START, CAST_SUCCESS, TARGET_HIT, PREVIEW, EQUIP, EQUIP_BURST, WHILE_EQUIPPED, UNEQUIP;
        static Trigger parse(String value) { return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT)); }
    }
    public enum Anchor { CASTER, CAST_POSITION, TARGET }

    static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"); }
    static String normalizeSkill(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
}
