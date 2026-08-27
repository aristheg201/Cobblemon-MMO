package vn.svframe.svframeitems.config;

import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import vn.svframe.svframeitems.model.*;

import java.util.*;

import static vn.svframe.svframeitems.config.ConfigValues.*;

public final class DefinitionParser {
    private DefinitionParser() {}

    public static ItemType type(String id, Map<String,Object> section) {
        NativeStatEngine.ModifierSource source = enumeration(section, "modifier-source", NativeStatEngine.ModifierSource.class, NativeStatEngine.ModifierSource.OTHER);
        Set<NativeStatEngine.EquipmentSlot> slots = new LinkedHashSet<>();
        for (String value : strings(section.get("slots"))) slots.add(NativeStatEngine.EquipmentSlot.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        return new ItemType(id, source, slots, integer(section, "max-stack-size", 1));
    }

    public static ItemRarity rarity(String id, Map<String,Object> section) {
        return new ItemRarity(id, string(section, "name", id), integer(section, "weight", 1), integer(section, "priority", 0), decimal(section, "stat-multiplier", 1d));
    }

    public static ItemDefinition item(String id, Map<String,Object> section) {
        int minLevel = integer(section, "min-level", 1);
        int maxLevel = integer(section, "max-level", Math.max(minLevel, integer(section, "level", minLevel)));
        int defaultLevel = integer(section, "level", minLevel);
        Map<String,Integer> rarity = integerMap(section.get("rarities"));
        if (rarity.isEmpty()) rarity = Map.of(string(section, "rarity", "common"), 1);
        List<StatRollSpec> stats = new ArrayList<>();
        Object statObject = section.get("stats");
        if (statObject instanceof Map<?,?> rawStats) {
            for (Map.Entry<?,?> entry : rawStats.entrySet()) stats.add(statRoll(String.valueOf(entry.getKey()), map(entry.getValue())));
        } else for (Object value : list(statObject)) {
            Map<String,Object> stat = map(value);
            stats.add(statRoll(string(stat, "stat", ""), stat));
        }
        List<ItemAbility> abilities = new ArrayList<>();
        for (Object value : list(section.get("abilities"))) abilities.add(ability(map(value)));
        return new ItemDefinition(
                id,
                string(section, "type", "miscellaneous"),
                string(section, "material", "minecraft:stone"),
                string(section, "name", id),
                integer(section, "revision", 1),
                defaultLevel,
                minLevel,
                maxLevel,
                rarity,
                stats,
                strings(section.get("sockets")),
                string(section, "set", null),
                string(section, "upgrade-template", null),
                abilities,
                string(section, "gem-color", null));
    }

    public static UpgradeTemplate upgrade(String id, Map<String,Object> section) {
        Map<Integer,Double> chances = new LinkedHashMap<>();
        for (Map.Entry<String,Object> entry : map(section.get("chances")).entrySet()) {
            chances.put(Integer.parseInt(entry.getKey()), probability(entry.getValue()));
        }
        List<UpgradeTemplate.Cost> costs = new ArrayList<>();
        for (Object value : list(section.get("costs"))) {
            Map<String,Object> cost = map(value);
            costs.add(new UpgradeTemplate.Cost(
                    string(cost, "provider", "minecraft_item"),
                    string(cost, "id", ""),
                    integer(cost, "amount", 0),
                    integer(cost, "per-level", 0)));
        }
        return new UpgradeTemplate(
                id,
                integer(section, "max-level", 10),
                probability(section.getOrDefault("base-success", 1d)),
                decimal(section, "success-decay", 1d),
                bool(section, "destroy-on-fail", false),
                decimal(section, "stat-multiplier-per-level", .05d),
                chances,
                costs);
    }

    public static ItemSetDefinition set(String id, Map<String,Object> section) {
        Set<String> pieces = new LinkedHashSet<>(strings(section.get("pieces")));
        NavigableMap<Integer,List<ItemStat>> bonuses = new TreeMap<>();
        for (Map.Entry<String,Object> entry : map(section.get("bonuses")).entrySet()) {
            int threshold = Integer.parseInt(entry.getKey());
            List<ItemStat> stats = new ArrayList<>();
            Object value = entry.getValue();
            if (value instanceof Map<?,?> raw) {
                for (Map.Entry<?,?> stat : raw.entrySet()) stats.add(fixedStat(String.valueOf(stat.getKey()), stat.getValue()));
            } else for (Object stat : list(value)) {
                Map<String,Object> statMap = map(stat);
                stats.add(fixedStat(string(statMap, "stat", ""), statMap));
            }
            bonuses.put(threshold, stats);
        }
        return new ItemSetDefinition(id, string(section, "name", id), pieces, bonuses);
    }

    public static RecipeDefinition recipe(String id, Map<String,Object> section) {
        List<RecipeDefinition.Ingredient> ingredients = new ArrayList<>();
        for (Object value : list(section.get("ingredients"))) {
            Map<String,Object> ingredient = map(value);
            RecipeDefinition.IngredientKind kind = enumeration(ingredient, "kind", RecipeDefinition.IngredientKind.class, RecipeDefinition.IngredientKind.VANILLA);
            ingredients.add(new RecipeDefinition.Ingredient(kind, string(ingredient, "id", ""), integer(ingredient, "count", 1)));
        }
        Map<String,Object> output = map(section.get("output"));
        return new RecipeDefinition(id, ingredients, string(output, "item", ""), integer(output, "amount", 1), integer(output, "level", 1));
    }

    public static LootTableDefinition loot(String id, Map<String,Object> section) {
        List<LootTableDefinition.Entry> entries = new ArrayList<>();
        for (Object value : list(section.get("entries"))) {
            Map<String,Object> entry = map(value);
            entries.add(new LootTableDefinition.Entry(
                    string(entry, "item", ""), integer(entry, "weight", 1), probability(entry.getOrDefault("chance", 1d)),
                    integer(entry, "min-amount", 1), integer(entry, "max-amount", integer(entry, "min-amount", 1)),
                    integer(entry, "min-level", 1), integer(entry, "max-level", integer(entry, "min-level", 1)),
                    string(entry, "condition", "always"), string(entry, "reward", "item")));
        }
        return new LootTableDefinition(id, integer(section, "rolls", 1), entries);
    }

    private static StatRollSpec statRoll(String stat, Map<String,Object> section) {
        double min = decimal(section, "min", decimal(section, "value", 0d));
        double max = decimal(section, "max", min);
        return new StatRollSpec(stat, min, max, decimal(section, "per-level", 0d), integer(section, "decimals", 2), enumeration(section, "type", NativeStatEngine.ModifierType.class, NativeStatEngine.ModifierType.FLAT));
    }

    private static ItemStat fixedStat(String stat, Object value) {
        if (value instanceof Map<?,?>) {
            Map<String,Object> section = map(value);
            return new ItemStat(stat, decimal(section, "value", 0d), enumeration(section, "type", NativeStatEngine.ModifierType.class, NativeStatEngine.ModifierType.FLAT));
        }
        double number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
        return new ItemStat(stat, number, NativeStatEngine.ModifierType.FLAT);
    }

    private static ItemAbility ability(Map<String,Object> section) {
        Map<String,Double> params = new LinkedHashMap<>();
        map(section.get("parameters")).forEach((key,value) -> params.put(key, value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value))));
        return new ItemAbility(
                enumeration(section, "trigger", ItemAbility.Trigger.class, ItemAbility.Trigger.USE),
                string(section, "skill", ""),
                probability(section.getOrDefault("chance", 1d)),
                integer(section, "cooldown-ticks", 0),
                params);
    }

    private static double probability(Object value) {
        double number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
        if (number > 1d) number /= 100d;
        if (!Double.isFinite(number) || number < 0d || number > 1d) throw new IllegalArgumentException("Probability out of range: " + value);
        return number;
    }
    private static Map<String,Integer> integerMap(Object value) {
        Map<String,Integer> out = new LinkedHashMap<>();
        map(value).forEach((key,raw) -> out.put(key, raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw))));
        return out;
    }
}
