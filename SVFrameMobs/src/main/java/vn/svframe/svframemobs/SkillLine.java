package vn.svframe.svframemobs;

import vn.svframe.svframelib.api.MMOLineConfig;
import java.util.*;

public record SkillLine(String mechanic, Map<String, String> parameters, String target, double radius,
                        String trigger, int interval, double chance) {
    public static SkillLine parse(String line) {
        List<String> tokens = tokens(line);
        if (tokens.isEmpty()) throw new IllegalArgumentException("Empty skill line");
        MMOLineConfig config = new MMOLineConfig(tokens.getFirst());
        String mechanic = config.getKey().toLowerCase(Locale.ROOT);
        if (!Set.of("damage", "velocity", "potion", "message", "effect:particles", "skill").contains(mechanic)) throw new IllegalArgumentException("Unsupported mechanic " + mechanic);
        Map<String, String> parameters = new LinkedHashMap<>();
        config.asMap().forEach((k,v) -> parameters.put(k.toLowerCase(Locale.ROOT), String.valueOf(v)));
        String target = "self", trigger = "ontimer"; int interval = 20; double radius = 8, chance = 1;
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("@")) {
                MMOLineConfig targetConfig = new MMOLineConfig(token.substring(1));
                target = targetConfig.getKey().toLowerCase(Locale.ROOT);
                if (!Set.of("self", "target", "trigger", "playersinradius", "pir").contains(target)) throw new IllegalArgumentException("Unsupported targeter " + target);
                radius = targetConfig.getDouble("r", 8);
            } else if (token.startsWith("~")) {
                String[] pieces = token.substring(1).toLowerCase(Locale.ROOT).split(":", -1);
                trigger = pieces[0];
                if (!Set.of("onspawn", "ondeath", "ondamaged", "onattack", "ontimer").contains(trigger)) throw new IllegalArgumentException("Unsupported trigger " + trigger);
                if (pieces.length > 1) interval = Integer.parseInt(pieces[1]);
                if (pieces.length > 2) throw new IllegalArgumentException("Invalid timer trigger");
            } else chance = Double.parseDouble(token);
        }
        if (interval < 1 || interval > 72000 || !Double.isFinite(radius) || radius < 0 || radius > 64 || !Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Invalid skill timer, radius or chance");
        for (String key : List.of("amount", "a", "x", "y", "z", "duration", "level")) if (parameters.containsKey(key)) {
            double value = Double.parseDouble(parameters.get(key));
            if (!Double.isFinite(value) || Math.abs(value) > 100000) throw new IllegalArgumentException("Invalid mechanic parameter " + key);
        }
        return new SkillLine(mechanic, Map.copyOf(parameters), target, radius, trigger, interval, chance);
    }
    /** Split only outside braces/quotes, so mechanic and targeter parameter maps stay separate. */
    private static List<String> tokens(String line) {
        List<String> out = new ArrayList<>(); StringBuilder token = new StringBuilder(); int braces = 0; char quote = 0;
        for (char c : line.toCharArray()) {
            if (quote != 0) { if (c == quote) quote = 0; }
            else if (c == '\'' || c == '"') quote = c;
            else if (c == '{') braces++;
            else if (c == '}') { if (--braces < 0) throw new IllegalArgumentException("Unbalanced skill braces"); }
            if (Character.isWhitespace(c) && braces == 0 && quote == 0) { if (!token.isEmpty()) { out.add(token.toString()); token.setLength(0); } }
            else token.append(c);
        }
        if (braces != 0 || quote != 0) throw new IllegalArgumentException("Unbalanced skill braces/quotes");
        if (!token.isEmpty()) out.add(token.toString());
        return out;
    }
    public String value(String key, String fallback) { return parameters.getOrDefault(key, fallback); }
    public double number(String key, double fallback) { return Double.parseDouble(parameters.getOrDefault(key, String.valueOf(fallback))); }
}
