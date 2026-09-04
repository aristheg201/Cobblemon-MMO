package vn.svframe.svframemmo.gui;

import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.gui.PluginInventory;
import vn.svframe.svframelib.gui.editable.placeholder.Placeholders;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GuiSupport {
    private static final DecimalFormat ONE = new DecimalFormat("0.#");

    private GuiSupport() { }

    static boolean left(PluginInventory.Click click) {
        return click.actionType() == SlotActionType.PICKUP && click.button() == 0;
    }

    static boolean right(PluginInventory.Click click) {
        return click.actionType() == SlotActionType.PICKUP && click.button() == 1;
    }

    static boolean shiftLeft(PluginInventory.Click click) {
        return click.actionType() == SlotActionType.QUICK_MOVE && click.button() == 0;
    }

    static boolean shift(PluginInventory.Click click) {
        return click.actionType() == SlotActionType.QUICK_MOVE;
    }

    static void action(ServerPlayerEntity player, String message) {
        if (player != null) player.sendMessage(Text.literal(colors(message)), true);
    }

    static String colors(String input) {
        return SVFrameLib.inst().parseColors(input == null ? "" : input);
    }

    static String progress(double current, double next) {
        double ratio = next <= 0d ? 1d : Math.max(0d, Math.min(1d, current / next));
        StringBuilder bar = new StringBuilder("§l");
        int chars = Math.min(20, Math.max(0, (int) Math.floor(ratio * 20d)));
        for (int j = 0; j < 20; j++) {
            if (j == chars) bar.append("§f§l");
            bar.append('|');
        }
        return bar.toString();
    }

    static String percent(double current, double next) {
        return ONE.format(next <= 0d ? 100d : Math.max(0d, Math.min(100d, current / next * 100d)));
    }

    static String roman(int number) {
        if (number <= 0) return "0";
        int[] values = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] numerals = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) while (number >= values[i]) { out.append(numerals[i]); number -= values[i]; }
        return out.toString();
    }

    static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    static String string(Map<String, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    static int integer(Map<String, ?> map, String key, int fallback) {
        Object value = map.get(key);
        try { return value instanceof Number n ? n.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    static boolean bool(Map<String, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean b ? b : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    static List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return value == null ? List.of() : List.of(String.valueOf(value));
        ArrayList<String> out = new ArrayList<>();
        for (Object element : iterable) if (element != null) out.add(String.valueOf(element));
        return out;
    }

    static String normalizeId(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    static Placeholders placeholders(Object... pairs) {
        Placeholders result = new Placeholders();
        for (int i = 0; i + 1 < pairs.length; i += 2) result.register(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
