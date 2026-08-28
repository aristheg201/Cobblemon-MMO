package vn.svframe.svframemmo.cobblemon.cosmetic;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Installs editable cosmetic/VFX defaults using the assets retained from CobblemonMMO 2.1.0. */
public final class CosmeticDefaults {
    public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("SVFrameMMOCobblemon");
    public static final Path COSMETICS = ROOT.resolve("cosmetics");
    public static final Path VFX = ROOT.resolve("vfx");
    private CosmeticDefaults() { }

    public static void ensure() throws java.io.IOException {
        Files.createDirectories(COSMETICS);
        Files.createDirectories(VFX);
        migrateLegacy();
        copyBundledTree("defaults/cosmetics", COSMETICS);
        copyBundledTree("defaults/vfx", VFX);
    }

    private static void migrateLegacy() throws java.io.IOException {
        Path legacy = FabricLoader.getInstance().getConfigDir().resolve("cobblemon-mmo");
        copyMissingTree(legacy.resolve("cosmetics"), COSMETICS);
        copyMissingTree(legacy.resolve("vfx"), VFX);
    }

    private static void copyBundledTree(String resource, Path target) throws java.io.IOException {
        var source = FabricLoader.getInstance().getModContainer("svframemmo_cobblemon").flatMap(container -> container.findPath(resource));
        if (source.isEmpty() || !Files.isDirectory(source.get())) return;
        copyMissingTree(source.get(), target);
    }

    private static void copyMissingTree(Path source, Path target) throws java.io.IOException {
        if (!Files.isDirectory(source)) return;
        try (var stream = Files.walk(source)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                Path relative = source.relativize(file);
                Path out = target.resolve(relative).normalize();
                if (!out.startsWith(target.normalize())) throw new java.io.IOException("Unsafe cosmetic resource path " + relative);
                if (Files.exists(out)) continue;
                Files.createDirectories(out.getParent());
                Files.copy(file, out);
            }
        }
    }
}
