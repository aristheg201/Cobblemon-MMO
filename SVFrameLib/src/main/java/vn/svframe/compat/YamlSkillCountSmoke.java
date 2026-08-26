package vn.svframe.compat;

import vn.svframe.mythiclibfabric.BuiltinSkillOwnership;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CI smoke for bundled skill definition count and real MythicLib/external handler ownership. */
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
        int nativeSources = 0;
        int externalSources = 0;
        for (Map.Entry<String,Object> entry : root.entrySet()) {
            if (!(entry.getValue() instanceof Map<?,?> section)) continue;
            Object rawSource = section.get("source");
            if (rawSource == null) continue;
            String source = unquote(String.valueOf(rawSource).trim());
            int colon = source.indexOf(':');
            String provider = colon < 0 ? "default" : source.substring(0, colon).trim();
            String internal = colon < 0 ? source : source.substring(colon + 1).trim();
            if (!provider.equalsIgnoreCase("default")) continue;
            defaultSources++;
            if (BuiltinSkillOwnership.isNative(internal)) nativeSources++;
            else if (BuiltinSkillOwnership.isExternalProvider(internal)) externalSources++;
            else missing.add(entry.getKey() + " -> " + source);
        }
        System.out.println("DEFAULT_SKILL_SOURCES=" + defaultSources + ",MYTHICLIB_NATIVE=" + nativeSources + ",EXTERNAL_PROVIDER=" + externalSources);
        if (!missing.isEmpty()) throw new IllegalStateException("Unowned default skill sources: " + missing);

        // Bundled 1.7.1 default_skills.yml has 93 definitions, but only 90 are MythicLib classes.
        // AMBERS, NEPTUNE_GIFT and SNEAKY_PICKY are MMOCore-owned registrations and must never be faked by SVFrameLib.
        if (actual == 93 && defaultSources == 93) {
            if (nativeSources != 90 || externalSources != 3)
                throw new IllegalStateException("Expected ownership 90 MythicLib + 3 external, got " + nativeSources + " + " + externalSources);
            if (BuiltinSkillOwnership.nativeIds().size() != 90)
                throw new IllegalStateException("Native runtime exposes " + BuiltinSkillOwnership.nativeIds().size() + " IDs, expected 90");
            if (BuiltinSkillOwnership.externalProviderIds().size() != 3)
                throw new IllegalStateException("External provider registry exposes " + BuiltinSkillOwnership.externalProviderIds().size() + " IDs, expected 3");
        }
    }

    private static String unquote(String source) {
        if (source.length() >= 2 && ((source.startsWith("'") && source.endsWith("'")) || (source.startsWith("\"") && source.endsWith("\""))))
            return source.substring(1, source.length() - 1).trim();
        return source;
    }
}
