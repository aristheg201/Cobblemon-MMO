package vn.svframe.svframemmo.experience.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Parsed MMOCore line-config experience source with native Fabric-side matching semantics. */
public final class ExperienceSourceDefinition {
    private final String type;
    private final Map<String, String> arguments;
    private final RandomAmount amount;

    private ExperienceSourceDefinition(String type, Map<String, String> arguments) {
        this.type = ExperienceSignal.normalize(type);
        this.arguments = Map.copyOf(arguments);
        this.amount = RandomAmount.parse(arguments.get("amount"));
    }

    public static ExperienceSourceDefinition parse(String input) {
        Objects.requireNonNull(input, "input");
        String line = input.trim();
        if (line.isEmpty()) throw new IllegalArgumentException("Empty experience source");
        int open = line.indexOf('{');
        if (open < 0) return new ExperienceSourceDefinition(line, Map.of());
        int close = line.lastIndexOf('}');
        if (close < open) throw new IllegalArgumentException("Malformed experience source: " + input);
        String type = line.substring(0, open).trim();
        if (type.isEmpty()) throw new IllegalArgumentException("Missing experience source type: " + input);
        return new ExperienceSourceDefinition(type, parseArguments(line.substring(open + 1, close)));
    }

    public String type() { return type; }
    public Map<String, String> arguments() { return arguments; }
    public boolean isFrom() { return type.equals("from"); }
    public String referencedSource() { return arguments.getOrDefault("source", "").trim(); }

    public double experience(ExperienceSignal signal) {
        return amount.roll() * Math.max(0d, signal.units());
    }

    public boolean matches(ExperienceSignal signal) {
        if (!type.equals(signal.type())) return false;
        if (type.equals("mineblock")) {
            if (!matchesPrimary(signal)) return false;
            if (bool("crop", false) && !signal.flag("crop-mature")) return false;
            if (signal.flag("player-placed") && !bool("player-placed", false)) return false;
            if (signal.flag("silk-touch") && bool("silk-touch", true)) return false;
            return true;
        }
        if (type.equals("placeblock") || type.equals("craftitem") || type.equals("smeltitem")
                || type.equals("eat") || type.equals("tame") || type.equals("climb")
                || type.equals("ride") || type.equals("projectile") || type.equals("repairitem"))
            return matchesPrimaryOrAny(signal);
        if (type.equals("killmob")) {
            if (!matchesPrimary(signal)) return false;
            String name = arguments.get("name");
            return name == null || name.equals(signal.text("name"));
        }
        if (type.equals("damagedealt")) {
            String damageType = arguments.get("type");
            return damageType == null || damageType.isBlank() || signal.hasTag(damageType);
        }
        if (type.equals("damagetaken")) {
            String cause = arguments.get("type");
            return cause == null || cause.isBlank() || canonical(cause).equals(canonical(signal.primary())) || signal.hasTag(cause);
        }
        if (type.equals("enchantitem")) {
            String filter = arguments.get("enchant");
            if (filter == null || filter.isBlank()) return true;
            for (String enchant : splitComma(filter)) if (signal.hasTag(enchant)) return true;
            return false;
        }
        if (type.equals("fishitem")) return matchesPrimaryOrAny(signal);
        if (type.equals("brewpotion")) {
            String effects = arguments.get("effect");
            if (effects == null || effects.isBlank()) return true;
            for (String effect : splitComma(effects)) if (canonical(effect).equals(canonical(signal.primary()))) return true;
            return false;
        }
        if (type.equals("move")) return matchesPrimaryOrAny(signal);
        if (type.equals("resource")) {
            String resource = arguments.get("type");
            return resource == null || resource.isBlank() || canonical(resource).equals(canonical(signal.primary()));
        }
        if (type.equals("play")) {
            if (bool("in-combat", false) && !signal.flag("in-combat")) return false;
            String world = arguments.get("world");
            if (world != null && !world.isBlank() && !world.equalsIgnoreCase(signal.text("world"))) return false;
            double x = signal.number("x", 0d), z = signal.number("z", 0d);
            if (arguments.containsKey("x1") && arguments.containsKey("x2")) {
                double a = number("x1", Double.NEGATIVE_INFINITY), b = number("x2", Double.POSITIVE_INFINITY);
                if (!(x > Math.min(a, b) && x < Math.max(a, b))) return false;
            }
            if (arguments.containsKey("z1") && arguments.containsKey("z2")) {
                double a = number("z1", Double.NEGATIVE_INFINITY), b = number("z2", Double.POSITIVE_INFINITY);
                if (!(z > Math.min(a, b) && z < Math.max(a, b))) return false;
            }
            return true;
        }
        return false;
    }

