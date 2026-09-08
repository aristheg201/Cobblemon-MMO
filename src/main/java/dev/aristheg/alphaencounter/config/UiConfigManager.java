package dev.aristheg.alphaencounter.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.config.model.BossBarProfile;
import dev.aristheg.alphaencounter.config.model.MessageBundle;
import dev.aristheg.alphaencounter.config.model.MessageProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class UiConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final AlphaEncounterConfigManager config;
    private final Map<String, BossBarProfile> bossBars = new LinkedHashMap<>();
    private MessageBundle messages = new MessageBundle();

    public UiConfigManager(AlphaEncounterConfigManager config) { this.config = config; }

    public synchronized void load() {
        try {
            ensureDefaults();
            String language = config.general().language;
            messages = read(config.root().resolve("messages").resolve(language + ".json"), MessageBundle.class, defaultMessages(language));
            messages.normalize(language);
            bossBars.clear();
            try (Stream<Path> files = Files.list(config.root().resolve("bossbars"))) {
                for (Path file : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    String id = file.getFileName().toString().replaceFirst("\.json$", "");
                    BossBarProfile profile = read(file, BossBarProfile.class, null);
                    if (profile != null) { profile.normalize(id); bossBars.put(profile.id, profile); }
                }
            }
            AlphaEncounterMod.LOGGER.info("Loaded Alpha-Encounter UI: language={}, messageProfiles={}, bossBars={}", language, messages.profiles.size(), bossBars.size());
        } catch (Exception e) {
            AlphaEncounterMod.LOGGER.error("Failed to load Alpha-Encounter UI configuration.", e);
        }
    }

    public MessageProfile messages(String id) {
        MessageProfile p = messages.profiles.get(id);
        if (p != null) return p;
        p = messages.profiles.get("default");
        return p == null ? new MessageProfile() : p;
    }

    public BossBarProfile bossBar(String id) {
        BossBarProfile p = bossBars.get(id);
        if (p != null) return p;
        p = bossBars.get("regional");
        return p == null ? defaultBossBar("regional", "YELLOW", 72) : p;
    }

    private void ensureDefaults() throws Exception {
        Path messagesDir = config.root().resolve("messages");
        Path bossbarsDir = config.root().resolve("bossbars");
        Files.createDirectories(messagesDir);
        Files.createDirectories(bossbarsDir);
        writeIfMissing(messagesDir.resolve("en_us.json"), defaultMessages("en_us"));
        writeIfMissing(messagesDir.resolve("vi_vn.json"), defaultMessages("vi_vn"));
        writeIfMissing(bossbarsDir.resolve("regional.json"), defaultBossBar("regional", "YELLOW", 72));
        writeIfMissing(bossbarsDir.resolve("signature.json"), defaultBossBar("signature", "BLUE", 88));
        writeIfMissing(bossbarsDir.resolve("apex.json"), defaultBossBar("apex", "PURPLE", 112));
    }

    private MessageBundle defaultMessages(String language) {
        MessageBundle bundle = new MessageBundle();
        bundle.language = language;
        if ("vi_vn".equals(language.toLowerCase(Locale.ROOT))) {
            bundle.profiles.put("default", profile("default",
                "<gold><bold>ALPHA</bold></gold> <gray><ae_name> đã xuất hiện tại <ae_biome>.</gray>",
                "<red><ae_name></red> <gray>đang săn đuổi <player_name>.</gray>",
                "<yellow><player_name></yellow> <gray>đã giao chiến với <ae_name>.</gray>",
                "", "<gold><bold><ae_name></bold></gold> <yellow>đã bị đánh bại!</yellow>",
                "<green><ae_name></green> <gray>có thể bị bắt trong <ae_catch_seconds> giây.</gray>"));
        } else {
            bundle.profiles.put("default", profile("default",
                "<gold><bold>ALPHA</bold></gold> <gray><ae_name> has appeared in <ae_biome>.</gray>",
                "<red><ae_name></red> <gray>is hunting <player_name>.</gray>",
                "<yellow><player_name></yellow> <gray>engaged <ae_name>.</gray>",
                "", "<gold><bold><ae_name></bold></gold> <yellow>has been defeated!</yellow>",
                "<green><ae_name></green> <gray>can be captured for <ae_catch_seconds> seconds.</gray>"));
        }
        MessageProfile base = bundle.profiles.get("default");
        bundle.profiles.put("regional", copy("regional", base));
        bundle.profiles.put("signature", copy("signature", base));
        bundle.profiles.put("apex", copy("apex", base));
        return bundle;
    }

    private MessageProfile profile(String id, String spawn, String aggro, String battleStart, String battleEnd, String defeat, String catchAvailable) {
        MessageProfile p = new MessageProfile(); p.id=id; p.spawn=spawn; p.aggro=aggro; p.battleStart=battleStart; p.battleEnd=battleEnd; p.defeat=defeat; p.catchAvailable=catchAvailable; return p;
    }
    private MessageProfile copy(String id, MessageProfile from) { return profile(id, from.spawn, from.aggro, from.battleStart, from.battleEnd, from.defeat, from.catchAvailable); }
    private BossBarProfile defaultBossBar(String id, String color, double range) { BossBarProfile p=new BossBarProfile(); p.id=id; p.color=color; p.range=range; p.title="<yellow><bold><ae_name></bold></yellow> <dark_gray>•</dark_gray> <white><ae_hp_percent>%</white> <gray>(<ae_hp>/<ae_max_hp>)</gray>"; return p; }
    private <T> T read(Path file, Class<T> type, T fallback) { try { T value=GSON.fromJson(Files.readString(file), type); return value==null?fallback:value; } catch(Exception e){ AlphaEncounterMod.LOGGER.error("Could not read UI config {}", file, e); return fallback; } }
    private void writeIfMissing(Path file, Object value) throws Exception { if (Files.notExists(file)) Files.writeString(file, GSON.toJson(value)); }
}
