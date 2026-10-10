package vn.svframe.svframemmo.skill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import vn.svframe.svframemmo.SVFrameMMO;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent progression for skills contributed by integration mods.
 *
 * <p>This state is deliberately independent of {@code PlayerData}'s legacy class-scoped skill maps. External skills
 * survive class changes and use the six-slot global SVFrameMMO skill loadout. Provider-specific temporary overlays,
 * such as a fused Pokemon's four-move set, remain independent from this persistent loadout.</p>
 */
public final class ExternalSkillProgression {
    public static final int LOADOUT_SIZE = 6;
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-ExternalSkills");

    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile Path file;
    private volatile ExecutorService writer;
    private volatile boolean closing;

    public synchronized void start(MinecraftServer server) {
        closeWriterNow();
        file = server.getSavePath(WorldSavePath.ROOT).resolve("svframemmo-external-skills.json");
        load();
        closing = false;
        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "SVFrameMMO-ExternalSkills-IO");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, failure) -> LOG.log(Level.SEVERE, "Uncaught external-skill writer failure", failure));
            return thread;
        });
    }

    /** Captures the immutable state on the server thread, then performs JSON/disk work off-thread. */
    public void save() {
        Path target = file;
        ExecutorService current = writer;
        if (target == null || current == null || closing) return;
        Map<String, SavedProfile> snapshot = snapshot();
        try {
            current.execute(() -> {
                try { write(target, snapshot); }
                catch (Exception exception) { LOG.log(Level.SEVERE, "Could not asynchronously save SVFrameMMO external skill progression", exception); }
            });
        } catch (RejectedExecutionException rejected) {
            if (!closing) LOG.log(Level.SEVERE, "External-skill writer rejected a save", rejected);
        }
    }

    /** Drains queued writes and performs one final synchronous save during server shutdown. */
    public void close() {
        final ExecutorService current;
        final Path target;
        final Map<String, SavedProfile> finalSnapshot;
        synchronized (this) {
            if (file == null) return;
            closing = true;
            current = writer;
            writer = null;
            target = file;
            finalSnapshot = snapshot();
        }

        if (current != null) {
            current.shutdown();
            try {
                if (!current.awaitTermination(30L, TimeUnit.SECONDS)) {
                    LOG.warning("Timed out draining external-skill writer; interrupting pending saves before final flush.");
                    current.shutdownNow();
                    current.awaitTermination(5L, TimeUnit.SECONDS);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                current.shutdownNow();
            }
        }

        try { write(target, finalSnapshot); }
        catch (Exception exception) { throw new IllegalStateException("Could not perform final SVFrameMMO external skill save", exception); }
        finally {
            synchronized (this) {
                file = null;
                closing = false;
            }
        }
    }

    public boolean learn(UUID playerId, String skillId, int requestedLevel) {
        ClassSkill skill = requireDefinition(skillId);
        String id = skill.getSkill().getId();
        int level = clampLevel(skill, requestedLevel);
        Profile profile = profile(playerId);
        Integer old = profile.learned.put(id, level);
        boolean changed = old == null || old != level;
        refreshOnline(playerId);
        return changed;
    }

    public boolean forget(UUID playerId, String skillId) {
        String id = normalize(skillId);
        Profile profile = profile(playerId);
        boolean changed = profile.learned.remove(id) != null;
        if (changed) {
            profile.bindings.entrySet().removeIf(entry -> id.equals(entry.getValue()));
            refreshOnline(playerId);
        }
        return changed;
    }

    public boolean isLearned(UUID playerId, String skillId) {
        return profile(playerId).learned.containsKey(normalize(skillId));
    }

    public int level(UUID playerId, String skillId) {
        return profile(playerId).learned.getOrDefault(normalize(skillId), 0);
    }

    public Map<String, Integer> learned(UUID playerId) {
        return Map.copyOf(profile(playerId).learned);
    }

    public Map<Integer, String> bindings(UUID playerId) {
        return Map.copyOf(profile(playerId).bindings);
    }

    public void bind(UUID playerId, int slot, String skillId) {
        if (slot < 1 || slot > LOADOUT_SIZE) throw new IllegalArgumentException("External skill slot must be 1.." + LOADOUT_SIZE);
        ClassSkill skill = requireDefinition(skillId);
        String id = skill.getSkill().getId();
        Profile profile = profile(playerId);
        if (!profile.learned.containsKey(id)) throw new IllegalStateException("External skill is not learned: " + id);
        if (skill.isPermanent() || skill.getTrigger().isPassive()) throw new IllegalArgumentException("Passive/permanent external skill cannot be bound: " + id);
        profile.bindings.put(slot, id);
        refreshOnline(playerId);
    }

    public String unbind(UUID playerId, int slot) {
        if (slot < 1 || slot > LOADOUT_SIZE) return null;
        String removed = profile(playerId).bindings.remove(slot);
        if (removed != null) refreshOnline(playerId);
        return removed;
    }

    public ClassSkill boundSkill(UUID playerId, int slot) {
        String id = profile(playerId).bindings.get(slot);
        if (id == null) return null;
        ClassSkill skill = SVFrameMMO.externalSkills().get(id);
        if (skill == null || !profile(playerId).learned.containsKey(id)) return null;
        return skill;
    }

    /** Keeps persisted IDs across early startup, but removes invalid active bindings once integration registries exist. */
    public void validateBindings(UUID playerId) {
        Profile profile = profile(playerId);
        boolean changed = profile.bindings.entrySet().removeIf(entry -> {
            ClassSkill skill = SVFrameMMO.externalSkills().get(entry.getValue());
            return skill == null || !profile.learned.containsKey(entry.getValue()) || skill.isPermanent() || skill.getTrigger().isPassive();
        });
        if (changed) refreshOnline(playerId);
    }

    private synchronized void load() {
        profiles.clear();
        Path target = file;
        if (target == null || !Files.exists(target)) return;
        try (Reader reader = Files.newBufferedReader(target)) {
            Type type = new TypeToken<Map<String, SavedProfile>>() { }.getType();
            Map<String, SavedProfile> loaded = gson.fromJson(reader, type);
            if (loaded == null) return;
            loaded.forEach((rawUuid, saved) -> {
                if (saved == null) return;
                UUID uuid = UUID.fromString(rawUuid);
                Profile profile = new Profile();
                if (saved.learned != null) saved.learned.forEach((rawId, rawLevel) -> {
                    if (rawId == null || rawLevel == null || rawLevel < 1) return;
                    profile.learned.put(normalize(rawId), rawLevel);
                });
                if (saved.bindings != null) saved.bindings.forEach((slot, rawId) -> {
                    if (slot == null || slot < 1 || slot > LOADOUT_SIZE || rawId == null) return;
                    String id = normalize(rawId);
                    if (profile.learned.containsKey(id)) profile.bindings.put(slot, id);
                });
                profiles.put(uuid, profile);
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load SVFrameMMO external skill progression", exception);
        }
    }

    private Map<String, SavedProfile> snapshot() {
        TreeMap<String, SavedProfile> out = new TreeMap<>();
        profiles.forEach((uuid, profile) -> out.put(uuid.toString(),
                new SavedProfile(Map.copyOf(new TreeMap<>(profile.learned)), Map.copyOf(new TreeMap<>(profile.bindings)))));
        return Map.copyOf(out);
    }

    private void write(Path target, Map<String, SavedProfile> snapshot) throws Exception {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(snapshot));
        try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private synchronized void closeWriterNow() {
        ExecutorService current = writer;
        writer = null;
        if (current != null) current.shutdownNow();
    }

    private Profile profile(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");
        return profiles.computeIfAbsent(playerId, ignored -> new Profile());
    }

    private static ClassSkill requireDefinition(String skillId) {
        ClassSkill skill = SVFrameMMO.externalSkills().get(skillId);
        if (skill == null) throw new IllegalArgumentException("Unknown external SVFrameMMO skill '" + skillId + "'");
        return skill;
    }

    private static int clampLevel(ClassSkill skill, int requested) {
        int level = Math.max(1, requested);
        if (skill.hasMaxLevel()) level = Math.min(level, skill.getMaxLevel());
        return level;
    }

    private static String normalize(String id) {
        ClassSkill known = SVFrameMMO.externalSkills().get(id);
        if (known != null) return known.getSkill().getId();
        return id == null ? "" : id.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static void refreshOnline(UUID playerId) {
        var data = SVFrameMMO.playerData().find(playerId);
        if (data != null && data.isOnline()) SVFrameMMO.skillRuntime().refresh(data);
    }

    private static final class Profile {
        private final Map<String, Integer> learned = new LinkedHashMap<>();
        private final Map<Integer, String> bindings = new LinkedHashMap<>();
    }

    private record SavedProfile(Map<String, Integer> learned, Map<Integer, String> bindings) { }
}
