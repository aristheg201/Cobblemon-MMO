package vn.svframe.svframelib.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class AdventureUtils {
    private AdventureUtils() {}

    public static Optional<Formatting> getByName(String name) {
        if (name == null) return Optional.empty();
        return Arrays.stream(Formatting.values())
                .filter(Formatting::isColor)
                .filter(f -> f.getName().equalsIgnoreCase(name) || f.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public static Optional<net.minecraft.text.TextColor> getByHex(String hex) {
        if (hex == null) return Optional.empty();
        String s = hex.trim();
        if (s.length() == 7 && s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return Optional.empty();
        try { return Optional.of(net.minecraft.text.TextColor.fromRgb(Integer.parseInt(s, 16) & 0xFFFFFF)); }
        catch (RuntimeException ignored) { return Optional.empty(); }
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) { return CompletableFuture.runAsync(runnable); }
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) { return CompletableFuture.supplyAsync(supplier); }

    /** Equivalent to Bungee ChatColor.of(raw).getColor() for MythicLib's accepted named/hex colors. */
    public static Color color(String raw) {
        if (raw == null) return Color.WHITE;
        String s = raw.trim();
        if (s.startsWith("#")) {
            Optional<net.minecraft.text.TextColor> parsed = getByHex(s);
            if (parsed.isPresent()) return new Color(parsed.get().getRgb());
            return Color.WHITE;
        }
        Integer rgb = switch (s.toLowerCase(Locale.ROOT)) {
            case "black" -> 0x000000; case "dark_blue" -> 0x0000AA; case "dark_green" -> 0x00AA00;
            case "dark_aqua" -> 0x00AAAA; case "dark_red" -> 0xAA0000; case "dark_purple" -> 0xAA00AA;
            case "gold" -> 0xFFAA00; case "gray", "grey" -> 0xAAAAAA; case "dark_gray", "dark_grey" -> 0x555555;
            case "blue" -> 0x5555FF; case "green" -> 0x55FF55; case "aqua" -> 0x55FFFF; case "red" -> 0xFF5555;
            case "light_purple" -> 0xFF55FF; case "yellow" -> 0xFFFF55; case "white" -> 0xFFFFFF; default -> null;
        };
        return rgb == null ? Color.WHITE : new Color(rgb);
    }

    public static ItemStack setDisplayName(ItemStack item, String name) {
        item.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name == null ? "" : name.replace('&', '§')));
        return item;
    }
    public static ItemStack setLore(ItemStack item, List<String> lore) {
        List<Text> parsed = new ArrayList<>();
        if (lore != null) for (String line : lore) parsed.add(Text.literal(line.replace('&', '§')));
        item.set(DataComponentTypes.LORE, new LoreComponent(parsed));
        return item;
    }
}
