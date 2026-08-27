package vn.svframe.svframelib.version;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Native 1.21.1 attribute registry retaining SVFrameLib's name/adapt surface. */
public class Attributes {
    private static final Map<String, RegistryEntry<EntityAttribute>> BY_ID = new LinkedHashMap<>();
    private static final Map<RegistryEntry<EntityAttribute>, String> NAMES = new LinkedHashMap<>();

    public static final RegistryEntry<EntityAttribute> ARMOR = register("ARMOR", "generic.armor");
    public static final RegistryEntry<EntityAttribute> ARMOR_TOUGHNESS = register("ARMOR_TOUGHNESS", "generic.armor_toughness");
    public static final RegistryEntry<EntityAttribute> ATTACK_DAMAGE = register("ATTACK_DAMAGE", "generic.attack_damage");
    public static final RegistryEntry<EntityAttribute> ATTACK_SPEED = register("ATTACK_SPEED", "generic.attack_speed");
    public static final RegistryEntry<EntityAttribute> KNOCKBACK_RESISTANCE = register("KNOCKBACK_RESISTANCE", "generic.knockback_resistance");
    public static final RegistryEntry<EntityAttribute> LUCK = register("LUCK", "generic.luck");
    public static final RegistryEntry<EntityAttribute> MAX_HEALTH = register("MAX_HEALTH", "generic.max_health");
    public static final RegistryEntry<EntityAttribute> MOVEMENT_SPEED = register("MOVEMENT_SPEED", "generic.movement_speed");
    public static final RegistryEntry<EntityAttribute> FOLLOW_RANGE = register("FOLLOW_RANGE", "generic.follow_range");
    public static final RegistryEntry<EntityAttribute> ENTITY_INTERACTION_RANGE = register("ENTITY_INTERACTION_RANGE", "player.entity_interaction_range");
    public static final RegistryEntry<EntityAttribute> BLOCK_INTERACTION_RANGE = register("BLOCK_INTERACTION_RANGE", "player.block_interaction_range");
    public static final RegistryEntry<EntityAttribute> ATTACK_KNOCKBACK = register("ATTACK_KNOCKBACK", "generic.attack_knockback");

    public Attributes() { }

    public static RegistryEntry<EntityAttribute> fromName(String... names) {
        if (names == null) throw new IllegalArgumentException("No attribute names provided");
        for (String name : names) {
            if (name == null) continue;
            String key = normalize(name);
            RegistryEntry<EntityAttribute> found = BY_ID.get(key);
            if (found != null) return found;
            Identifier id = id(name);
            if (id != null) {
                var entry = Registries.ATTRIBUTE.getEntry(id);
                if (entry.isPresent()) return entry.get();
            }
        }
        throw new IllegalArgumentException("Unknown attribute: " + java.util.Arrays.toString(names));
    }

    public static String name(RegistryEntry<EntityAttribute> attribute) {
        String named = NAMES.get(attribute);
        if (named != null) return named;
        return attribute == null ? null : attribute.getKey().map(key -> key.getValue().getPath().toUpperCase(Locale.ROOT).replace('.', '_')).orElse(null);
    }

    public static RegistryEntry<EntityAttribute> adapt(String name) {
        return fromName(name, "GENERIC_" + name, "PLAYER_" + name);
    }

    public static Collection<RegistryEntry<EntityAttribute>> getAll() {
        return java.util.List.copyOf(new java.util.LinkedHashSet<>(BY_ID.values()));
    }

    private static RegistryEntry<EntityAttribute> register(String name, String path) {
        RegistryEntry<EntityAttribute> entry = Registries.ATTRIBUTE.getEntry(Identifier.of("minecraft", path))
                .orElseThrow(() -> new IllegalStateException("Missing vanilla attribute minecraft:" + path));
        BY_ID.put(normalize(name), entry);
        BY_ID.put(normalize(path), entry);
        BY_ID.put(normalize("minecraft:" + path), entry);
        NAMES.put(entry, name);
        return entry;
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace('.', '_').replace('-', '_').replace(' ', '_');
    }

    private static Identifier id(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!value.contains(":")) {
            value = value.replace("generic_", "generic.").replace("player_", "player.");
            value = "minecraft:" + value;
        }
        return Identifier.tryParse(value);
    }
}
