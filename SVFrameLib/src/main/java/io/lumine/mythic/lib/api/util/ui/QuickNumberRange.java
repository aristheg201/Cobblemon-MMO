package io.lumine.mythic.lib.api.util.ui;

public class QuickNumberRange {
    private final double min, max;

    public QuickNumberRange(double value) { this(value, value); }
    public QuickNumberRange(double min, double max) { this.min = Math.min(min, max); this.max = Math.max(min, max); }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public boolean inRange(double value) { return value >= min && value <= max; }

    public static QuickNumberRange fromString(String input) {
        if (input == null || input.isBlank()) return new QuickNumberRange(1);
        String value = input.trim();
        int split = value.indexOf("..");
        int separatorLength = 2;
        if (split < 0) { split = value.indexOf('-'); separatorLength = 1; }
        try {
            return split > 0
                    ? new QuickNumberRange(Double.parseDouble(value.substring(0, split).trim()), Double.parseDouble(value.substring(split + separatorLength).trim()))
                    : new QuickNumberRange(Double.parseDouble(value));
        } catch (RuntimeException ignored) {
            return new QuickNumberRange(1);
        }
    }

    /** MythicLib 1.7.1 public factory name retained for binary/source compatibility. */
    public static QuickNumberRange getFromString(String input) { return fromString(input); }

    @Override public String toString() { return min == max ? Double.toString(min) : min + ".." + max; }
}
