package vn.svframe.svframemobs;

import java.util.*;

/** Validated subset of Mythic-style mob configuration; unsupported fields are rejected. */
public record MobDefinition(String id, String type, String display, double health, double damage,
                            double armor, double speed, boolean noAi, boolean silent,
                            Map<String, String> equipment, List<Drop> drops, List<SkillLine> skills) {
    public record Drop(String item, int amount, double chance) { }
    private static final Set<String> FIELDS = Set.of("Type", "Display", "Health", "Damage", "Armor", "MovementSpeed", "Options", "Equipment", "Drops", "Skills");
    public static MobDefinition parse(String id, Map<String, Object> map) {
        if (!id.matches("[A-Za-z0-9_]{1,64}")) throw new IllegalArgumentException("Invalid mob ID: " + id);
        for (String key : map.keySet()) if (!FIELDS.contains(key)) throw new IllegalArgumentException(id + ": unsupported field " + key);
        Map<?, ?> options = map.get("Options") instanceof Map<?, ?> m ? m : Map.of();
        for (Object key : options.keySet()) if (!Set.of("NoAI", "Silent").contains(key.toString())) throw new IllegalArgumentException(id + ": unsupported option " + key);
        Map<String, String> equipment = new LinkedHashMap<>();
        for (Object value : list(map.get("Equipment"))) {
            String[] parts = value.toString().trim().split("\\s+");
            if (parts.length != 2 || !Set.of("HEAD", "CHEST", "LEGS", "FEET", "HAND", "OFFHAND").contains(parts[1].toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Equipment requires item SLOT");
            equipment.put(parts[1].toUpperCase(Locale.ROOT), parts[0]);
        }
        List<Drop> drops = new ArrayList<>();
        for (Object value : list(map.get("Drops"))) {
            String[] parts = value.toString().trim().split("\\s+");
            if (parts.length < 1 || parts.length > 3) throw new IllegalArgumentException("Drop requires item [amount] [chance]");
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            double chance = parts.length > 2 ? Double.parseDouble(parts[2]) : 1;
            if (amount < 1 || amount > 64 || !Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Invalid drop amount/chance");
            drops.add(new Drop(parts[0], amount, chance));
        }
        return new MobDefinition(id, String.valueOf(map.getOrDefault("Type", "ZOMBIE")), String.valueOf(map.getOrDefault("Display", id)),
                number(map,"Health",20,1,100000), number(map,"Damage",3,0,10000), number(map,"Armor",0,0,30), number(map,"MovementSpeed",0.23,0,2),
                Boolean.parseBoolean(String.valueOf(options.get("NoAI"))), Boolean.parseBoolean(String.valueOf(options.get("Silent"))),
                Map.copyOf(equipment), List.copyOf(drops), list(map.get("Skills")).stream().map(v -> SkillLine.parse(v.toString())).toList());
    }
    private static List<?> list(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> values) return values;
        throw new IllegalArgumentException("Expected YAML list: " + value);
    }
    private static double number(Map<String, Object> map, String key, double fallback, double min, double max) {
        double value = Double.parseDouble(String.valueOf(map.getOrDefault(key, fallback)));
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid " + key + ": " + value);
        return value;
    }
}
