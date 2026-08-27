package vn.svframe.svframeitems.config;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class DefaultFiles {
    private static final List<String> FILES = List.of(
            "types.yml", "rarities.yml", "upgrades.yml", "sets.yml", "recipes.yml", "loot.yml", "items/examples.yml");
    private DefaultFiles() {}
    public static Path root() { return FabricLoader.getInstance().getConfigDir().resolve("SVFrameItems"); }
    public static void ensure() throws IOException {
        Path root = root();
        for (String relative : FILES) {
            Path target = root.resolve(relative);
            if (Files.exists(target)) continue;
            Files.createDirectories(target.getParent());
            try (InputStream input = DefaultFiles.class.getResourceAsStream("/default/" + relative)) {
                if (input == null) throw new FileNotFoundException("Missing bundled default/" + relative);
                Files.copy(input, target);
            }
        }
    }
}
