package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Gameplay fallback compiled from the live Cobblemon move registry, based on CobblemonMMO 2.1.0's adapter. */
public record CobblemonMoveProfile(
        String moveId,
        String type,
        String damageCategory,
        String targetType,
        Executor executor,
        double baseDamage,
        double range,
        double radius,
        double cooldownSeconds,
        double dashStrength
) {
    private static final Set<String> HEAL_MOVES = Set.of(
            "recover", "roost", "slackoff", "softboiled", "milkdrink", "healorder",
            "shoreup", "synthesis", "moonlight", "morningsun", "lifedew",
            "junglehealing", "lunarblessing", "healbell", "rest");
    private static final Set<String> SHIELD_MOVES = Set.of(
            "protect", "detect", "kingsshield", "spikyshield", "banefulbunker",
            "obstruct", "silktrap", "burningbulwark", "matblock", "wideguard",
            "quickguard", "craftyshield", "maxguard");
    private static final Map<String, String> WEATHER_MOVES = Map.of("raindance", "rain", "sunnyday", "clear");
    private static final Set<String> TELEPORT_MOVES = Set.of("teleport");
    private static final Set<String> DASH_MOVES = Set.of(
            "quickattack", "extremespeed", "aquajet", "bulletpunch", "iceshard",
            "shadowsneak", "suckerpunch", "machpunch", "vacuumwave", "accelerock");

    public static CobblemonMoveProfile of(MoveTemplate move) {
        String id = CobblemonMoveSkillAdapter.id(move.getName());
        String type = move.getElementalType().getName().toLowerCase(Locale.ROOT);
        String category = move.getDamageCategory().getName().toLowerCase(Locale.ROOT);
        String target = move.getTarget().name().toLowerCase(Locale.ROOT);
        Executor executor = executor(id, category, target);
        long cooldownMs = Math.max(500L, Math.min(30_000L,
                1500L + (long) Math.max(0d, move.getPower()) * 15L - move.getPriority() * 150L));
        if (executor == Executor.HEAL || executor == Executor.SHIELD || executor == Executor.WEATHER)
            cooldownMs = Math.max(cooldownMs, 8_000L);
        double baseDamage = Math.max(1d, move.getPower() / 10d);
        double radius = executor == Executor.AOE ? 5d : 0d;
        double range = executor == Executor.AOE ? 6d : 18d;
        double dash = Math.min(2d, 0.8d + Math.max(0, move.getPriority()) * 0.15d);
        return new CobblemonMoveProfile(id, type, category, target, executor, baseDamage, range, radius,
                cooldownMs / 1000d, dash);
    }

    public boolean isHeal() { return HEAL_MOVES.contains(moveId); }
    public boolean isShield() { return SHIELD_MOVES.contains(moveId); }
    public String weather() { return WEATHER_MOVES.get(moveId); }
    public boolean cleanse() { return moveId.equals("healbell") || moveId.equals("junglehealing") || moveId.equals("lunarblessing"); }
    public double healBase() { return moveId.equals("rest") ? 12d : 8d; }

    public boolean requiresSingleTarget() {
        return executor == Executor.TARGET || executor == Executor.PROJECTILE || executor == Executor.TARGET_DEBUFF;
    }

    public String fallbackStatus() {
        return switch (type) {
            case "poison" -> "minecraft:poison";
            case "ice", "water", "electric", "grass" -> "minecraft:slowness";
            default -> "minecraft:weakness";
        };
    }

    private static Executor executor(String id, String category, String target) {
        if (HEAL_MOVES.contains(id)) return Executor.HEAL;
        if (SHIELD_MOVES.contains(id)) return Executor.SHIELD;
        if (WEATHER_MOVES.containsKey(id)) return Executor.WEATHER;
        if (TELEPORT_MOVES.contains(id)) return Executor.TELEPORT;
        if (DASH_MOVES.contains(id)) return Executor.DASH;
        if (target.equals("all") || target.contains("alladjacent")) return Executor.AOE;
        if (target.contains("self") || target.contains("allyside") || target.contains("allyteam")) return Executor.SELF_BUFF;
        if (category.equals("status")) return Executor.TARGET_DEBUFF;
        return category.equals("physical") ? Executor.TARGET : Executor.PROJECTILE;
    }

    public enum Executor { HEAL, SHIELD, WEATHER, TELEPORT, DASH, AOE, SELF_BUFF, TARGET_DEBUFF, TARGET, PROJECTILE }
}
