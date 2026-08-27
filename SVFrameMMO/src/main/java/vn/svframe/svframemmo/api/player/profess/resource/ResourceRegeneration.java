package vn.svframe.svframemmo.api.player.profess.resource;

import vn.svframe.svframelib.skill.parameter.value.FormulaFailsafeException;
import vn.svframe.svframelib.skill.parameter.value.ScalingFormula;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ResourceRegeneration {
    private final boolean offCombatOnly;
    private final ScalingFormula scalar;
    private final HandlerType type;
    private final PlayerResource resource;

    public ResourceRegeneration(PlayerResource resource) {
        this(resource, null, null, false);
    }

    public ResourceRegeneration(PlayerResource resource, Map<String, Object> config) {
        this.resource = Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(config, "config");
        this.offCombatOnly = bool(config.get("off-combat"), false);
        Object typeInput = config.get("type");
        if (typeInput == null) throw new IllegalArgumentException("Could not find scaling type");
        this.type = HandlerType.valueOf(String.valueOf(typeInput).trim().toUpperCase(Locale.ROOT));
        Object value = config.get("value");
        if (value == null) throw new IllegalArgumentException("Could not find regen value");
        this.scalar = ScalingFormula.fromConfig(value);
    }

    public ResourceRegeneration(PlayerResource resource, HandlerType type, ScalingFormula scalar, boolean offCombatOnly) {
        this.resource = Objects.requireNonNull(resource, "resource");
        this.type = type;
        this.scalar = scalar;
        this.offCombatOnly = offCombatOnly;
    }

    /** Amount regenerated per second. */
    public double getRegen(PlayerData player) {
        Objects.requireNonNull(player, "player");
        double amount = 0d;
        if (!player.isInCombat() || !player.getProfess().hasOption(resource.getOffCombatRegen())) {
            amount += player.getMMOPlayerData().getStatMap().getStat(resource.getRegenStat());
            amount += player.getMMOPlayerData().getStatMap().getStat(resource.getMaxRegenStat()) / 100d * resource.getMax(player);
        }
        if (type != null && (!player.isInCombat() || !offCombatOnly)) {
            try {
                amount += scalar.evaluate(player.getLevel(), player.getPlayer()) / 100d * type.getScaling(player, resource);
            } catch (FormulaFailsafeException exception) {
                exception.log("Could not evaluate special %s regeneration for class %s", resource.name(), player.getProfess().getId());
            }
        }
        return amount;
    }

    public boolean isSpecial() { return type != null; }
    public boolean isOffCombatOnly() { return offCombatOnly; }
    public HandlerType getType() { return type; }
    public ScalingFormula getScalar() { return scalar; }

    public enum HandlerType {
        MAX {
            @Override public double getScaling(PlayerData player, PlayerResource resource) { return resource.getMax(player); }
        },
        MISSING {
            @Override public double getScaling(PlayerData player, PlayerResource resource) { return resource.getMax(player) - resource.getCurrent(player); }
        };

        public abstract double getScaling(PlayerData player, PlayerResource resource);
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
