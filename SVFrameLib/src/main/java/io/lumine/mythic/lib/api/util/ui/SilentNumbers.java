package io.lumine.mythic.lib.api.util.ui;

import io.lumine.mythic.lib.api.util.ToStringLambda;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fabric-native port of MythicLib 1.7.1 SilentNumbers. Pure parsing/formatting
 * methods retain the original behavior; item helpers use native ItemStack and
 * registry types instead of Bukkit Material/ItemMeta.
 */
public class SilentNumbers {
    private static final TreeMap<Integer, String> romanNumeralValues = new TreeMap<>();

    static {
        romanNumeralValues.put(1, "I");
        romanNumeralValues.put(4, "IV");
        romanNumeralValues.put(5, "V");
        romanNumeralValues.put(9, "IX");
        romanNumeralValues.put(10, "X");
        romanNumeralValues.put(40, "XL");
        romanNumeralValues.put(50, "L");
        romanNumeralValues.put(90, "XC");
        romanNumeralValues.put(100, "C");
        romanNumeralValues.put(400, "CD");
        romanNumeralValues.put(500, "D");
        romanNumeralValues.put(900, "CM");
        romanNumeralValues.put(1000, "M");
    }

    public static boolean BooleanTryParse(String value) {
        return value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"));
    }

    public static boolean DoubleTryParse(String value) { return DoubleParse(value) != null; }
    public static boolean IntTryParse(String value) { return IntegerParse(value) != null; }

    public static Boolean BooleanParse(String value) {
        if (value == null) return null;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        return null;
    }

