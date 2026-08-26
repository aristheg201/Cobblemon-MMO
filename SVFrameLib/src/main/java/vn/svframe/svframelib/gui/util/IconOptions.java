package vn.svframe.svframelib.gui.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import vn.svframe.svframelib.version.VersionUtils;

import java.util.*;

/** Native 1.21.1 equivalent of MythicLib 1.7.1 IconOptions. */
public class IconOptions {
    public static final IconOptions EMPTY = new IconOptions();

    private final Item material;
    private final Integer customModelDataInt;
    private final String customModelDataString;
    private final Float customModelDataFloat;
    private final Identifier itemModel;
    private final String skullTexture;
    private final String tooltipStyle;
    private final String[] itemFlags;
    private final Boolean hideTooltip;
    private final Boolean fakeAttribute;
    private final Boolean unbreakable;

    public IconOptions() { this(null, (Integer) null); }
    public IconOptions(Item material) { this(material, (Integer) null); }
    public IconOptions(Item material, Integer model) {
        this(material, model, null, null, null, null, null, null, null, null, null);
    }
    public IconOptions(Item material, String model) {
        this(material, null, model, null, null, null, null, null, null, null, null);
    }

    public IconOptions(Item material, Integer customModelDataInt, String customModelDataString,
                       Float customModelDataFloat, Identifier itemModel, String skullTexture,
                       String tooltipStyle, String[] itemFlags, Boolean hideTooltip,
                       Boolean fakeAttribute, Boolean unbreakable) {
        this.material = material;
        this.customModelDataInt = customModelDataInt;
        this.customModelDataString = customModelDataString;
        this.customModelDataFloat = customModelDataFloat;
        this.itemModel = itemModel;
        this.skullTexture = skullTexture;
        this.tooltipStyle = tooltipStyle;
        this.itemFlags = itemFlags == null ? null : itemFlags.clone();
        this.hideTooltip = hideTooltip;
        this.fakeAttribute = fakeAttribute;
        this.unbreakable = unbreakable;
    }

    public Item getMaterialElse(Item fallback) { return material == null ? fallback : material; }
    public Item getMaterial() { return material; }
    public Integer getCustomModelDataInt() { return customModelDataInt; }
    public String getCustomModelDataString() { return customModelDataString; }
    public Float getCustomModelDataFloat() { return customModelDataFloat; }
    public String getItemModel() { return itemModel == null ? null : itemModel.toString(); }
    public Identifier getItemModelIdentifier() { return itemModel; }
    public String getSkullTexture() { return skullTexture; }
    public String getTooltipStyle() { return tooltipStyle; }
    public String[] getItemFlags() { return itemFlags == null ? null : itemFlags.clone(); }
    public Boolean getHideTooltip() { return hideTooltip; }
    public Boolean getFakeAttribute() { return fakeAttribute; }
    public Boolean getUnbreakable() { return unbreakable; }

    /**
     * Applies every option that exists in Minecraft 1.21.1. String/float CMD,
     * item-model and tooltip-style are retained in the API/config object because
     * upstream 1.7.1 only applies them on versions newer than the target runtime.
     */
    public ItemStack applyToItemStack(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (customModelDataInt != null)
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(customModelDataInt));

