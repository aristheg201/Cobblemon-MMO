package vn.svframe.svframemmo.manager;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.config.DefaultFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native configurable item-template registry backed by items.yml. */
public final class ConfigItemManager {
    public static final String ITEM_ID_TAG = "SVFrameMMOItemId";
    private static final ConfigItemManager INSTANCE = new ConfigItemManager();
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-ConfigItems");

    private final Path file = DefaultFiles.ROOT.resolve("items.yml");
    private volatile Map<String, ConfigItem> items = Map.of();

    private ConfigItemManager() { }

    public static ConfigItemManager instance() { return INSTANCE; }

    public synchronized void reload() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Missing item templates: " + file);
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        LinkedHashMap<String, ConfigItem> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            try {
                if (!(entry.getValue() instanceof Map<?, ?> raw))
                    throw new IllegalArgumentException("entry is not a configuration section");
                ConfigItem item = ConfigItem.parse(entry.getKey(), stringMap(raw));
                if (parsed.putIfAbsent(item.id(), item) != null)
                    throw new IllegalArgumentException("duplicate normalized item id '" + item.id() + "'");
            } catch (RuntimeException | IOException exception) {
                LOG.log(Level.WARNING, "Could not load config item " + entry.getKey(), exception);
            }
        }
        items = Map.copyOf(parsed);
    }

    public ConfigItem get(String id) {
        return items.get(normalizeId(id));
    }

    public ConfigItem getOrThrow(String id) {
        ConfigItem item = get(id);
        if (item == null) throw new IllegalArgumentException("Unknown config item '" + id + "'");
        return item;
    }

    public Collection<ConfigItem> getAll() { return List.copyOf(items.values()); }

    public ItemStack build(String id) { return build(id, Map.of()); }

    public ItemStack build(String id, Map<String, ?> placeholders) {
        ConfigItem config = getOrThrow(id);
        ItemStack stack = new ItemStack(config.item());
        stack.set(DataComponentTypes.CUSTOM_NAME, legacyText(apply(config.name(), placeholders)));

        ArrayList<Text> lines = new ArrayList<>(config.lore().size());
        for (String line : config.lore()) lines.add(legacyText(apply(line, placeholders)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.copyOf(lines)));
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(config.customModelData()));
        if (stack.isDamageable())
            stack.set(DataComponentTypes.DAMAGE, Math.min(config.damage(), Math.max(0, stack.getMaxDamage() - 1)));
        if (config.unbreakable())
            stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        if (config.texture() != null && !config.texture().isBlank() && stack.isOf(Items.PLAYER_HEAD))
            applyHeadTexture(stack, config.texture());

        NbtCompound nbt = custom(stack);
        nbt.putString(ITEM_ID_TAG, config.id());
        setCustom(stack, nbt);
        return stack;
    }

    public ItemStack buildCurrency(String id, int worth) {
        if (worth < 1) throw new IllegalArgumentException("Currency worth must be positive");
        ItemStack stack = build(id, Map.of("worth", worth));
        NbtCompound nbt = custom(stack);
        nbt.putInt("RpgWorth", worth);
        setCustom(stack, nbt);
        return stack;
    }

    private static void applyHeadTexture(ItemStack stack, String texture) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("svframemmo:" + texture).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "SVFrameMMO");
        profile.getProperties().put("textures", new Property("textures", texture));
        stack.set(DataComponentTypes.PROFILE, new ProfileComponent(profile));
    }

    private static NbtCompound custom(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? new NbtCompound() : component.copyNbt();
    }

    private static void setCustom(ItemStack stack, NbtCompound nbt) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static String apply(String input, Map<String, ?> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, ?> entry : placeholders.entrySet())
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        return result;
    }

    /** Parses legacy ampersand/section color codes into native Text formatting. */
    private static Text legacyText(String input) {
        MutableText root = Text.empty();
        ArrayList<Formatting> active = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if ((current == '&' || current == '§') && index + 1 < input.length()) {
                Formatting formatting = Formatting.byCode(input.charAt(index + 1));
                if (formatting != null) {
                    append(root, segment, active);
                    index++;
                    if (formatting == Formatting.RESET) active.clear();
                    else if (formatting.isColor()) {
                        active.removeIf(Formatting::isColor);
                        active.removeIf(value -> value == Formatting.BOLD || value == Formatting.ITALIC || value == Formatting.UNDERLINE
                                || value == Formatting.STRIKETHROUGH || value == Formatting.OBFUSCATED);
                        active.add(formatting);
                    } else if (!active.contains(formatting)) active.add(formatting);
                    continue;
                }
            }
            segment.append(current);
        }
        append(root, segment, active);
        return root;
    }

    private static void append(MutableText root, StringBuilder segment, List<Formatting> active) {
        if (segment.isEmpty()) return;
        MutableText text = Text.literal(segment.toString());
        if (!active.isEmpty()) text.formatted(active.toArray(Formatting[]::new));
        root.append(text);
        segment.setLength(0);
    }

    private static String normalizeId(String raw) {
        return UtilityMethods.enumName(raw == null ? "" : raw);
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static List<String> strings(Object raw) {
        if (raw instanceof Collection<?> collection) {
            ArrayList<String> result = new ArrayList<>(collection.size());
            for (Object value : collection) if (value != null) result.add(String.valueOf(value));
            return List.copyOf(result);
        }
        if (raw == null || raw instanceof Map<?, ?>) return List.of();
        return List.of(String.valueOf(raw));
    }

    private static int integer(Object raw, int fallback) {
        try { return raw instanceof Number number ? number.intValue() : raw == null ? fallback : Integer.parseInt(String.valueOf(raw)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(Object raw, boolean fallback) {
        return raw instanceof Boolean value ? value : raw == null ? fallback : Boolean.parseBoolean(String.valueOf(raw));
    }

    private static Identifier itemId(Object raw) throws IOException {
        String value = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) throw new IOException("Missing item material");
        if (value.indexOf(':') < 0) value = "minecraft:" + value;
        Identifier id = Identifier.tryParse(value);
        if (id == null || !Registries.ITEM.containsId(id)) throw new IOException("Unknown item material '" + raw + "'");
        return id;
    }

    public record ConfigItem(String id, Item item, String name, List<String> lore, int damage,
                             int customModelData, boolean unbreakable, String texture) {
        private static ConfigItem parse(String key, Map<String, Object> section) throws IOException {
            String id = normalizeId(key);
            Identifier material = itemId(section.get("item"));
            String name = Objects.toString(section.get("name"), null);
            if (name == null) throw new IOException("Config item '" + id + "' has no name");
            return new ConfigItem(id, Registries.ITEM.get(material), name, strings(section.get("lore")),
                    Math.max(0, integer(section.get("damage"), 0)),
                    Math.max(0, integer(section.get("custom-model-data"), 0)),
                    bool(section.get("unbreakable"), false),
                    section.get("texture") == null ? null : String.valueOf(section.get("texture")));
        }
    }
}
