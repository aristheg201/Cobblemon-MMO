package vn.svframe.svframemmo.cobblemon.cosmetic;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Single-startup Polymer pack contribution, ported from CobblemonMMO's Snowstorm pack lifecycle. */
public final class SnowstormPackService {
    private static final Logger LOG = LoggerFactory.getLogger("SVFrameMMO/Cosmetic-Pack");
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean();
    private static final AtomicReference<Map<String, byte[]>> ASSETS = new AtomicReference<>(Map.of());
    private static final AtomicReference<String> STAGED = new AtomicReference<>("");
    private static final AtomicReference<String> VERIFIED = new AtomicReference<>("");
    private static final Consumer<ResourcePackBuilder> WRITER = builder -> ASSETS.get().forEach((path, data) -> builder.addData(path, Arrays.copyOf(data, data.length)));
    private SnowstormPackService() { }

    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(WRITER);
            PolymerResourcePackUtils.RESOURCE_PACK_FINISHED_EVENT.register(() -> VERIFIED.set(STAGED.get()));
        }
    }

    public static synchronized void stage(SnowstormAssetLoader.Bundle bundle) {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        bundle.assets().forEach((path, data) -> copy.put(path, Arrays.copyOf(data, data.length)));
        ASSETS.set(Map.copyOf(copy));
        STAGED.set(digest(copy));
        VERIFIED.set("");
        ATTEMPTED.set(false);
        LOG.info("Staged {} Snowstorm asset(s) / {} particle id(s)", copy.size(), bundle.particleIds().size());
    }

    public static synchronized boolean buildInitial() {
        if (ready()) return true;
        if (STAGED.get().isBlank() || !ATTEMPTED.compareAndSet(false, true)) return ready();
        Path output = PolymerResourcePackUtils.getMainPath();
        if (!replaceable(output)) {
            LOG.warn("Polymer pack output is locked at {}; custom cosmetics will use vanilla fallback until restart", output.toAbsolutePath());
            return false;
        }
        try {
            boolean built = PolymerResourcePackUtils.buildMain();
            if (!built || !ready()) LOG.warn("Polymer build did not verify the staged Snowstorm payload; vanilla fallbacks remain active");
            return built && ready();
        } catch (RuntimeException | LinkageError error) {
            LOG.warn("Polymer cosmetic pack build failed; vanilla fallbacks remain active", error);
            return false;
        }
    }

    public static boolean ready() { return !STAGED.get().isBlank() && STAGED.get().equals(VERIFIED.get()); }

    private static boolean replaceable(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(path)) return parent == null || Files.isWritable(parent);
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE); var lock = channel.tryLock()) { return lock != null; }
            catch (OverlappingFileLockException ignored) { return false; }
        } catch (Exception ignored) { return false; }
    }

    private static String digest(Map<String, byte[]> assets) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            assets.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                hash.update(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                hash.update((byte) 0); hash.update(entry.getValue());
            });
            return HexFormat.of().formatHex(hash.digest());
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
