package vn.svframe.svframelib.fabric;

import java.util.Map;

/** Bounded server work and finite motion even for administrator-supplied skill parameters. */
public record HeroEntranceSettings(double range, double radius, double height, double damage,
                                   double knockup, int aimTicks, int descentTicks) {
    public static HeroEntranceSettings from(Map<String, ?> parameters) {
        return new HeroEntranceSettings(value(parameters, "range", 32, 4, 48),
                value(parameters, "radius", 5, 1, 12), value(parameters, "height", 12, 4, 24),
                value(parameters, "damage", 12, 0, 10000), value(parameters, "knockup", 1.1, 0, 3),
                (int) value(parameters, "aim_ticks", 60, 20, 200),
                (int) value(parameters, "descent_ticks", 12, 6, 40));
    }

    private static double value(Map<String, ?> parameters, String key, double fallback, double min, double max) {
        Object raw = parameters.get(key);
        double result = raw instanceof Number number ? number.doubleValue() : fallback;
        return Double.isFinite(result) ? Math.max(min, Math.min(max, result)) : fallback;
    }
}
