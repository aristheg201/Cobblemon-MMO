package vn.svframe.compat;

import vn.svframe.mythiclibfabric.NativeDefaultSkillRuntime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CI smoke which prevents truncating or silently leaving bundled default skills without a native executor. */
public final class YamlSkillCountSmoke {
    private YamlSkillCountSmoke() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: <yaml-file> <expected-top-level-keys>");
        int expected = Integer.parseInt(args[1]);
        Map<String,Object> root = YamlLite.map(YamlLite.parse(Path.of(args[0])));
        int actual = root.size();
        System.out.println("YAML_TOP_LEVEL_KEYS=" + actual);
        if (actual != expected) throw new IllegalStateException("Expected " + expected + " top-level keys, got " + actual);

        List<String> missing = new ArrayList<>();
        int defaultSources = 0;
        for (Map.Entry<String,Object> entry : root.entrySet()) {
            if (!(entry.getValue() instanceof Map<?,?> section)) continue;
            Object rawSource = section.get("source");
            if (rawSource == null) continue;
            String source = String.valueOf(rawSource).trim();
            if (source.length() >= 2 && ((source.startsWith("'") && source.endsWith("'")) || (source.startsWith("\"") && source.endsWith("\""))))
                source = source.substring(1, source.length() - 1).trim();
            int colon = source.indexOf(':');
            String provider = colon < 0 ? "default" : source.substring(0, colon).trim();
            String internal = colon < 0 ? source : source.substring(colon + 1).trim();
            if (!provider.equalsIgnoreCase("default")) continue;
            defaultSources++;
            if (!NativeDefaultSkillRuntime.supports(internal)) missing.add(entry.getKey() + " -> " + source);
        }
        System.out.println("DEFAULT_SKILL_EXECUTORS=" + defaultSources);
        if (!missing.isEmpty()) throw new IllegalStateException("Missing native default skill executors: " + missing);

        // A pure default:* corpus (the bundled default_skills.yml) must have one native executor per definition.
        // Mixed/non-default corpora such as example_skills.yml are still count-checked without incorrectly
        // comparing their entry count to the global default runtime ID set.
        if (defaultSources == actual && actual > 0 && NativeDefaultSkillRuntime.ids().size() != actual)
            throw new IllegalStateException("Native runtime exposes " + NativeDefaultSkillRuntime.ids().size() + " IDs, expected " + actual);
    }
}
