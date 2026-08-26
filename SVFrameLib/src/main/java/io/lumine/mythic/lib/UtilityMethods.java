package io.lumine.mythic.lib;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Platform-neutral utility surface used by the native Fabric port. */
public final class UtilityMethods {
    private UtilityMethods() {}

    public static <T> T getLast(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public static Runnable emptyRunnable() { return () -> {}; }

    public static <T> T prettyValueOf(Function<String,T> evaluator, String rawInput, String errorMessage) {
        try { return evaluator.apply(enumName(rawInput)); }
        catch (Throwable throwable) { throw new RuntimeException(String.format(errorMessage, rawInput), throwable); }
    }

    public static UUID uniqueIdFromString(String input) {
        return UUID.nameUUIDFromBytes(String.valueOf(input).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String enumName(String input) {
        if (input == null) return "";
        return input.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    public static String caseOnWords(String input) {
        if (input == null || input.isBlank()) return "";
        String[] split = input.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : split) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static <T extends Enum<?>> String kebabCase(T value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String kebabCase(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    public static String substringBetween(String input, String open, String close) {
        if (input == null || open == null || close == null) return null;
        int start = input.indexOf(open);
        if (start < 0) return null;
        start += open.length();
        int end = input.indexOf(close, start);
        return end < 0 ? null : input.substring(start, end);
    }

    public static <T> Consumer<T> dummyConsume() { return ignored -> {}; }
}
