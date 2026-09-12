package dev.aristheg.alphaencounter.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.config.model.BossBarProfile;
import dev.aristheg.alphaencounter.config.model.MessageBundle;
import dev.aristheg.alphaencounter.config.model.MessageProfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class UiConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_ROOT = "/assets/alpha_encounter/defaults/";

    private final AlphaEncounterConfigManager config;
    private final Map<String, BossBarProfile> bossBars = new LinkedHashMap<>();
    private MessageBundle messages = new MessageBundle();
    private MessageBundle fallbackMessages = new MessageBundle();

    public UiConfigManager(AlphaEncounterConfigManager config) {
        this.config = config;
    }

    public synchronized void load() {
        try {
            ensureDefaults();
            String language = config.general().language;
            fallbackMessages = bundledMessages("en_us");
            MessageBundle localizedDefaults = bundledMessages(language);

            Path messageFile = config.root().resolve("messages").resolve(language + ".json");
            boolean fileExisted = Files.exists(messageFile);
            MessageBundle loaded = read(messageFile, MessageBundle.class, null);
            boolean canPersist = loaded != null || !fileExisted;
            if (loaded == null) loaded = copyBundle(localizedDefaults);
            loaded.normalize(language);

            boolean changed = mergeDefaults(loaded, localizedDefaults);
            Path legacyFile = config.root().resolve("messages/legacy.json");
            if (Files.exists(legacyFile)) {
                MessageBundle legacy = read(legacyFile, MessageBundle.class, null);
                if (legacy != null) {
                    legacy.normalize("legacy");
                    for (Map.Entry<String, MessageProfile> entry : legacy.profiles.entrySet()) {
                        if (!loaded.profiles.containsKey(entry.getKey())) {
                            loaded.profiles.put(entry.getKey(), entry.getValue());
                            changed = true;
                        }
                    }
                    for (Map.Entry<String, String> entry : legacy.text.entrySet()) {
                        if (!loaded.text.containsKey(entry.getKey())) {
                            loaded.text.put(entry.getKey(), entry.getValue());
                            changed = true;
                        }
                    }
                }
            }
            messages = loaded;
            if (canPersist && (!fileExisted || changed)) write(messageFile, messages);

            bossBars.clear();
            try (Stream<Path> files = Files.list(config.root().resolve("bossbars"))) {
                for (Path file : files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                    String fileName = file.getFileName().toString();
                    String id = fileName.substring(0, fileName.length() - 5);
                    BossBarProfile profile = read(file, BossBarProfile.class, null);
                    if (profile != null) {
                        profile.normalize(id);
                        bossBars.put(profile.id, profile);
                    }
                }
            }

            AlphaEncounterMod.LOGGER.info(
                "Loaded Alpha-Encounter UI: language={}, messageProfiles={}, textKeys={}, bossBars={}",
                language,
                messages.profiles.size(),
                messages.text.size(),
                bossBars.size()
            );
        } catch (Exception e) {
            AlphaEncounterMod.LOGGER.error("Failed to load Alpha-Encounter UI configuration.", e);
        }
    }

    public MessageProfile messages(String id) {
        MessageProfile profile = messages.profiles.get(id);
        if (profile != null) return profile;
        profile = messages.profiles.get("default");
        return profile == null ? new MessageProfile() : profile;
    }

    public String text(String key) {
        if (key == null || key.isBlank()) return "";
        String value = messages.text.get(key);
        if (value != null) return value;
        value = fallbackMessages.text.get(key);
        return value == null ? "" : value;
    }

    public BossBarProfile bossBar(String id) {
        BossBarProfile profile = bossBars.get(id);
        if (profile != null) return profile;
        profile = bossBars.get("regional");
        if (profile != null) return profile;
        BossBarProfile fallback = new BossBarProfile();
        fallback.normalize("regional");
        return fallback;
    }

    private void ensureDefaults() throws IOException {
        Path messagesDir = config.root().resolve("messages");
        Path bossbarsDir = config.root().resolve("bossbars");
        Files.createDirectories(messagesDir);
        Files.createDirectories(bossbarsDir);

        copyBundledIfMissing(messagesDir.resolve("en_us.json"), DEFAULT_ROOT + "messages/en_us.json");
        copyBundledIfMissing(messagesDir.resolve("vi_vn.json"), DEFAULT_ROOT + "messages/vi_vn.json");
        copyBundledIfMissing(bossbarsDir.resolve("regional.json"), DEFAULT_ROOT + "bossbars/regional.json");
        copyBundledIfMissing(bossbarsDir.resolve("signature.json"), DEFAULT_ROOT + "bossbars/signature.json");
        copyBundledIfMissing(bossbarsDir.resolve("apex.json"), DEFAULT_ROOT + "bossbars/apex.json");
    }

    private MessageBundle bundledMessages(String language) {
        MessageBundle bundle = readResource(DEFAULT_ROOT + "messages/" + language + ".json", MessageBundle.class);
        if (bundle == null && !"en_us".equals(language)) {
            bundle = readResource(DEFAULT_ROOT + "messages/en_us.json", MessageBundle.class);
        }
        if (bundle == null) bundle = new MessageBundle();
        bundle.language = language;
        bundle.normalize(language);
        return bundle;
    }

    private boolean mergeDefaults(MessageBundle target, MessageBundle defaults) {
        boolean changed = false;
        for (Map.Entry<String, MessageProfile> entry : defaults.profiles.entrySet()) {
            if (!target.profiles.containsKey(entry.getKey())) {
                target.profiles.put(entry.getKey(), copyProfile(entry.getValue()));
                changed = true;
            }
        }
        for (Map.Entry<String, String> entry : defaults.text.entrySet()) {
            if (!target.text.containsKey(entry.getKey())) {
                target.text.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private MessageBundle copyBundle(MessageBundle source) {
        MessageBundle copy = GSON.fromJson(GSON.toJson(source), MessageBundle.class);
        if (copy == null) copy = new MessageBundle();
        return copy;
    }

    private MessageProfile copyProfile(MessageProfile source) {
        MessageProfile copy = GSON.fromJson(GSON.toJson(source), MessageProfile.class);
        return copy == null ? new MessageProfile() : copy;
    }

    private <T> T read(Path file, Class<T> type, T fallback) {
        try {
            if (Files.notExists(file)) return fallback;
            T value = GSON.fromJson(Files.readString(file), type);
            return value == null ? fallback : value;
        } catch (Exception e) {
            AlphaEncounterMod.LOGGER.error("Could not read UI config {}", file, e);
            return fallback;
        }
    }

    private <T> T readResource(String resource, Class<T> type) {
        try (InputStream input = UiConfigManager.class.getResourceAsStream(resource)) {
            if (input == null) return null;
            return GSON.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            AlphaEncounterMod.LOGGER.error("Could not read bundled UI resource {}", resource, e);
            return null;
        }
    }

    private void copyBundledIfMissing(Path target, String resource) throws IOException {
        if (Files.exists(target)) return;
        try (InputStream input = UiConfigManager.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing bundled UI resource: " + resource);
            Files.copy(input, target);
        }
    }

    private void write(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(value));
    }
}