    public static Double DoubleParse(String value) {
        if (value == null) return null;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    public static Integer IntegerParse(String value) {
        if (value == null) return null;
        try { return Integer.parseInt(removeDecimalZeros(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    public static double round(double value, int places) {
        long rounded = Math.round(value * Math.pow(10d, places));
        return rounded / Math.pow(10d, places);
    }

    public static int round(double value) { return (int) Math.round(value); }
    public static double floor(double value, int places) { return Math.floor(value * Math.pow(10d, places)) / Math.pow(10d, places); }
    public static int floor(double value) { return (int) Math.floor(value); }
    public static double ceil(double value, int places) { return Math.ceil(value * Math.pow(10d, places)) / Math.pow(10d, places); }
    public static int ceil(double value) { return (int) Math.ceil(value); }
    public static int IntegerParse(boolean value) { return value ? 1 : 0; }
    public static boolean BooleanParse(int value) { return value != 0; }
    public static boolean BooleanParse(double value) { return value != 0d; }
    public static boolean rollSuccess(double chance) { return Math.random() <= chance; }
    public static boolean rollSuccessPercent(double chance) { return Math.random() <= chance / 100d; }
    public static double randomRange(double min, double max) { return Math.random() * (max - min) + min; }

    public static String removeDecimalZeros(String value) {
        if (value == null || !value.contains(".")) return value;
        String decimal = value.substring(value.lastIndexOf('.'));
        int lastNonZero = -1;
        for (int i = 1; i < decimal.length(); i++) if (decimal.charAt(i) != '0') lastNonZero = i;
        return value.substring(0, value.lastIndexOf('.') + lastNonZero + 1);
    }

    public static String readableRounding(double value, int places) {
        return removeDecimalZeros(String.valueOf(round(value, places)));
    }

    /** Retains the original heuristic time-unit selection. Input is seconds. */
    public static String nicestTimeValueFrom(double seconds) {
        if (seconds > 60d) {
            if (seconds > 1800d) {
                double halfHours = seconds / 1800d;
                double diff = Math.round(halfHours) - halfHours;
                if (diff < .34d) return readableRounding(round(seconds / 3600d, 1), 1) + "h";
                if (seconds > 60000d) return readableRounding(round(seconds / 3600d, 2), 1) + "h";
            }
            double halfMinutes = seconds / 30d;
            double diff = Math.round(halfMinutes) - halfMinutes;
            if (diff < .34d) return readableRounding(round(seconds / 60d, 1), 1) + "m";
            if (seconds > 1000d) return readableRounding(round(seconds / 60d, 2), 1) + "m";
        }
        return readableRounding(round(seconds, 1), 1) + "s";
    }

    public static UUID UUIDParse(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) return null;
        return UUID.fromString(value);
    }

    public static ArrayList<String> chop(String input, int maxLength, String prefix) {
        ArrayList<String> result = new ArrayList<>();
        boolean chopped = false;
        while (input.length() > maxLength) {
            chopped = true;
            int split = input.lastIndexOf(" ", maxLength + 1);
            if (split <= 0) split = Math.min(maxLength, input.length());
            result.add(prefix + input.substring(0, split));
            input = input.substring(Math.min(input.length(), split + 1));
            if (input.length() <= maxLength) result.add(prefix + input);
        }
        if (!chopped) result.add(prefix + input);
        return result;
    }

    public static ArrayList<String> smartFilter(ArrayList<String> values, String query, boolean ignoreCase) {
        ArrayList<String> starts = new ArrayList<>(), contains = new ArrayList<>();
        if (ignoreCase) query = query.toLowerCase(Locale.ROOT);
        for (String original : values) {
            String value = ignoreCase ? original.toLowerCase(Locale.ROOT) : original;
            if (value.startsWith(query)) starts.add(value);
            else if (value.contains(query)) contains.add(value);
        }
        starts.addAll(contains);
        return starts;
    }

    @SafeVarargs
    public static <S> ArrayList<S> toArrayList(S... values) {
        ArrayList<S> result = new ArrayList<>();
        if (values != null) for (S value : values) if (value != null) result.add(value);
        return result;
    }

    @SafeVarargs
    public static <S> ArrayList<S> addAll(ArrayList<S> list, S... values) {
        ArrayList<S> result = list == null ? new ArrayList<>() : list;
        if (values != null) Arrays.stream(values).filter(java.util.Objects::nonNull).forEach(result::add);
        return result;
    }

    public static String toRomanNumerals(int value) {
        if (value == 0) return "0";
        if (value < 0) return "-" + toRomanNumerals(-value);
        int key = romanNumeralValues.floorKey(value);
        if (value == key) return romanNumeralValues.get(key);
        return romanNumeralValues.get(key) + toRomanNumerals(value - key);
    }

    public static <S> boolean hasAll(List<S> first, List<S> second) {
        for (S value : second) if (!first.contains(value)) return false;
        return true;
    }

    public static String getItemName(ItemStack item, boolean includeAmount) {
        if (item == null) return "null";
        String prefix = includeAmount ? item.getCount() + "x " : "";
        return prefix + item.getName().getString();
    }

    public static String getItemName(ItemStack item) { return getItemName(item, true); }

    public static String findItemName(ItemStack item) {
        if (item == null || !item.contains(DataComponentTypes.CUSTOM_NAME)) return null;
        Text custom = item.get(DataComponentTypes.CUSTOM_NAME);
        return custom == null ? null : custom.getString();
    }

    public static ItemStack setItemName(ItemStack item, String name) {
        if (item == null) return null;
        item.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return item;
    }

    public static String titleCaseConversion(String input) {
        if (input == null || input.isEmpty()) return "";
        if (input.length() == 1) return input.toUpperCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(input.length());
        for (String part : input.split(" ")) {
            if (part.length() > 1) result.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1).toLowerCase(Locale.ROOT));
            else result.append(part.toUpperCase(Locale.ROOT));
            result.append(' ');
        }
        return result.toString().trim();
    }

    public static String collapseList(ArrayList<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String value : values) {
            if (value == null) value = "null";
            if (!first) result.append(separator);
            first = false;
            result.append(value);
        }
        return result.toString();
    }

    public static ArrayList<String> transcribeList(List<?> values, ToStringLambda conversion) {
        ArrayList<String> result = new ArrayList<>();
        for (Object value : values) result.add(conversion.rewrite(value));
        return result;
    }

    public static ArrayList<String> split(String input, String separator) {
        if (!input.contains(separator)) return toArrayList(input);
        return toArrayList(input.split(Pattern.quote(separator)));
    }

    public static boolean isAir(ItemStack item) { return item == null || item.isEmpty(); }

    public static String unwrapFromCurlyBrackets(String input) {
        if (input.endsWith("}")) input = input.substring(0, input.length() - 1);
        if (input.startsWith("{")) input = input.substring(1);
        return input;
    }

    public static Integer integerFromBracketsTab(String input, String key) { return IntegerParse(valueFromBracketsTab(input, key)); }
    public static QuickNumberRange rangeFromBracketsTab(String input, String key) { return QuickNumberRange.getFromString(valueFromBracketsTab(input, key)); }

    public static String valueFromBracketsTab(String input, String key) {
        int index = input.indexOf(key + "=");
        if (index < 0) return null;
        String tail = input.substring(index + key.length() + 1);
        int curly = tail.startsWith("{") ? tail.indexOf('}') : -1;
        if (curly == -1) curly = 0;
        int comma = tail.indexOf(',', curly);
        int close = tail.indexOf(']');
        int end = -1;
        if (comma > 0) end = comma;
        if (close > 0 && (end <= 0 || close < end)) end = close;
        return end == -1 ? tail : tail.substring(0, end);
    }

    /** Native item-registry equivalent of Bukkit Material parsing. */
    public static Item getMaterial(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        Identifier id = Identifier.tryParse(normalized.contains(":") ? normalized : "minecraft:" + normalized);
        if (id == null || !Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }
}
