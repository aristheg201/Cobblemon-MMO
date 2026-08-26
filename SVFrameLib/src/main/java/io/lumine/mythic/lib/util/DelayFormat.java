package io.lumine.mythic.lib.util;

import io.lumine.mythic.lib.util.lang3.Validate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure-Java/Fabric port of MythicLib 1.7.1 DelayFormat. */
public class DelayFormat {
    private static final char[] DELAY_CHARACTERS = {'s', 'm', 'h', 'd', 'M', 'y'};
    private static final long[] DELAY_AMOUNTS = {1000L, 60000L, 3600000L, 86400000L, 2629746000L, 31557522240L};
    private static final int N = DELAY_AMOUNTS.length;

    private final int[] chars;
    private final String[] translation;
    private final String threshold;
    private final String each;
    private final int smallestUnit;

    public DelayFormat() {
        this.chars = new int[N];
        for (int i = 0; i < N; i++) chars[i] = i;
        this.translation = null;
        this.threshold = null;
        this.each = null;
        this.smallestUnit = 0;
    }

    public DelayFormat(Object object) {
        if (object instanceof String format) {
            this.translation = null;
            this.threshold = null;
            this.each = null;
            this.chars = new int[format.length()];
            this.smallestUnit = determineCharactersUsed(format, null);
            return;
        }

        if (object instanceof Map<?, ?> section) {
            Object rawFormat = section.get("format");
            String format = Objects.requireNonNull(rawFormat, "Could not find format").toString();
            this.chars = new int[format.length()];
            this.translation = new String[N];
            this.threshold = stringOrNull(section.get("threshold"));
            this.each = stringOrNull(section.get("each"));
            this.smallestUnit = determineCharactersUsed(format, stringOrNull(section.get("translate")));
            return;
        }

        throw new IllegalArgumentException("Provide either a config section or string");
    }

    private String[] loadTranslation(String input) {
        if (input == null) return null;
        if (input.contains("\\")) return input.split("\\\\", -1);
        if (input.contains("|")) return input.split("\\|", -1);
        String[] out = new String[input.length()];
        for (int i = 0; i < input.length(); i++) out[i] = String.valueOf(input.charAt(i));
        return out;
    }

    private int determineCharactersUsed(String input, String translate) {
        Validate.isTrue(!input.isEmpty(), "Format cannot be empty");
        String[] translated = loadTranslation(translate);
        char[] raw = input.toCharArray();
        Validate.isTrue(translated == null || raw.length == translated.length,
                "Format and translation don't have the same size");

        int smallest = N;
        for (int i = 0; i < raw.length; i++) {
            int unit = indexOf(raw[i]);
            chars[i] = unit;
            smallest = Math.min(smallest, unit);
            if (translated != null) translation[i] = translated[i];
        }
        return smallest;
    }

    private int indexOf(char token) {
        for (int i = 0; i < N; i++) if (DELAY_CHARACTERS[i] == token) return i;
        throw new IllegalArgumentException(String.format("Unknown token %s", token));
    }

    private String each(String unit, long amount) {
        return each == null ? amount + unit : String.format(each, amount, unit);
    }

    public String format(long delay) {
        if (delay <= DELAY_AMOUNTS[smallestUnit]) {
            return threshold != null ? threshold : each(String.valueOf(DELAY_CHARACTERS[smallestUnit]), 1L);
        }

        delay += DELAY_AMOUNTS[smallestUnit] - 1L;
        long[] amounts = new long[N];
        for (int i = N - 1; i >= 0; i--) {
            long unit = DELAY_AMOUNTS[i];
            amounts[i] = delay / unit;
            delay %= unit;
        }

        StringBuilder out = new StringBuilder();
        for (int unit : chars) {
            long amount = amounts[unit];
            if (amount == 0L) continue;
            String name = translation != null && unit < translation.length && translation[unit] != null
                    ? translation[unit]
                    : String.valueOf(DELAY_CHARACTERS[unit]);
            out.append(each(name, amount));
        }
        return out.toString();
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
