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

/** Data-driven runtime quest registry. Quest content never lives in Java source. */
public final class QuestCatalog {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String COMPLETE_ID = "__complete__";
    private static final double MAX_COBBLEDOLLARS = 100_000_000D;
    private static final double MIN_COBBLEDOLLARS_GRANT = 500_000D;
    private static final double MAX_BEASTCOIN = 500D;
    private static final double MIN_BEASTCOIN_GRANT = 10D;

    private QuestCatalog() {}

    public record Objective(String key, String label, int target, String featureId, boolean resetOnClaim) {
        public Objective {
            key = clean(key);
            label = label == null ? "" : label.trim();
            featureId = featureId == null ? "" : featureId.trim();
            if (key.isBlank()) throw new IllegalArgumentException("Objective key must not be blank");
            if (label.isBlank()) throw new IllegalArgumentException("Objective label must not be blank for " + key);
            if (target <= 0) throw new IllegalArgumentException("Objective target must be > 0 for " + key);
        }
    }

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

    public static volatile List<Quest> QUESTS = List.of();
    public static volatile Set<String> CARRY_OVER = Set.of();

    public static synchronized int loadServer() {
        Path root = FabricLoader.getInstance().getConfigDir().resolve("svquest");
        try {
            copyPackagedDefaults(root);
            Set<String> carry = readCarryOver(root.resolve("settings.json"));
            Path questsDir = root.resolve("quests");
            Files.createDirectories(questsDir);

            List<Path> allFiles;
            try (var files = Files.list(questsDir)) {
                allFiles = files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted().toList();
            }
            List<Path> selected = selectCatalogFiles(allFiles);
            ArrayList<Quest> loaded = new ArrayList<>();
            for (Path file : selected) loaded.addAll(readQuestFile(file));

            validate(loaded);
            QUESTS = List.copyOf(loaded);
            CARRY_OVER = Set.copyOf(carry);
            SVQuest.LOGGER.info("SVQuest loaded {} quests from {} catalog file(s), carry-over metrics={}",
                    QUESTS.size(), selected.size(), CARRY_OVER.size());
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

    private static List<Path> selectCatalogFiles(List<Path> files) {
        ArrayList<Path> roots = new ArrayList<>();
        for (Path file : files) if (isCatalogRoot(file)) roots.add(file);
        return roots.isEmpty() ? files : List.copyOf(roots);
    }

    private static boolean isCatalogRoot(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() && booleanValue(parsed.getAsJsonObject(), "catalogRoot", false);
        } catch (Exception ignored) { return false; }
    }

    private static List<Quest> readQuestFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonArray()) return parseQuestArray(parsed.getAsJsonArray(), file.toString());
            JsonObject object = parsed.getAsJsonObject();
            ArrayList<Quest> result = new ArrayList<>();
            JsonArray entries = object.getAsJsonArray("entries");
            if (entries != null) {
                for (JsonElement element : entries) {
                    JsonObject entry = element.getAsJsonObject();
                    String kind = optionalString(entry, "kind").toLowerCase(Locale.ROOT);
                    if (kind.isBlank() || kind.equals("quest")) result.add(parseQuest(entry, file.toString()));
                    else if (kind.equals("series")) result.addAll(expandSeries(entry, file.toString()));
                    else if (kind.equals("rotation")) result.addAll(expandRotation(entry, file.toString()));
                    else throw new IllegalArgumentException("Unknown quest entry kind '" + kind + "' in " + file);
                }
                return result;
            }
            if (object.has("quests")) result.addAll(parseQuestArray(object.getAsJsonArray("quests"), file.toString()));
            JsonArray series = object.getAsJsonArray("series");
            if (series != null) for (JsonElement element : series) result.addAll(expandSeries(element.getAsJsonObject(), file.toString()));
            if (!result.isEmpty()) return result;
            return List.of(parseQuest(object, file.toString()));
        }
    }

    private static ArrayList<Quest> expandSeries(JsonObject series, String source) {
        String prefix = requiredString(series, "idPrefix", source);
        String phase = optionalString(series, "phase");
        String titleTemplate = requiredString(series, "title", source + ":" + prefix);
        String descriptionTemplate = optionalString(series, "description");
        int count = intValue(series, "count", 0);
        if (count <= 0 || count > 10000) throw new IllegalArgumentException("Series count must be 1..10000: " + prefix);
        JsonArray objectiveTemplates = series.getAsJsonArray("objectives");
        if (objectiveTemplates == null || objectiveTemplates.isEmpty()) {
            JsonObject single = series.getAsJsonObject("objective");
            if (single == null) throw new IllegalArgumentException("Series has no objective(s): " + prefix);
            objectiveTemplates = new JsonArray(); objectiveTemplates.add(single);
        }
        ArrayList<String> rewardTemplates = strings(series.getAsJsonArray("rewards"));
        JsonArray grantTemplates = series.getAsJsonArray("grants");
        ArrayList<Quest> result = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ArrayList<Objective> objectives = new ArrayList<>();
            int primaryTarget = 1;
            for (int oi = 0; oi < objectiveTemplates.size(); oi++) {
                JsonObject template = objectiveTemplates.get(oi).getAsJsonObject();
                int target = Math.max(1, intValue(template, "targetStart", intValue(template, "target", 1)) + (i - 1) * intValue(template, "targetStep", 0));
                if (oi == 0) primaryTarget = target;
                objectives.add(new Objective(requiredString(template, "key", source + ":" + prefix),
                        render(requiredString(template, "label", source + ":" + prefix), i, target), target,
                        optionalString(template, "featureId"), booleanValue(template, "resetOnClaim", false)));
            }
            result.add(new Quest(prefix + "_" + String.format(Locale.ROOT, "%03d", i), phase,
                    render(titleTemplate, i, primaryTarget), render(descriptionTemplate, i, primaryTarget), objectives,
                    renderRewards(rewardTemplates, i, primaryTarget), expandGrants(grantTemplates, i, primaryTarget)));
        }
        return result;
    }

    /** Distinct steps repeat only after a complete cycle, avoiding hundreds of consecutive copy-paste milestones. */
    private static ArrayList<Quest> expandRotation(JsonObject rotation, String source) {
        String prefix = requiredString(rotation, "idPrefix", source);
        int cycles = intValue(rotation, "cycles", 0);
        if (cycles <= 0 || cycles > 1000) throw new IllegalArgumentException("Rotation cycles must be 1..1000: " + prefix);
        JsonArray steps = rotation.getAsJsonArray("steps");
        if (steps == null || steps.isEmpty()) throw new IllegalArgumentException("Rotation has no steps: " + prefix);
        String defaultPhase = optionalString(rotation, "phase");
        ArrayList<Quest> result = new ArrayList<>(cycles * steps.size());
        for (int cycle = 1; cycle <= cycles; cycle++) {
            for (int si = 0; si < steps.size(); si++) {
                int stepNumber = si + 1;
                JsonObject step = steps.get(si).getAsJsonObject();
                String stepId = clean(requiredString(step, "id", source + ":" + prefix));
                String phase = optionalString(step, "phase"); if (phase.isBlank()) phase = defaultPhase;
                JsonArray objectiveTemplates = step.getAsJsonArray("objectives");
                if (objectiveTemplates == null || objectiveTemplates.isEmpty()) throw new IllegalArgumentException("Rotation step has no objectives: " + stepId);
                ArrayList<Objective> objectives = new ArrayList<>();
                int primaryTarget = 1;
                for (int oi = 0; oi < objectiveTemplates.size(); oi++) {
                    JsonObject template = objectiveTemplates.get(oi).getAsJsonObject();
                    int target = Math.max(1, intValue(template, "targetStart", intValue(template, "target", 1)) + (cycle - 1) * intValue(template, "targetStep", 0));
                    if (oi == 0) primaryTarget = target;
                    objectives.add(new Objective(requiredString(template, "key", source + ":" + prefix + ":" + stepId),
                            renderRotation(requiredString(template, "label", source + ":" + prefix + ":" + stepId), cycle, stepNumber, target), target,
                            optionalString(template, "featureId"), booleanValue(template, "resetOnClaim", false)));
                }
                ArrayList<String> displayRewards = new ArrayList<>();
                for (String reward : strings(step.getAsJsonArray("rewards"))) displayRewards.add(renderRotation(reward, cycle, stepNumber, primaryTarget));
                result.add(new Quest(prefix + "_" + String.format(Locale.ROOT, "%02d", cycle) + "_" + stepId, phase,
                        renderRotation(requiredString(step, "title", source + ":" + prefix + ":" + stepId), cycle, stepNumber, primaryTarget),
                        renderRotation(optionalString(step, "description"), cycle, stepNumber, primaryTarget), objectives, displayRewards,
                        expandRotationGrants(step.getAsJsonArray("grants"), cycle, stepNumber, primaryTarget)));
            }
        }
        return result;
    }

    private static ArrayList<String> renderRewards(List<String> templates, int index, int target) {
        ArrayList<String> out = new ArrayList<>(); for (String template : templates) out.add(render(template, index, target)); return out;
    }

    private static ArrayList<Reward> expandGrants(JsonArray templates, int index, int target) {
        ArrayList<Reward> out = new ArrayList<>(); if (templates == null) return out;
        for (JsonElement element : templates) {
            JsonObject g = element.getAsJsonObject();
            out.add(new Reward(requiredString(g, "type", "series grant"), optionalString(g, "id"),
                    Math.max(0, doubleValue(g, "amountStart", doubleValue(g, "amount", 0)) + (index - 1) * doubleValue(g, "amountStep", 0)),
                    Math.max(0, intValue(g, "countStart", intValue(g, "count", 0)) + (index - 1) * intValue(g, "countStep", 0)),
                    render(optionalString(g, "command"), index, target), optionalString(g, "point")));
        }
        return out;
    }

    private static ArrayList<Reward> expandRotationGrants(JsonArray templates, int cycle, int step, int target) {
        ArrayList<Reward> out = new ArrayList<>(); if (templates == null) return out;
        for (JsonElement element : templates) {
            JsonObject g = element.getAsJsonObject();
            out.add(new Reward(requiredString(g, "type", "rotation grant"), optionalString(g, "id"),
                    Math.max(0, doubleValue(g, "amountStart", doubleValue(g, "amount", 0)) + (cycle - 1) * doubleValue(g, "amountStep", 0)),
                    Math.max(0, intValue(g, "countStart", intValue(g, "count", 0)) + (cycle - 1) * intValue(g, "countStep", 0)),
                    renderRotation(optionalString(g, "command"), cycle, step, target), optionalString(g, "point")));
        }
        return out;
    }

    private static ArrayList<Quest> parseQuestArray(JsonArray array, String source) {
        ArrayList<Quest> quests = new ArrayList<>(); if (array != null) for (JsonElement element : array) quests.add(parseQuest(element.getAsJsonObject(), source)); return quests;
    }

    private static Quest parseQuest(JsonObject object, String source) {
        String id = requiredString(object, "id", source), phase = optionalString(object, "phase"), title = requiredString(object, "title", source + ":" + id), description = optionalString(object, "description");
        ArrayList<Objective> objectives = new ArrayList<>();
        JsonArray objectiveArray = object.getAsJsonArray("objectives");
        if (objectiveArray != null) for (JsonElement element : objectiveArray) {
            JsonObject o = element.getAsJsonObject();
            objectives.add(new Objective(requiredString(o, "key", source + ":" + id), requiredString(o, "label", source + ":" + id),
                    intValue(o, "target", 1), optionalString(o, "featureId"), booleanValue(o, "resetOnClaim", false)));
        }
        ArrayList<Reward> grants = new ArrayList<>();
        JsonArray grantArray = object.getAsJsonArray("grants");
        if (grantArray != null) for (JsonElement element : grantArray) {
            JsonObject g = element.getAsJsonObject();
            grants.add(new Reward(requiredString(g, "type", source + ":" + id), optionalString(g, "id"), doubleValue(g, "amount", 0),
                    intValue(g, "count", 0), optionalString(g, "command"), optionalString(g, "point")));
        }
        return new Quest(id, phase, title, description, objectives, strings(object.getAsJsonArray("rewards")), grants);
    }

    private static Set<String> readCarryOver(Path settings) throws IOException {
        LinkedHashSet<String> result = new LinkedHashSet<>(); if (!Files.isRegularFile(settings)) return result;
        try (Reader reader = Files.newBufferedReader(settings, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject(); JsonArray values = root.getAsJsonArray("carryOver");
            if (values != null) for (JsonElement e : values) { String key = clean(e.getAsString()); if (!key.isBlank()) result.add(key); }
        }
        return result;
    }

    private static void validate(List<Quest> quests) {
        if (quests.isEmpty()) throw new IllegalArgumentException("No quests were loaded");
        HashSet<String> ids = new HashSet<>(); double cobbleTotal = 0D, beastTotal = 0D;
        for (Quest quest : quests) {
            if (!ids.add(quest.id())) throw new IllegalArgumentException("Duplicate quest id: " + quest.id());
            for (String display : quest.rewards()) if (display.toLowerCase(Locale.ROOT).contains("huntercoin")) throw new IllegalArgumentException("HunterCoin reward is forbidden: " + quest.id());
            for (Reward reward : quest.grants()) {
                String type = clean(reward.type()), id = clean(reward.id());
                if (type.equals("beconomy") && id.equals("huntercoin")) throw new IllegalArgumentException("HunterCoin reward is forbidden: " + quest.id());
                if (type.equals("cobbledollars") && reward.amount() > 0) {
                    if (reward.amount() < MIN_COBBLEDOLLARS_GRANT) throw new IllegalArgumentException("CobbleDollars reward below 500000: " + quest.id());
                    cobbleTotal += reward.amount();
                }
                if (type.equals("beconomy") && id.equals("beastcoin") && reward.amount() > 0) {
                    if (reward.amount() < MIN_BEASTCOIN_GRANT) throw new IllegalArgumentException("BeastCoin reward below 10: " + quest.id());
                    beastTotal += reward.amount();
                }
            }
        }
        if (cobbleTotal > MAX_COBBLEDOLLARS) throw new IllegalArgumentException("CobbleDollars campaign budget exceeds 100000000: " + cobbleTotal);
        if (beastTotal > MAX_BEASTCOIN) throw new IllegalArgumentException("BeastCoin campaign budget exceeds 500: " + beastTotal);
        SVQuest.LOGGER.info("SVQuest reward budget validated: CobbleDollars={}, BeastCoin={}, HunterCoin=0", cobbleTotal, beastTotal);
    }

    private static void copyPackagedDefaults(Path configRoot) throws IOException {
        Files.createDirectories(configRoot);
        var container = FabricLoader.getInstance().getModContainer(SVQuest.MOD_ID).orElseThrow(() -> new IllegalStateException("SVQuest mod container unavailable"));
        for (Path root : container.getRootPaths()) {
            Path defaults = root.resolve("defaults/svquest"); if (!Files.exists(defaults)) continue;
            try (var stream = Files.walk(defaults)) {
                for (Path source : stream.filter(Files::isRegularFile).toList()) {
                    Path target = configRoot.resolve(defaults.relativize(source).toString()); Files.createDirectories(target.getParent());
                    if (!Files.exists(target) || shouldReplaceManagedDefault(source, target)) Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static boolean shouldReplaceManagedDefault(Path source, Path target) { int sourceVersion = managedVersion(source); return sourceVersion >= 0 && sourceVersion > managedVersion(target); }
    private static int managedVersion(Path file) {
        if (file == null || !Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) return -1;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { JsonElement parsed = JsonParser.parseReader(reader); return parsed.isJsonObject() ? intValue(parsed.getAsJsonObject(), "managedVersion", -1) : -1; }
        catch (Exception ignored) { return -1; }
    }

    private static ArrayList<String> strings(JsonArray array) { ArrayList<String> out = new ArrayList<>(); if (array != null) for (JsonElement e : array) out.add(e.getAsString()); return out; }
    private static String render(String template, int index, int target) { return template == null ? "" : template.replace("{index}", Integer.toString(index)).replace("{target}", Integer.toString(target)); }
    private static String renderRotation(String template, int cycle, int step, int target) { return template == null ? "" : template.replace("{cycle}", Integer.toString(cycle)).replace("{step}", Integer.toString(step)).replace("{target}", Integer.toString(target)); }
    private static int intValue(JsonObject object, String key, int fallback) { JsonElement value = object.get(key); try { return value == null || value.isJsonNull() ? fallback : value.getAsInt(); } catch (Exception ignored) { return fallback; } }
    private static double doubleValue(JsonObject object, String key, double fallback) { JsonElement value = object.get(key); try { return value == null || value.isJsonNull() ? fallback : value.getAsDouble(); } catch (Exception ignored) { return fallback; } }
    private static boolean booleanValue(JsonObject object, String key, boolean fallback) { JsonElement value = object.get(key); try { return value == null || value.isJsonNull() ? fallback : value.getAsBoolean(); } catch (Exception ignored) { return fallback; } }
    private static String requiredString(JsonObject object, String key, String source) { String value = optionalString(object, key); if (value.isBlank()) throw new IllegalArgumentException("Missing '" + key + "' in " + source); return value; }
    private static String optionalString(JsonObject object, String key) { JsonElement value = object.get(key); return value == null || value.isJsonNull() ? "" : value.getAsString().trim(); }
    private static String clean(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]+", "_"); }
}
