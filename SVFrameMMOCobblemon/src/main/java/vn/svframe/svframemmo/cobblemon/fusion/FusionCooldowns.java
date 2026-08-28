package vn.svframe.svframemmo.cobblemon.fusion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Timestamp cooldown storage. No per-player scheduler; cooldowns persist across server restarts. */
public final class FusionCooldowns {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, Long> potaraUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> danceUntil = new ConcurrentHashMap<>();
    private volatile Path saveFile;
    private volatile boolean dirty;

    public long potaraRemainingMillis(UUID player) { return remaining(potaraUntil, player); }
    public long danceRemainingMillis(UUID player) { return remaining(danceUntil, player); }
    public boolean potaraReady(UUID player) { return potaraRemainingMillis(player) <= 0L; }
    public boolean danceReady(UUID player) { return danceRemainingMillis(player) <= 0L; }

    public void markPotara(UUID player, int seconds) { mark(potaraUntil, player, seconds); }
    public void markDance(UUID player, int seconds) { mark(danceUntil, player, seconds); }

    public synchronized void start(MinecraftServer server) {
        saveFile = server.getSavePath(WorldSavePath.ROOT).resolve("svframemmo-cobblemon-cooldowns.json");
        potaraUntil.clear();
        danceUntil.clear();
        if (!Files.isRegularFile(saveFile)) return;
        try {
            Save saved = GSON.fromJson(Files.readString(saveFile), Save.class);
            long now = System.currentTimeMillis();
            if (saved != null) {
                restore(saved.potara, potaraUntil, now);
                restore(saved.dance, danceUntil, now);
            }
            dirty = false;
        } catch (Exception error) {
            throw new IllegalStateException("Could not load fusion cooldown state", error);
        }
    }

    public void tick(long tick) {
        if (dirty && tick % 100L == 0L) save();
    }

    public synchronized void stop() {
        save();
        saveFile = null;
    }

    public synchronized void save() {
        Path file = saveFile;
        if (file == null || !dirty) return;
        try {
            prune(potaraUntil);
            prune(danceUntil);
            Files.createDirectories(file.getParent());
            Save out = new Save(serialize(potaraUntil), serialize(danceUntil));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(out));
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        } catch (Exception error) {
            throw new IllegalStateException("Could not save fusion cooldown state", error);
        }
    }

    private void mark(Map<UUID, Long> map, UUID player, int seconds) {
        if (player == null) return;
        if (seconds <= 0) map.remove(player);
        else map.put(player, System.currentTimeMillis() + seconds * 1000L);
        dirty = true;
    }

    private long remaining(Map<UUID, Long> map, UUID player) {
        if (player == null) return 0L;
        Long until = map.get(player);
        if (until == null) return 0L;
        long left = until - System.currentTimeMillis();
        if (left <= 0L) {
            if (map.remove(player, until)) dirty = true;
            return 0L;
        }
        return left;
    }

    private static void restore(Map<String, Long> source, Map<UUID, Long> target, long now) {
        if (source == null) return;
        source.forEach((raw, until) -> {
            try {
                UUID id = UUID.fromString(raw);
                if (until != null && until > now) target.put(id, until);
            } catch (RuntimeException ignored) { }
        });
    }

    private static Map<String, Long> serialize(Map<UUID, Long> source) {
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.put(entry.getKey().toString(), entry.getValue()));
        return out;
    }

    private static void prune(Map<UUID, Long> source) {
        long now = System.currentTimeMillis();
        source.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= now);
    }

    private static final class Save {
        Map<String, Long> potara = Map.of();
        Map<String, Long> dance = Map.of();
        Save() { }
        Save(Map<String, Long> potara, Map<String, Long> dance) { this.potara = potara; this.dance = dance; }
    }
}
