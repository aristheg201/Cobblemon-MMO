package vn.svframe.svframemmo.cobblemon.cosmetic;

import java.util.List;
import java.util.Locale;

/** Data-driven player cosmetic definition. Cosmetic identity is a player slot, never a skill id. */
public record CosmeticDefinition(String id, Slot slot, String name, String particleId,
                                 List<Phase> phases, Fallback fallback, boolean hideWithoutResourcePack,
                                 String permission) {
    public CosmeticDefinition {
        id = normalize(id);
        if (id.isBlank()) throw new IllegalArgumentException("Cosmetic id must not be blank");
        if (slot == null) throw new IllegalArgumentException("Cosmetic slot must not be null");
        if (particleId == null || !particleId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || particleId.contains(".."))
            throw new IllegalArgumentException("Invalid Snowstorm particle id: " + particleId);
        name = name == null || name.isBlank() ? id : name;
        phases = phases == null ? List.of() : List.copyOf(phases);
        fallback = fallback == null ? Fallback.none() : fallback;
        permission = permission == null || permission.isBlank() ? "svframemmo.cobblemon.cosmetic.use." + id : permission;
    }

    public List<Phase> phases(Trigger trigger) {
        return phases.stream().filter(phase -> phase.trigger() == trigger).toList();
    }

    public record Phase(Trigger trigger, Anchor anchor, int delayTicks, int repetitions, int intervalTicks,
                        double offsetX, double offsetY, double offsetZ,
                        double broadcastRadius, int maxViewers,
                        double movementThreshold, double orbitRadius, int orbitPeriodTicks) {
        public Phase {
            if (trigger == null) trigger = Trigger.EQUIP;
            if (anchor == null) anchor = Anchor.BODY;
            if (delayTicks < 0 || delayTicks > 1200) throw new IllegalArgumentException("Invalid cosmetic delay");
            if (repetitions < 1 || repetitions > 32) throw new IllegalArgumentException("Invalid cosmetic repetitions");
            if (intervalTicks < 1 || intervalTicks > 1200) throw new IllegalArgumentException("Invalid cosmetic interval");
            if (!Double.isFinite(broadcastRadius) || broadcastRadius <= 0d || broadcastRadius > 64d)
                throw new IllegalArgumentException("Invalid cosmetic radius");
            if (maxViewers < 1 || maxViewers > 128) throw new IllegalArgumentException("Invalid cosmetic viewer limit");
            if (!Double.isFinite(movementThreshold) || movementThreshold < 0d || movementThreshold > 8d)
                throw new IllegalArgumentException("Invalid cosmetic movement threshold");
            if (!Double.isFinite(orbitRadius) || orbitRadius < 0d || orbitRadius > 8d)
                throw new IllegalArgumentException("Invalid cosmetic orbit radius");
            if (orbitPeriodTicks < 1 || orbitPeriodTicks > 2400)
                throw new IllegalArgumentException("Invalid cosmetic orbit period");
        }
    }

    public record Fallback(String particleId, int count, double spread, double speed) {
        public Fallback {
            particleId = particleId == null ? "" : particleId;
            if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid fallback count");
            if (!Double.isFinite(spread) || spread < 0d || spread > 8d)
                throw new IllegalArgumentException("Invalid fallback spread");
            if (!Double.isFinite(speed) || speed < 0d || speed > 8d)
                throw new IllegalArgumentException("Invalid fallback speed");
        }
        public static Fallback none() { return new Fallback("", 0, 0d, 0d); }
        public boolean enabled() { return !particleId.isBlank() && count > 0; }
    }

    public enum Trigger {
        PREVIEW, EQUIP, EQUIP_BURST, WHILE_EQUIPPED, UNEQUIP;

        static Trigger parse(String value) {
            return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        }
    }

    public enum Slot {
        AURA(Anchor.BODY),
        TRAIL(Anchor.FEET),
        HEAD(Anchor.HEAD),
        BACK(Anchor.BACK),
        ORBIT(Anchor.ORBIT),
        FOOTSTEP(Anchor.FEET);

        private final Anchor defaultAnchor;

        Slot(Anchor defaultAnchor) { this.defaultAnchor = defaultAnchor; }

        public Anchor defaultAnchor() { return defaultAnchor; }

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Slot parse(String value) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Cosmetic slot is blank");
            return valueOf(value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT));
        }

        public static Slot tryParse(String value) {
            try { return parse(value); }
            catch (RuntimeException ignored) { return null; }
        }
    }

    public enum Anchor { BODY, FEET, HEAD, BACK, ORBIT }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