    private boolean matchesPrimaryOrAny(ExperienceSignal signal) {
        String expected = arguments.get("type");
        return expected == null || expected.isBlank() || canonical(expected).equals(canonical(signal.primary()));
    }

    private boolean matchesPrimary(ExperienceSignal signal) {
        String expected = arguments.get("type");
        return expected != null && !expected.isBlank() && canonical(expected).equals(canonical(signal.primary()));
    }

    private boolean bool(String key, boolean fallback) {
        String value = arguments.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private double number(String key, double fallback) {
        try { return Double.parseDouble(arguments.get(key)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String canonical(String id) {
        if (id == null) return "";
        String value = id.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!value.contains(":")) return value;
        String namespace = value.substring(0, value.indexOf(':'));
        String path = value.substring(value.indexOf(':') + 1);
        return "MINECRAFT".equals(namespace) ? path : namespace + ':' + path;
    }

    private static List<String> splitComma(String raw) {
        ArrayList<String> result = new ArrayList<>();
        for (String entry : raw.split(",")) if (!entry.isBlank()) result.add(entry.trim());
        return result;
    }

    private static Map<String, String> parseArguments(String body) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String token : split(body, ';')) {
            if (token.isBlank()) continue;
            int equals = indexOfUnquoted(token, '=');
            if (equals < 0) {
                result.put(token.trim().toLowerCase(Locale.ROOT), "true");
                continue;
            }
            String key = token.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = unquote(token.substring(equals + 1).trim());
            result.put(key, value);
        }
        return result;
    }

    private static List<String> split(String input, char delimiter) {
        ArrayList<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '\'' || c == '"') && (i == 0 || input.charAt(i - 1) != '\\')) {
                if (quote == 0) quote = c; else if (quote == c) quote = 0;
            }
            if (c == delimiter && quote == 0) {
                result.add(current.toString()); current.setLength(0);
            } else current.append(c);
        }
        result.add(current.toString());
        return result;
    }

    private static int indexOfUnquoted(String input, char needle) {
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '\'' || c == '"') && (i == 0 || input.charAt(i - 1) != '\\')) {
                if (quote == 0) quote = c; else if (quote == c) quote = 0;
            } else if (c == needle && quote == 0) return i;
        }
        return -1;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
                || (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'')))
            return value.substring(1, value.length() - 1);
        return value;
    }

    private record RandomAmount(double min, double max) {
        private static RandomAmount parse(String raw) {
            if (raw == null || raw.isBlank()) return new RandomAmount(1d, 1d);
            String value = unquote(raw.trim());
            int separator = value.indexOf('-', 1);
            try {
                if (separator > 0) {
                    double a = Double.parseDouble(value.substring(0, separator).trim());
                    double b = Double.parseDouble(value.substring(separator + 1).trim());
                    return new RandomAmount(Math.min(a, b), Math.max(a, b));
                }
                double exact = Double.parseDouble(value);
                return new RandomAmount(exact, exact);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid experience amount: " + raw, exception);
            }
        }

        private double roll() {
            return min == max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
        }
    }
}