        if (skullTexture != null && !skullTexture.isBlank() && stack.isOf(Items.PLAYER_HEAD)) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "svframelib");
            profile.getProperties().put("textures", new Property("textures", skullTexture));
            stack.set(DataComponentTypes.PROFILE, new ProfileComponent(profile));
        }

        boolean hideUnbreakable = hasFlag("HIDE_UNBREAKABLE");
        if (unbreakable != null) {
            if (unbreakable) stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(!hideUnbreakable));
            else stack.remove(DataComponentTypes.UNBREAKABLE);
        } else if (hideUnbreakable) {
            UnbreakableComponent current = stack.get(DataComponentTypes.UNBREAKABLE);
            if (current != null) stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        }

        if (hasFlag("HIDE_ATTRIBUTES")) {
            AttributeModifiersComponent current = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
            stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, current.withShowInTooltip(false));
        }
        if (itemFlags != null && Arrays.stream(itemFlags).anyMatch(this::isAdditionalTooltipFlag))
            stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);

        if (hideTooltip != null) {
            if (hideTooltip) stack.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE);
            else stack.remove(DataComponentTypes.HIDE_TOOLTIP);
        }
        if (Boolean.TRUE.equals(fakeAttribute)) VersionUtils.addEmptyAttributeModifier(stack);
        return stack;
    }

    public ItemStack applyToItemMeta(ItemStack stack) { return applyToItemStack(stack); }

    /** Upstream defaults a missing material to BARRIER. */
    public ItemStack toItemStack() { return applyToItemStack(new ItemStack(getMaterialElse(Items.BARRIER))); }

    /** Upstream combine keeps values from this instance and only falls back to the supplied one. */
    public IconOptions combine(IconOptions other) {
        if (other == null) return this;
        return new IconOptions(
                fallback(material, other.material),
                fallback(customModelDataInt, other.customModelDataInt),
                fallback(customModelDataString, other.customModelDataString),
                fallback(customModelDataFloat, other.customModelDataFloat),
                fallback(itemModel, other.itemModel),
                fallback(skullTexture, other.skullTexture),
                fallback(tooltipStyle, other.tooltipStyle),
                fallback(itemFlags, other.itemFlags),
                fallback(hideTooltip, other.hideTooltip),
                fallback(fakeAttribute, other.fakeAttribute),
                fallback(unbreakable, other.unbreakable));
    }

    private static <T> T fallback(T primary, T secondary) { return primary != null ? primary : secondary; }

    public static IconOptions from(Object value) {
        if (value instanceof IconOptions options) return options;
        if (value instanceof ItemStack stack) return from(stack);
        if (value instanceof Item item) return new IconOptions(item);
        if (value instanceof String string) {
            String[] split = string.split("[:.,]");
            if (split.length == 0 || split[0].isBlank()) throw new IllegalArgumentException("Could not read icon");
            Item item = parseItem(split[0]);
            int model = split.length == 1 ? 0 : Integer.parseInt(split[1]);
            return new IconOptions(item, model);
        }
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = normalized(raw);
            Item item = parseItem(first(map, "item", "material"));
            Integer cmdInt = integer(first(map, "custom_model_data", "custom-model-data", "model-data", "cmd", "model_data"));
            String cmdString = string(first(map, "custom_model_data_string", "custom-model-data-string", "cmd-string", "cmd_string", "cmds"));
            Float cmdFloat = decimal(first(map, "custom_model_data_float", "custom-model-data-float", "cmd-float", "cmd_float", "cmdf"));
            Identifier model = identifier(string(first(map, "model", "item_model", "item-model")));
            String texture = string(first(map, "texture", "skull_texture", "skull-texture"));
            String tooltip = string(first(map, "tooltip", "tooltip_style", "tooltip-style"));
            String[] flags = bool(first(map, "hide-flags", "hide_flags"), false)
                    ? allItemFlags() : stringArray(first(map, "item_flags", "item-flags"));
            Boolean hideTooltip = boolObject(first(map, "hide_tooltip", "hide-tooltip"));
            Boolean fakeAttribute = boolObject(first(map, "fake_attribute_modifier", "fake-attribute-modifier"));
            Boolean unbreakable = boolObject(first(map, "unbreakable"));
            return new IconOptions(item, cmdInt, cmdString, cmdFloat, model, texture, tooltip, flags, hideTooltip, fakeAttribute, unbreakable);
        }
        if (value == null) return EMPTY;
        throw new IllegalArgumentException("Could not read icon");
    }

    public static IconOptions from(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return EMPTY;
        CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        String texture = null;
        ProfileComponent profile = stack.get(DataComponentTypes.PROFILE);
        if (profile != null) {
            Collection<Property> textures = profile.properties().get("textures");
            if (textures != null && !textures.isEmpty()) texture = textures.iterator().next().value();
        }
        Boolean hide = stack.contains(DataComponentTypes.HIDE_TOOLTIP) ? Boolean.TRUE : null;
        UnbreakableComponent unbreak = stack.get(DataComponentTypes.UNBREAKABLE);
        return new IconOptions(stack.getItem(), cmd == null ? null : cmd.value(), null, null, null,
                texture, null, null, hide, null, unbreak == null ? null : Boolean.TRUE);
    }

    private boolean hasFlag(String id) {
        if (itemFlags == null) return false;
        for (String flag : itemFlags) if (id.equalsIgnoreCase(normalize(flag))) return true;
        return false;
    }

    private boolean isAdditionalTooltipFlag(String raw) {
        String id = normalize(raw);
        return !id.equals("HIDE_ATTRIBUTES") && !id.equals("HIDE_UNBREAKABLE") && !id.equals("HIDE_ENCHANTS");
    }

    private static Map<String, Object> normalized(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, val) -> result.put(String.valueOf(key).toLowerCase(Locale.ROOT), val));
        return result;
    }

    private static Object first(Map<String, Object> map, String... keys) {
        for (String key : keys) if (map.containsKey(key)) return map.get(key);
        return null;
    }

    private static Item parseItem(Object value) {
        if (value instanceof Item item) return item;
        if (value == null) return null;
        String raw = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        if (id == null || !Registries.ITEM.containsId(id)) throw new IllegalArgumentException("No item with ID '" + value + "'");
        return Registries.ITEM.get(id);
    }

    private static Identifier identifier(String value) {
        if (value == null || value.isBlank()) return null;
        Identifier id = Identifier.tryParse(value.contains(":") ? value : "minecraft:" + value);
        if (id == null) throw new IllegalArgumentException("Invalid identifier: " + value);
        return id;
    }

    private static Integer integer(Object value) {
        try { return value instanceof Number n ? n.intValue() : value == null ? null : Integer.valueOf(String.valueOf(value)); }
        catch (RuntimeException ignored) { return null; }
    }
    private static Float decimal(Object value) {
        try { return value instanceof Number n ? n.floatValue() : value == null ? null : Float.valueOf(String.valueOf(value)); }
        catch (RuntimeException ignored) { return null; }
    }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static Boolean boolObject(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        String s = String.valueOf(value).trim();
        if (s.equalsIgnoreCase("true")) return true;
        if (s.equalsIgnoreCase("false")) return false;
        return null;
    }
    private static boolean bool(Object value, boolean fallback) { Boolean b = boolObject(value); return b == null ? fallback : b; }
    private static String[] stringArray(Object value) {
        if (value == null) return null;
        if (value instanceof Collection<?> c) return c.stream().map(String::valueOf).map(IconOptions::normalize).toArray(String[]::new);
        return Arrays.stream(String.valueOf(value).split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).map(IconOptions::normalize).toArray(String[]::new);
    }
    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
    private static String[] allItemFlags() {
        return new String[]{"HIDE_ENCHANTS","HIDE_ATTRIBUTES","HIDE_UNBREAKABLE","HIDE_DESTROYS","HIDE_PLACED_ON","HIDE_ADDITIONAL_TOOLTIP","HIDE_DYE","HIDE_ARMOR_TRIM"};
    }

    @Override
    public String toString() {
        return "IconOptions{material=" + (material == null ? null : Registries.ITEM.getId(material))
                + ", customModelDataInt=" + customModelDataInt
                + ", customModelDataString=" + customModelDataString
                + ", customModelDataFloat=" + customModelDataFloat
                + ", itemModel=" + itemModel
                + ", skullTexture=" + skullTexture + '}';
    }
}
