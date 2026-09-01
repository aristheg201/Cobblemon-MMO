package vn.svframe.svquest.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svquest.SVQuest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Runtime quest registry. Quest content is data, never Java source.
 *
 * Server source of truth: config/svquest/quests/*.json and settings.json.
 * Client receives the validated server snapshot through the existing state payload.
 */
public final class QuestCatalog {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String COMPLETE_ID = "__complete__";

    private QuestCatalog() {}

    public record Objective(String key, String label, int target, String featureId) {
        public Objective {
            key = clean(key);
            label = label == null ? "" : label.trim();
            featureId = featureId == null ? "" : featureId.trim();
            if (key.isBlank()) throw new IllegalArgumentException("Objective key must not be blank");
            if (label.isBlank()) throw new IllegalArgumentException("Objective label must not be blank for " + key);
            if (target <= 0) throw new IllegalArgumentException("Objective target must be > 0 for " + key);
        }
    }

    /** Generic reward payload interpreted by RewardDispatcher. */
    public record Reward(String type, String id, double amount, int count, String command, String point) {
        public Reward {
            type = clean(type);
            id = id == null ? "" : id.trim();
            command = command == null ? "" : command.trim();
            point = clean(point);
            count = Math.max(0, count);
            if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) amount = 0;
            if (type.isBlank()) throw new IllegalArgumentException("Reward type must not be blank");
        }
    }

    public record Quest(String id, String phase, String title, String description,
                        List<Objective> objectives, List<String> rewards, List<Reward> grants) {
        public Quest {
            id = clean(id);
            phase = phase == null ? "" : phase.trim();
            title = title == null ? "" : title.trim();
            description = description == null ? "" : description.trim();
            objectives = List.copyOf(objectives == null ? List.of() : objectives);
            rewards = List.copyOf(rewards == null ? List.of() : rewards);
            grants = List.copyOf(grants == null ? List.of() : grants);
            if (id.isBlank()) throw new IllegalArgumentException("Quest id must not be blank");
            if (title.isBlank()) throw new IllegalArgumentException("Quest title must not be blank: " + id);
            if (objectives.isEmpty()) throw new IllegalArgumentException("Quest must have at least one objective: " + id);
        }
    }

    /** Kept public because the existing GUI reads this list directly. The GUI layout is unchanged. */
    public static volatile List<Quest> QUESTS = List.of();
    public static volatile Set<String> CARRY_OVER = Set.of();

    public static synchronized int loadServer() {
        Path root = FabricLoader.getInstance().getConfigDir().resolve("svquest");
        try {
            copyPackagedDefaults(root);
            Set<String> carry = readCarryOver(root.resolve("settings.json"));
            Path questsDir = root.resolve("quests");
            Files.createDirectories(questsDir);

            ArrayList<Quest> loaded = new ArrayList<>();
            try (var files = Files.list(questsDir)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted().toList()) {
                    loaded.addAll(readQuestFile(file));
                }
            }
            validate(loaded);
            QUESTS = List.copyOf(loaded);
            CARRY_OVER = Set.copyOf(carry);
            SVQuest.LOGGER.info("SVQuest loaded {} quests and {} carry-over metrics from config.", QUESTS.size(), CARRY_OVER.size());
            return QUESTS.size();
        } catch (Exception error) {
            throw new IllegalStateException("SVQuest quest configuration failed validation; previous registry kept", error);
        }
    }

    public static synchronized void installClientSnapshotToken(String token) {
        if (token == null || token.isBlank()) return;
        try {
            byte[] compressed = Base64.getUrlDecoder().decode(token);
            String json;
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                json = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            ArrayList<Quest> loaded = parseQuestArray(root.getAsJsonArray("quests"), "server snapshot");
            validate(loaded);
            LinkedHashSet<String> carry = new LinkedHashSet<>();
            if (root.has("carryOver")) for (JsonElement e : root.getAsJsonArray("carryOver")) carry.add(clean(e.getAsString()));
            QUESTS = List.copyOf(loaded);
            CARRY_OVER = Set.copyOf(carry);
        } catch (Exception error) {
            SVQuest.LOGGER.warn("Ignored invalid SVQuest catalog snapshot from server: {}", error.toString());
        }
    }

    public static String snapshotToken() {
        JsonObject root = new JsonObject();
        root.add("carryOver", GSON.toJsonTree(CARRY_OVER));
        root.add("quests", GSON.toJsonTree(QUESTS));
        byte[] raw = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(raw); }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException error) {
            throw new IllegalStateException("Could not encode quest catalog snapshot", error);
        }
    }

    public static Quest byIndex(int index) {
        List<Quest> quests = QUESTS;
        if (quests.isEmpty()) throw new IllegalStateException("SVQuest catalog is empty");
        return quests.get(Math.max(0, Math.min(index, quests.size() - 1)));
    }

    public static int indexOf(String questId) {
        if (questId == null || questId.isBlank()) return -1;
        if (COMPLETE_ID.equals(questId)) return QUESTS.size();
        for (int i = 0; i < QUESTS.size(); i++) if (QUESTS.get(i).id().equals(questId)) return i;
        return -1;
    }

    public static String idAt(int index) {
        if (index >= QUESTS.size()) return COMPLETE_ID;
        if (index < 0 || QUESTS.isEmpty()) return "";
        return QUESTS.get(index).id();
    }

    public static boolean currentAccepts(int questIndex, String key) {
        String normalized = clean(key);
        if (normalized.isBlank()) return false;
        if (CARRY_OVER.contains(normalized)) return true;
        if (questIndex < 0 || questIndex >= QUESTS.size()) return false;
        return QUESTS.get(questIndex).objectives().stream().anyMatch(o -> o.key().equals(normalized));
    }

    private static List<Quest> readQuestFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonArray()) return parseQuestArray(parsed.getAsJsonArray(), file.toString());
            JsonObject object = parsed.getAsJsonObject();
            if (object.has("quests")) return parseQuestArray(object.getAsJsonArray("quests"), file.toString());
            return List.of(parseQuest(object, file.toString()));
        }
    }

    private static ArrayList<Quest> parseQuestArray(JsonArray array, String source) {
        ArrayList<Quest> quests = new ArrayList<>();
        if (array == null) return quests;
        for (JsonElement element : array) quests.add(parseQuest(element.getAsJsonObject(), source));
        return quests;
    }

    private static Quest parseQuest(JsonObject object, String source) {
        String id = requiredString(object, "id", source);
        String phase = optionalString(object, "phase");
        String title = requiredString(object, "title", source + ":" + id);
        String description = optionalString(object, "description");

        ArrayList<Objective> objectives = new ArrayList<>();
        JsonArray objectiveArray = object.getAsJsonArray("objectives");
        if (objectiveArray != null) for (JsonElement element : objectiveArray) {
            JsonObject objective = element.getAsJsonObject();
            objectives.add(new Objective(
                    requiredString(objective, "key", source + ":" + id),
                    requiredString(objective, "label", source + ":" + id),
                    objective.has("target") ? objective.get("target").getAsInt() : 1,
                    optionalString(objective, "featureId")
            ));
        }

        ArrayList<String> displayRewards = new ArrayList<>();
        JsonArray rewardDisplay = object.getAsJsonArray("rewards");
        if (rewardDisplay != null) for (JsonElement element : rewardDisplay) displayRewards.add(element.getAsString());

        ArrayList<Reward> grants = new ArrayList<>();
        JsonArray grantArray = object.getAsJsonArray("grants");
        if (grantArray != null) for (JsonElement element : grantArray) {
            JsonObject grant = element.getAsJsonObject();
            grants.add(new Reward(
                    requiredString(grant, "type", source + ":" + id),
                    optionalString(grant, "id"),
                    grant.has("amount") ? grant.get("amount").getAsDouble() : 0,
                    grant.has("count") ? grant.get("count").getAsInt() : 0,
                    optionalString(grant, "command"),
                    optionalString(grant, "point")
            ));
        }
        return new Quest(id, phase, title, description, objectives, displayRewards, grants);
    }

    private static Set<String> readCarryOver(Path settings) throws IOException {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!Files.isRegularFile(settings)) return result;
        try (Reader reader = Files.newBufferedReader(settings, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray values = root.getAsJsonArray("carryOver");
            if (values != null) for (JsonElement e : values) {
                String key = clean(e.getAsString());
                if (!key.isBlank()) result.add(key);
            }
        }
        return result;
    }

    private static void validate(List<Quest> quests) {
        if (quests.isEmpty()) throw new IllegalArgumentException("No quests were loaded");
        HashSet<String> ids = new HashSet<>();
        for (Quest quest : quests) {
            if (!ids.add(quest.id())) throw new IllegalArgumentException("Duplicate quest id: " + quest.id());
        }
    }

    /** Copies data files shipped with the mod only when the administrator has no file at that path. */
    private static void copyPackagedDefaults(Path configRoot) throws IOException {
        Files.createDirectories(configRoot);
        var container = FabricLoader.getInstance().getModContainer(SVQuest.MOD_ID)
                .orElseThrow(() -> new IllegalStateException("SVQuest mod container unavailable"));
        for (Path root : container.getRootPaths()) {
            Path defaults = root.resolve("defaults/svquest");
            if (!Files.exists(defaults)) continue;
            try (var stream = Files.walk(defaults)) {
                for (Path source : stream.filter(Files::isRegularFile).toList()) {
                    Path relative = defaults.relativize(source);
                    Path target = configRoot.resolve(relative.toString());
                    if (Files.exists(target)) continue;
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static String requiredString(JsonObject object, String key, String source) {
        String value = optionalString(object, key);
        if (value.isBlank()) throw new IllegalArgumentException("Missing '" + key + "' in " + source);
        return value;
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]+", "_");
    }
}
