package vn.svframe.compat;

import java.nio.file.Path;

/** CI smoke which prevents silently truncating MythicLib's bundled default skills. */
public final class YamlSkillCountSmoke {
    private YamlSkillCountSmoke() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: <yaml-file> <expected-top-level-keys>");
        int expected = Integer.parseInt(args[1]);
        int actual = YamlLite.map(YamlLite.parse(Path.of(args[0]))).size();
        System.out.println("YAML_TOP_LEVEL_KEYS=" + actual);
        if (actual != expected) throw new IllegalStateException("Expected " + expected + " top-level keys, got " + actual);
    }
}
