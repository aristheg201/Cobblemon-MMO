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

/**
 * Persistent progression for skills contributed by integration mods.
 *
 * <p>This state is deliberately independent of {@code PlayerData}'s legacy class-scoped skill maps. External skills
 * survive class changes and use the six-slot global SVFrameMMO skill loadout. Provider-specific temporary overlays,
 * such as a fused Pokemon's four-move set, remain independent from this persistent loadout.</p>
 */
public final class ExternalSkillProgression {
    public static final int LOADOUT_SIZE = 6;

    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Path file;

    public synchronized void start(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("svframemmo-external-skills.json");
        load();
    }

    public synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Map<String, SavedProfile> out = new TreeMap<>();
            profiles.forEach((uuid, profile) -> out.put(uuid.toString(),
                    new SavedProfile(new TreeMap<>(profile.learned), new TreeMap<>(profile.bindings))));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(out));
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not save SVFrameMMO external skill progression", exception);
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
        if (file == null || !Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
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
