package vn.svframe.svframemmo.cobblemon.cosmetic;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the editable cosmetic/VFX directories and installs missing bundled defaults.
 *
 * Bundled defaults are copied only when a destination file is absent, so administrators may freely edit or remove
 * installed definitions without future updates overwriting their changes. VFX assets are handled the same way.
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
        copyBundledTree("defaults/cosmetics", COSMETICS);
    }

    private static void migrateLegacyVfx() throws java.io.IOException {
        Path legacy = FabricLoader.getInstance().getConfigDir().resolve("cobblemon-mmo");
        copyMissingTree(legacy.resolve("vfx"), VFX);
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
