package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Stable quest-id persistence. Reordering config files no longer reassigns a player's current quest. */
public final class QuestStateStore {
    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("svquest/playerdata");
    private final Map<UUID, PlayerState> cache = new HashMap<>();

    public QuestStateStore() {
        try { Files.createDirectories(dir); }
        catch (IOException e) { SVQuest.LOGGER.error("Cannot create SVQuest playerdata directory", e); }
    }

    public synchronized PlayerState get(UUID id) { return cache.computeIfAbsent(id, this::load); }

    public synchronized void unload(UUID id) {
        PlayerState state = cache.remove(id);
        if (state != null) save(id, state);
    }

    public synchronized void saveAll() { cache.forEach(this::save); }

    public synchronized void saveNow(UUID id) {
        PlayerState state = cache.get(id);
        if (state != null) save(id, state);
    }

    public synchronized void rebindCatalog() { cache.values().forEach(PlayerState::normalize); }

    private PlayerState load(UUID id) {
        PlayerState state = new PlayerState();
        Path file = dir.resolve(id + ".properties");
        if (!Files.isRegularFile(file)) {
            state.normalize();
            return state;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            state.questId = p.getProperty("questId", "").trim();
            state.questIndex = parseInt(p.getProperty("questIndex"), 0);
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("progress.")) state.progress.put(key.substring(9), parseInt(p.getProperty(key), 0));
                else if (key.startsWith("rewarded.") && Boolean.parseBoolean(p.getProperty(key))) state.rewarded.add(key.substring(9));
            }
        } catch (Exception e) {
            SVQuest.LOGGER.error("Could not load SVQuest state for {}. Using a safe empty state.", id, e);
        }
        state.normalize();
        return state;
    }

    private void save(UUID id, PlayerState state) {
        try {
            Files.createDirectories(dir);
            state.normalize();
            Properties p = new Properties();
            p.setProperty("questId", state.questId);
            p.setProperty("questIndex", Integer.toString(state.questIndex));
            state.progress.forEach((k, v) -> p.setProperty("progress." + k, Integer.toString(v)));
            state.rewarded.forEach(q -> p.setProperty("rewarded." + q, "true"));
            Path target = dir.resolve(id + ".properties");
            Path temp = dir.resolve(id + ".properties.tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                p.store(out, "SVQuest player state v5 - manual reward claim, stable quest ids");
            }
            try {
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            SVQuest.LOGGER.error("Could not save SVQuest state for {}", id, e);
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return fallback; }
    }

    public static final class PlayerState {
        private String questId = "";
        private int questIndex;
        private final Map<String, Integer> progress = new HashMap<>();
        private final Set<String> rewarded = new HashSet<>();
        private long revision;

        public int questIndex() {
            normalize();
            return questIndex;
        }

        public long revision() { return revision; }
        public int progress(String key) { return Math.max(0, progress.getOrDefault(key, 0)); }
        public boolean rewarded(String id) { return rewarded.contains(id); }

        public boolean markRewarded(String id) {
            boolean changed = rewarded.add(id);
            if (changed) revision++;
            return changed;
        }

        public void signal(String key, int amount) {
            if (amount <= 0 || key == null || key.isBlank()) return;
            normalize();
            if (!QuestCatalog.currentAccepts(questIndex, key)) return;
            int before = progress(key);
            int after = before > Integer.MAX_VALUE - amount ? Integer.MAX_VALUE : before + amount;
            if (after != before) {
                progress.put(key, after);
                revision++;
            }
        }

        public void metric(String key, int value) {
            if (key == null || key.isBlank()) return;
            normalize();
            int safe = Math.max(0, value);
            int before = progress(key);
            if (safe > before) {
                progress.put(key, safe);
                revision++;
            }
        }

        public void add(String key, int amount) {
            if (amount == 0 || key == null || key.isBlank()) return;
            normalize();
            int before = progress(key);
            long candidate = (long) before + amount;
            int after = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, candidate));
            if (after != before) {
                progress.put(key, after);
                revision++;
            }
        }

        public void set(String key, int value) {
            if (key == null || key.isBlank()) return;
            normalize();
            int safe = Math.max(0, value);
            int before = progress(key);
            if (safe != before) {
                progress.put(key, safe);
                revision++;
            }
        }

        public boolean currentComplete() {
            normalize();
            if (questIndex < 0 || questIndex >= QuestCatalog.QUESTS.size()) return false;
            var quest = QuestCatalog.QUESTS.get(questIndex);
            return quest.objectives().stream().allMatch(o -> progress(o.key()) >= o.target());
        }

        /** Advances exactly one completed quest after the server has successfully processed its claim. */
        public boolean advanceClaimed(String expectedQuestId) {
            normalize();
            if (questIndex < 0 || questIndex >= QuestCatalog.QUESTS.size()) return false;
            var quest = QuestCatalog.QUESTS.get(questIndex);
            if (expectedQuestId == null || !quest.id().equals(expectedQuestId) || !currentComplete()) return false;
            questIndex++;
            questId = QuestCatalog.idAt(questIndex);
            revision++;
            return true;
        }

        private void normalize() {
            int size = QuestCatalog.QUESTS.size();
            if (size <= 0) {
                questIndex = 0;
                questId = "";
                progress.replaceAll((k, v) -> Math.max(0, v));
                return;
            }
            if (!questId.isBlank()) {
                int resolved = QuestCatalog.indexOf(questId);
                if (resolved >= 0) questIndex = Math.min(resolved, size);
                else {
                    questIndex = Math.max(0, Math.min(questIndex, size));
                    questId = QuestCatalog.idAt(questIndex);
                }
            } else {
                questIndex = Math.max(0, Math.min(questIndex, size));
                questId = QuestCatalog.idAt(questIndex);
            }
            progress.replaceAll((k, v) -> Math.max(0, v));
        }

        /** Small progress-only payload. The quest catalog is synchronized separately. */
        public String encode() {
            normalize();
            StringBuilder out = new StringBuilder();
            out.append("v=5\n");
            out.append("questIndex=").append(questIndex).append('\n');
            out.append("questId=").append(safe(questId)).append('\n');
            progress.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    out.append("p.").append(safe(e.getKey())).append('=').append(e.getValue()).append('\n'));
            return out.toString();
        }

        private static String safe(String s) {
            return s == null ? "" : s.replace("\\", "").replace("\n", "").replace("\r", "").replace("=", "");
        }
    }
}
