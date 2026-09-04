package vn.svframe.svframemmo.cobblemon.cosmetic;

import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

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

    private static final String LEGACY_DAI_THANH_ID = "back_dai_thanh_hoa_bao";
    private static final String LEGACY_DAI_THANH_FILE = "back_dai_thanh_hoa_bao.yml";

    private CosmeticDefaults() { }

    public static void ensure() throws java.io.IOException {
        Files.createDirectories(COSMETICS);
        Files.createDirectories(VFX);
        migrateLegacyVfx();
        copyBundledTree("defaults/vfx", VFX);

        // Versions that briefly auto-installed the Dai Thanh example could leave a generated canonical file next to
        // an administrator-supplied copy with the same id. Remove only that known generated file when another file
        // already defines the same id, before CosmeticService scans the directory.
        cleanupLegacyAutoInstalledCosmetic(LEGACY_DAI_THANH_ID, LEGACY_DAI_THANH_FILE);
    }

    private static void migrateLegacyVfx() throws java.io.IOException {
        Path legacy = FabricLoader.getInstance().getConfigDir().resolve("cobblemon-mmo");
        copyMissingTree(legacy.resolve("vfx"), VFX);
    }

    private static void cleanupLegacyAutoInstalledCosmetic(String id, String canonicalName) throws java.io.IOException {
        Path canonical = COSMETICS.resolve(canonicalName).toAbsolutePath().normalize();
        if (!Files.isRegularFile(canonical)) return;

        boolean duplicate = false;
        try (var stream = Files.walk(COSMETICS)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(CosmeticDefaults::yaml).toList()) {
                Path normalized = file.toAbsolutePath().normalize();
                if (normalized.equals(canonical)) continue;
                if (id.equals(readCosmeticId(file))) {
                    duplicate = true;
                    break;
                }
            }
        }
        if (!duplicate) return;

        Files.deleteIfExists(canonical);
        SVFrameMMOCobblemon.LOG.warn(
                "Removed legacy auto-installed cosmetic {} because another YAML already defines id '{}'. "
                        + "Cosmetic YAML is now fully administrator-managed.", canonical, id);
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
