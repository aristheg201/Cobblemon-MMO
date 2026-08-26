package vn.svframe.svframelib.skill.parameter.value;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.Map;

/** MythicLib 1.7.1 scaling formula contract, adapted to Fabric player types. */
public interface ScalingFormula {
    ScalingFormula ZERO = new NonScalingFormula(0d);

    double evaluate(int level, ServerPlayerEntity player);

    boolean isInteger();

    static ScalingFormula fromConfig(Object input) {
        return fromConfig(input, null);
    }

    static ScalingFormula fromConfig(Object input, ScalingFormula previous) {
        if (input == null) return ZERO;
        if (input instanceof Number number) return new NonScalingFormula(number.doubleValue());
        if (input instanceof ConfigObject config) {
            if (config.contains("base")) return new LinearScalingFormula(config);
            if (config.contains("formula")) return new CustomScalingFormula(config, previous);
            throw new IllegalArgumentException("Skill parameter formula must contain either 'base' or 'formula' as key");
        }
        if (input instanceof Map<?, ?> map) {
            if (map.containsKey("base")) return new LinearScalingFormula(map);
            if (map.containsKey("formula")) return new CustomScalingFormula(map, previous);
            throw new IllegalArgumentException("Skill parameter formula must contain either 'base' or 'formula' as key");
        }
        if (input instanceof String string) return new CustomScalingFormula(string);
        throw new IllegalArgumentException("Skill parameter formula must be a string, number or config section");
    }
}
