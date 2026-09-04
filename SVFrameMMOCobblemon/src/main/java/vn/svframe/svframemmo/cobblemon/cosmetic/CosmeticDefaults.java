package vn.svframe.svframemmo.cobblemon.cosmetic;

import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Creates the editable cosmetic/VFX directories.
 *
 * VFX runtime assets may ship with the mod and are copied when missing. Player cosmetic definitions are deliberately
 * NOT auto-installed: cosmetics are administrator-owned YAML under config/SVFrameMMOCobblemon/cosmetics so a JAR
 * update can never silently create a second definition with the same id.
 */
public final class CosmeticDefaults {
    public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("SVFrameMMOCobblemon");
    public static final Path COSMETICS = ROOT.resolve("cosmetics");
    public static final Path VFX = ROOT.resolve("vfx");

    private CosmeticDefaults() { }

    public static void ensure() throws java.io.IOException {
        Files.createDirectories(COSMETICS);
        Files.createDirectories(VFX);
        migrateLegacyVfx();
        copyBundledTree("defaults/vfx", VFX);
        quarantineDuplicateCosmetics();
    }

    private static void migrateLegacyVfx() throws java.io.IOException {
        Path legacy = FabricLoader.getInstance().getConfigDir().resolve("cobblemon-mmo");
        copyMissingTree(legacy.resolve("vfx"), VFX);
    }

    /**
     * Cosmetic definitions are user configuration, so duplicate ids must never take down the whole server.
     * The canonical <id>.yml file wins when present; otherwise the lexicographically first path wins.
     * Losing files are preserved next to the original with a .duplicate-disabled suffix, which keeps them out of
     * the YAML scanner while making the conflict obvious and reversible to an administrator.
     */
    private static void quarantineDuplicateCosmetics() throws java.io.IOException {
        if (!Files.isDirectory(COSMETICS)) return;

        Map<String, Path> selected = new LinkedHashMap<>();
        try (var stream = Files.walk(COSMETICS)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(CosmeticDefaults::yaml).sorted().toList()) {
                String id = readCosmeticId(file);
                if (id.isBlank()) continue;

                Path existing = selected.get(id);
                if (existing == null) {
                    selected.put(id, file);
                    continue;
                }

                Path keep = preferred(existing, file, id);
                Path duplicate = keep.equals(existing) ? file : existing;
                if (!keep.equals(existing)) selected.put(id, keep);
                quarantine(duplicate, id, keep);
            }
        }
    }

    private static Path preferred(Path first, Path second, String id) {
        int firstScore = canonicalScore(first, id);
        int secondScore = canonicalScore(second, id);
        if (firstScore != secondScore) return firstScore < secondScore ? first : second;
        return first.toString().compareToIgnoreCase(second.toString()) <= 0 ? first : second;
    }

    private static int canonicalScore(Path file, String id) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String normalized = id.toLowerCase(Locale.ROOT);
        if (name.equals(normalized + ".yml")) return 0;
        if (name.equals(normalized + ".yaml")) return 1;
        return 2;
    }

    private static void quarantine(Path duplicate, String id, Path keep) throws java.io.IOException {
        String base = duplicate.getFileName().toString() + ".duplicate-disabled";
        Path target = duplicate.resolveSibling(base);
        int suffix = 2;
        while (Files.exists(target)) target = duplicate.resolveSibling(base + "." + suffix++);
        try {
            Files.move(duplicate, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(duplicate, target);
        }
        SVFrameMMOCobblemon.LOG.warn(
                "Duplicate cosmetic id '{}' found. Keeping {} and disabling duplicate as {}.", id, keep, target);
    }

    private static String readCosmeticId(Path file) {
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (!trimmed.regionMatches(true, 0, "id:", 0, 3)) continue;
                String value = trimmed.substring(3).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))))
                    value = value.substring(1, value.length() - 1).trim();
                return CosmeticDefinition.normalize(value);
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static boolean yaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static void copyBundledTree(String resource, Path target) throws java.io.IOException {
        var source = FabricLoader.getInstance().getModContainer("svframemmo_cobblemon")
                .flatMap(container -> container.findPath(resource));
        if (source.isEmpty() || !Files.isDirectory(source.get())) return;
        copyMissingTree(source.get(), target);
    }

    private static void copyMissingTree(Path source, Path target) throws java.io.IOException {
        if (!Files.isDirectory(source)) return;
        Path normalizedTarget = target.toAbsolutePath().normalize();
        try (var stream = Files.walk(source)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String relative = source.relativize(file).toString().replace('\\', '/');
                if (relative.isBlank() || relative.startsWith("/")
                        || relative.contains("../") || relative.equals(".."))
                    throw new java.io.IOException("Unsafe cosmetic resource path " + relative);
                Path out = normalizedTarget.resolve(relative).normalize();
                if (!out.startsWith(normalizedTarget))
                    throw new java.io.IOException("Unsafe cosmetic resource path " + relative);
                if (Files.exists(out)) continue;
                Files.createDirectories(out.getParent());
                Files.copy(file, out);
            }
        }
    }
}
