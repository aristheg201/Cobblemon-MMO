from pathlib import Path
import re

ROOT = Path('.')

def write(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')

def replace(path, old, new, count=-1):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise RuntimeError(f'Expected block not found in {path}: {old[:120]!r}')
    s = s.replace(old, new, count)
    p.write_text(s, encoding='utf-8')

def regex(path, pattern, repl, flags=re.S):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    out, n = re.subn(pattern, repl, s, flags=flags)
    if n != 1:
        raise RuntimeError(f'Expected exactly one regex match in {path}, got {n}: {pattern[:100]}')
    p.write_text(out, encoding='utf-8')

# Core config models.
write('src/main/java/dev/aristheg/alphaencounter/config/model/GeneralConfig.java', '''package dev.aristheg.alphaencounter.config.model;

public final class GeneralConfig {
    public String language = "en_us";
    public boolean usePlaceholderApi = true;
    public int spawnCheckIntervalTicks = 100;
    public double spawnAttemptChance = 0.08;
    public int huntUpdateIntervalTicks = 10;
    public int bossBarUpdateIntervalTicks = 5;
    public int stateSaveIntervalTicks = 1200;
    public boolean loadEncounterChunksOnRestore = true;
    public int missingEntityGraceTicks = 200;
    public int battlePendingTimeoutTicks = 60;

    public void normalize() {
        if (language == null || language.isBlank()) language = "en_us";
        language = language.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        spawnCheckIntervalTicks = Math.max(20, spawnCheckIntervalTicks);
        spawnAttemptChance = Math.max(0.0, Math.min(1.0, spawnAttemptChance));
        huntUpdateIntervalTicks = Math.max(1, huntUpdateIntervalTicks);
        bossBarUpdateIntervalTicks = Math.max(1, bossBarUpdateIntervalTicks);
        stateSaveIntervalTicks = Math.max(200, stateSaveIntervalTicks);
        missingEntityGraceTicks = Math.max(40, missingEntityGraceTicks);
        battlePendingTimeoutTicks = Math.max(20, battlePendingTimeoutTicks);
    }
}
''')

write('src/main/java/dev/aristheg/alphaencounter/config/model/TierConfig.java', '''package dev.aristheg.alphaencounter.config.model;

public final class TierConfig {
    public String id = "regional";
    public float healthMultiplier = 4.0f;
    public String behaviour = "passive";
    public String bossBarProfile = "";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        healthMultiplier = Math.max(1.0f, healthMultiplier);
        if (behaviour == null || behaviour.isBlank()) behaviour = "passive";
        if (bossBarProfile == null || bossBarProfile.isBlank()) bossBarProfile = id;
    }
}
''')

write('src/main/java/dev/aristheg/alphaencounter/config/model/EncounterDefinition.java', '''package dev.aristheg.alphaencounter.config.model;

import java.util.ArrayList;
import java.util.List;

public final class EncounterDefinition {
    public String id = "unnamed";
    public String displayName = "Unnamed Alpha";
    public boolean enabled = true;
    public String tier = "regional";
    public String pokemon = "pikachu level=50 alpha=true";
    public String messageProfile = "";
    public String bossBarProfile = "";
    public Spawn spawn = new Spawn();
    public Animations animations = new Animations();
    public boolean catchable = false;
    public int catchPhaseSeconds = 0;
    public float catchHealthPercent = 0.10f;
    public boolean rewardOnAdminDefeat = false;
    public List<String> rewardCommands = new ArrayList<>();
    public transient String categoryId = "misc";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        if (displayName == null || displayName.isBlank()) displayName = id;
        if (tier == null || tier.isBlank()) tier = "regional";
        if (pokemon == null) pokemon = "";
        if (messageProfile == null || messageProfile.isBlank()) messageProfile = tier;
        if (bossBarProfile == null || bossBarProfile.isBlank()) bossBarProfile = tier;
        if (spawn == null) spawn = new Spawn();
        spawn.normalize();
        if (animations == null) animations = new Animations();
        animations.normalize();
        catchPhaseSeconds = Math.max(0, catchPhaseSeconds);
        catchHealthPercent = Math.max(0.01f, Math.min(1.0f, catchHealthPercent));
        if (rewardCommands == null) rewardCommands = new ArrayList<>();
    }

    public static final class Spawn {
        public List<String> dimensions = new ArrayList<>();
        public List<String> biomes = new ArrayList<>();
        public double weight = 1.0;
        public int minDistance = 24;
        public int maxDistance = 64;
        public int maxActive = 1;
        public long globalCooldownTicks = 12000;

        public void normalize() {
            if (dimensions == null) dimensions = new ArrayList<>();
            if (biomes == null) biomes = new ArrayList<>();
            weight = Math.max(0.0, weight);
            minDistance = Math.max(8, minDistance);
            maxDistance = Math.max(minDistance, maxDistance);
            maxActive = Math.max(1, maxActive);
            globalCooldownTicks = Math.max(0, globalCooldownTicks);
        }
    }

    public static final class Animations {
        public String aggro = "cry";
        public String hit = "";
        public String battleStart = "";
        public String battleEnd = "";
        public String defeat = "";

        public void normalize() {
            if (aggro == null) aggro = "";
            if (hit == null) hit = "";
            if (battleStart == null) battleStart = "";
            if (battleEnd == null) battleEnd = "";
            if (defeat == null) defeat = "";
        }
    }
}
''')

write('src/main/java/dev/aristheg/alphaencounter/config/model/MessageProfile.java', '''package dev.aristheg.alphaencounter.config.model;

public final class MessageProfile {
    public String id = "default";
    public String spawn = "";
    public String aggro = "";
    public String battleStart = "";
    public String battleEnd = "";
    public String defeat = "";
    public String catchAvailable = "";
    public String despawn = "";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        if (spawn == null) spawn = "";
        if (aggro == null) aggro = "";
        if (battleStart == null) battleStart = "";
        if (battleEnd == null) battleEnd = "";
        if (defeat == null) defeat = "";
        if (catchAvailable == null) catchAvailable = "";
        if (despawn == null) despawn = "";
    }
}
''')

write('src/main/java/dev/aristheg/alphaencounter/config/model/MessageBundle.java', '''package dev.aristheg.alphaencounter.config.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageBundle {
    public String language = "en_us";
    public Map<String, MessageProfile> profiles = new LinkedHashMap<>();

    public void normalize(String fallbackLanguage) {
        if (language == null || language.isBlank()) language = fallbackLanguage;
        if (profiles == null) profiles = new LinkedHashMap<>();
        profiles.entrySet().removeIf(e -> e.getValue() == null);
        for (Map.Entry<String, MessageProfile> entry : profiles.entrySet()) entry.getValue().normalize(entry.getKey());
    }
}
''')

write('src/main/java/dev/aristheg/alphaencounter/config/model/BossBarProfile.java', '''package dev.aristheg.alphaencounter.config.model;

public final class BossBarProfile {
    public String id = "regional";
    public boolean enabled = true;
    public String title = "<yellow><bold><ae_name></bold></yellow> <dark_gray>•</dark_gray> <white><ae_hp_percent>%</white>";
    public double range = 72.0;
    public String color = "YELLOW";
    public String style = "PROGRESS";

    public void normalize(String fallbackId) {
        if (id == null || id.isBlank()) id = fallbackId;
        if (title == null) title = "";
        range = Math.max(16.0, range);
        if (color == null || color.isBlank()) color = "YELLOW";
        if (style == null || style.isBlank()) style = "PROGRESS";
    }
}
''')

write('src/main/java/dev/aristheg/alphaencounter/config/UiConfigManager.java', '''package dev.aristheg.alphaencounter.config;

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
                    String id = file.getFileName().toString().replaceFirst("\\.json$", "");
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
''')

write('src/main/java/dev/aristheg/alphaencounter/text/TextContext.java', '''package dev.aristheg.alphaencounter.text;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.aristheg.alphaencounter.config.model.EncounterDefinition;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public record TextContext(MinecraftServer server, ServerPlayerEntity player, ActiveEncounter encounter, EncounterDefinition definition, Pokemon pokemon, PokemonEntity entity, String biome) {}
''')

write('src/main/java/dev/aristheg/alphaencounter/integration/PlaceholderIntegration.java', '''package dev.aristheg.alphaencounter.integration;

import dev.aristheg.alphaencounter.text.TextContext;
import net.minecraft.text.Text;

public interface PlaceholderIntegration {
    PlaceholderIntegration NONE = new PlaceholderIntegration() {
        public Text parse(Text input, TextContext context) { return input; }
        public boolean available() { return false; }
        public String status() { return "not installed"; }
    };
    Text parse(Text input, TextContext context);
    boolean available();
    String status();
}
''')

write('src/main/java/dev/aristheg/alphaencounter/integration/PlaceholderApiIntegration.java', '''package dev.aristheg.alphaencounter.integration;

import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import dev.aristheg.alphaencounter.runtime.EncounterRuntime;
import dev.aristheg.alphaencounter.text.TextContext;
import dev.aristheg.alphaencounter.text.TextService;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class PlaceholderApiIntegration implements PlaceholderIntegration {
    private static final List<String> TARGET_KEYS = List.of("name","id","tier","state","hp","max_hp","hp_percent","species","level","aspects","dimension","biome","x","y","z","distance","participants","category","pokemon_uuid","entity_uuid");
    private final EncounterRuntime runtime;
    private final TextService textService;
    public PlaceholderApiIntegration(EncounterRuntime runtime, TextService textService) { this.runtime=runtime; this.textService=textService; register(); }
    private void register() {
        Placeholders.register(Identifier.of(AlphaEncounterMod.MOD_ID,"active_count"), (ctx,arg)->PlaceholderResult.value(Text.literal(Integer.toString(runtime.activeCount()))));
        Placeholders.register(Identifier.of(AlphaEncounterMod.MOD_ID,"cooldown_ticks"), (ctx,arg)-> arg==null||arg.isBlank()?PlaceholderResult.invalid("Expected encounter id"):PlaceholderResult.value(Text.literal(Long.toString(runtime.cooldownRemainingTicks(firstArg(arg))))));
        Placeholders.register(Identifier.of(AlphaEncounterMod.MOD_ID,"cooldown_seconds"), (ctx,arg)-> arg==null||arg.isBlank()?PlaceholderResult.invalid("Expected encounter id"):PlaceholderResult.value(Text.literal(Long.toString((runtime.cooldownRemainingTicks(firstArg(arg))+19L)/20L))));
        for(String key:TARGET_KEYS) Placeholders.register(Identifier.of(AlphaEncounterMod.MOD_ID,key),(ctx,arg)->targetValue(ctx,arg,key));
        AlphaEncounterMod.LOGGER.info("Registered {} Text Placeholder API placeholders.", TARGET_KEYS.size()+3);
    }
    private PlaceholderResult targetValue(PlaceholderContext ctx,String arg,String key){ ActiveEncounter active=runtime.resolveExternalTarget(ctx.source(),arg==null||arg.isBlank()?"nearest":firstArg(arg)); if(active==null)return PlaceholderResult.invalid("No matching Alpha-Encounter"); TextContext tc=runtime.textContext(ctx.server(),active,ctx.player()); return PlaceholderResult.value(Text.literal(textService.value(tc,key))); }
    private String firstArg(String arg){ String t=arg.trim(); int i=t.indexOf(' '); return i<0?t:t.substring(0,i); }
    public Text parse(Text input,TextContext context){ if(context==null||context.server()==null)return input; PlaceholderContext pc=context.player()!=null?PlaceholderContext.of(context.player()):PlaceholderContext.of(context.server()); return Placeholders.parseText(input,pc); }
    public boolean available(){return true;} public String status(){return "loaded";}
}
''')

write('src/main/java/dev/aristheg/alphaencounter/text/TextService.java', '''package dev.aristheg.alphaencounter.text;

import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.config.AlphaEncounterConfigManager;
import dev.aristheg.alphaencounter.integration.PlaceholderIntegration;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import dev.aristheg.alphaencounter.runtime.EncounterRuntime;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Locale;

public final class TextService {
    private static final List<String> KEYS=List.of("name","id","tier","state","hp","max_hp","hp_percent","species","level","aspects","dimension","biome","x","y","z","distance","participants","category","pokemon_uuid","entity_uuid","catch_seconds","player_name");
    private final AlphaEncounterConfigManager config;
    private final MiniMessage miniMessage=MiniMessage.miniMessage();
    private PlaceholderIntegration placeholders=PlaceholderIntegration.NONE;
    private FabricServerAudiences audiences;
    public TextService(AlphaEncounterConfigManager config){this.config=config;}
    public void initialize(EncounterRuntime runtime){ if(!FabricLoader.getInstance().isModLoaded("placeholder-api")){AlphaEncounterMod.LOGGER.info("Text Placeholder API not installed; integration disabled.");return;} try{Class<?> type=Class.forName("dev.aristheg.alphaencounter.integration.PlaceholderApiIntegration");Constructor<?> c=type.getConstructor(EncounterRuntime.class,TextService.class);placeholders=(PlaceholderIntegration)c.newInstance(runtime,this);}catch(Throwable t){AlphaEncounterMod.LOGGER.error("Could not initialize Text Placeholder API integration.",t);} }
    public void start(MinecraftServer server){audiences=FabricServerAudiences.of(server);} public void stop(){if(audiences!=null){try{audiences.close();}catch(Throwable ignored){}audiences=null;}}
    public Text render(String raw,TextContext context){if(raw==null||raw.isBlank())return Text.literal("");try{Component component=miniMessage.deserialize(raw,resolver(context));Text nativeText=audiences==null?Text.literal(raw):audiences.toNative(component);if(config.general().usePlaceholderApi&&placeholders.available())nativeText=placeholders.parse(nativeText,context);return nativeText;}catch(Throwable t){AlphaEncounterMod.LOGGER.warn("MiniMessage render failed: {}",raw,t);return Text.literal(raw);}}
    public void broadcast(MinecraftServer server,String raw,TextContext context){if(server!=null&&raw!=null&&!raw.isBlank())server.getPlayerManager().broadcast(render(raw,context),false);}
    public String integrationStatus(){return "MiniMessage=enabled, PlaceholderAPI="+placeholders.status()+", externalParsing="+(config.general().usePlaceholderApi?"enabled":"disabled");}
    public String value(TextContext c,String key){if(c==null||key==null)return "";ActiveEncounter a=c.encounter();var d=c.definition();var p=c.pokemon();var e=c.entity();return switch(key){case"name"->d==null?(a==null?"":a.definitionId):d.displayName;case"id"->a==null?(d==null?"":d.id):a.definitionId;case"tier"->a==null?(d==null?"":d.tier):a.tierId;case"state"->a==null?"":a.state.name().toLowerCase(Locale.ROOT);case"hp"->a==null?"0":Integer.toString(Math.max(0,Math.round(a.hp)));case"max_hp"->a==null?"0":Integer.toString(Math.max(0,Math.round(a.maxHp)));case"hp_percent"->a==null||a.maxHp<=0?"0.0":one(a.hp*100.0/a.maxHp);case"species"->p==null?"":p.getSpecies().getName();case"level"->p==null?"0":Integer.toString(p.getLevel());case"aspects"->p==null?"":String.join(",",p.getAspects());case"dimension"->a==null?"":a.dimension;case"biome"->c.biome()==null?"":c.biome();case"x"->a==null?"0":one(a.x);case"y"->a==null?"0":one(a.y);case"z"->a==null?"0":one(a.z);case"distance"->c.player()==null||e==null?"":one(Math.sqrt(e.squaredDistanceTo(c.player())));case"participants"->a==null?"0":Integer.toString(a.participants.size());case"category"->d==null||d.categoryId==null?"":d.categoryId;case"pokemon_uuid"->a==null||a.pokemonId==null?"":a.pokemonId.toString();case"entity_uuid"->a==null||a.entityId==null?"":a.entityId.toString();case"catch_seconds"->d==null?"0":Integer.toString(d.catchPhaseSeconds);case"player_name"->c.player()==null?"":c.player().getName().getString();default->"";};}
    private TagResolver resolver(TextContext c){TagResolver.Builder b=TagResolver.builder();for(String key:KEYS){String tag=key.equals("player_name")?key:"ae_"+key;b.resolver(Placeholder.unparsed(tag,value(c,key)));}return b.build();}
    private String one(double v){return String.format(Locale.ROOT,"%.1f",v);}
}
''')

# Runtime state: no phase recovery.
write('src/main/java/dev/aristheg/alphaencounter/runtime/EncounterState.java', '''package dev.aristheg.alphaencounter.runtime;
public enum EncounterState { IDLE, HUNT, BATTLE_PENDING, BATTLE, DEFEATED }
''')

write('src/main/java/dev/aristheg/alphaencounter/runtime/ActiveEncounter.java', '''package dev.aristheg.alphaencounter.runtime;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.entity.boss.ServerBossBar;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ActiveEncounter {
    public final String definitionId; public final String tierId; public UUID entityId; public final UUID pokemonId; public final float healthMultiplier;
    public float maxHp=-1f,hp=-1f; public EncounterState state=EncounterState.IDLE;
    public UUID targetPlayer,pendingBattlePlayer,lastBattlePlayer; public long battleAtTick,pendingSinceTick,nextReengageTick,nextFieldAttackTick,nextPathRefreshTick,missingSinceTick;
    public int lastPokemonHealth=-1,battleLocalMax=-1; public boolean defeatPending;
    public final Set<UUID> participants=new HashSet<>(); public String dimension="minecraft:overworld"; public double x,y,z;
    public transient PokemonEntity entityRef; public transient Pokemon pokemonRef; public transient ServerBossBar bossBar; public transient Set<UUID> bossBarViewers=new HashSet<>();
    public ActiveEncounter(String definitionId,String tierId,UUID entityId,UUID pokemonId,float healthMultiplier){this.definitionId=definitionId;this.tierId=tierId;this.entityId=entityId;this.pokemonId=pokemonId;this.healthMultiplier=healthMultiplier;}
}
''')

runtime='src/main/java/dev/aristheg/alphaencounter/runtime/EncounterRuntime.java'
replace(runtime, '    private long phaseRecoveries;\n', '')
replace(runtime, '        processPhaseRecoveries(server);\n', '')
replace(runtime, ' || active.state == EncounterState.PHASE_RECOVERY', '')

regex(runtime, r'    private void syncBattleHealth\(ActiveEncounter active, int current\) \{.*?\n    \}\n\n    private void onBattleFainted', '''    private void syncBattleHealth(ActiveEncounter active, int current) {
        if (active.maxHp <= 0f) return;
        Pokemon pokemon = resolvePokemon(active);
        int localMax = active.battleLocalMax > 0 ? active.battleLocalMax : Math.max(1, pokemon.getMaxHealth());
        active.battleLocalMax = localMax;
        active.hp = Math.max(0f, Math.min(active.maxHp, active.maxHp * (Math.max(0, current) / (float)localMax)));
        active.lastPokemonHealth = current;
        stateDirty = true;
    }

    private void onBattleFainted''')

regex(runtime, r'    private void onBattleFainted\(BattleFaintedEvent event\) \{.*?\n    \}\n\n    private void onBattleFled', '''    private void onBattleFainted(BattleFaintedEvent event) {
        ActiveEncounter active = activeByPokemon.get(event.getKilled().getOriginalPokemon().getUuid());
        if (active == null) return;
        active.hp = 0f;
        active.lastPokemonHealth = 0;
        active.defeatPending = true;
        stateDirty = true;
    }

    private void onBattleFled''')

regex(runtime, r'    private void onBattleVictory\(BattleVictoryEvent event\) \{.*?\n    \}\n\n    private ActiveEncounter encounterFromBattle', '''    private void onBattleVictory(BattleVictoryEvent event) {
        ActiveEncounter active = encounterFromBattle(event.getBattle());
        if (active == null) return;
        Pokemon pokemon = resolvePokemon(active);
        syncBattleHealth(active, pokemon.getCurrentHealth());
        if (pokemon.getCurrentHealth() <= 0 || active.hp <= 0.5f) {
            active.hp = 0f;
            active.defeatPending = true;
        }
        stateDirty = true;
    }

    private ActiveEncounter encounterFromBattle''')

regex(runtime, r'    private void finishBattle\(MinecraftServer server, ActiveEncounter active\) \{.*?\n    \}\n\n    private void processPhaseRecoveries\(MinecraftServer server\) \{.*?\n    \}\n\n    private void prepareLocalBar', '''    private void finishBattle(MinecraftServer server, ActiveEncounter active) {
        battleEnds++;
        PokemonEntity entity = resolveEntity(server, active);
        if (entity != null) CobblemonBridge.playAnimation(entity, animation(active, "battleEnd"));
        if (active.defeatPending || active.hp <= 0.5f || resolvePokemon(active).getCurrentHealth() <= 0) {
            active.hp = 0f;
            defeatEncounter(server, active, false);
            return;
        }
        active.state = EncounterState.HUNT;
        active.targetPlayer = active.lastBattlePlayer;
        active.nextReengageTick = tick + config.behaviourForTier(active.tierId).reengageCooldownTicks;
        active.lastPokemonHealth = -1;
        active.battleLocalMax = -1;
        active.defeatPending = false;
        broadcastProfile(server, active, active.lastBattlePlayer, "battleEnd");
        stateDirty = true;
    }

    private void prepareLocalBar''')

regex(runtime, r'    private void prepareLocalBar\(ActiveEncounter active, Pokemon pokemon\) \{.*?\n    \}', '''    private void prepareLocalBar(ActiveEncounter active, Pokemon pokemon) {
        initializeHealth(active, pokemon);
        int localMax = Math.max(1, pokemon.getMaxHealth());
        int localHp = Math.max(1, Math.min(localMax, (int)Math.ceil(localMax * (active.hp / active.maxHp))));
        pokemon.setCurrentHealth(localHp);
        active.battleLocalMax = localMax;
        active.lastPokemonHealth = localHp;
        active.defeatPending = false;
    }''')

replace(runtime, '            broadcast(world.getServer(), format(def.spawnMessage, active, null));', '            broadcastProfile(world.getServer(), active, null, "spawn");')
replace(runtime, '            if (newTarget) CobblemonBridge.playAnimation(pokemon, animation(active, "aggro"));', '            if (newTarget) { CobblemonBridge.playAnimation(pokemon, animation(active, "aggro")); broadcastProfile(server, active, target.getUuid(), "aggro"); }')
replace(runtime, '            if (invoked) {\n                active.lastBattlePlayer = player.getUuid();\n                active.participants.add(player.getUuid());\n            }', '            if (invoked) {\n                active.lastBattlePlayer = player.getUuid();\n                active.participants.add(player.getUuid());\n                broadcastProfile(server, active, player.getUuid(), "battleStart");\n            }')

regex(runtime, r'    private void updateBossBars\(MinecraftServer server\) \{.*?\n    \}\n\n    private void setupBossBar', '''    private void updateBossBars(MinecraftServer server) {
        for (ActiveEncounter active : activeByPokemon.values()) {
            if (active.bossBar == null) continue;
            PokemonEntity pokemon = resolveEntity(server, active);
            if (pokemon == null) continue;
            EncounterDefinition def = config.encounter(active.definitionId);
            if (def == null) continue;
            var profile = AlphaEncounterMod.UI.bossBar(def.bossBarProfile);
            active.bossBar.setPercent(active.maxHp <= 0 ? 1f : Math.max(0f, Math.min(1f, active.hp / active.maxHp)));
            active.bossBar.setName(AlphaEncounterMod.TEXT.render(profile.title, textContext(server, active, null)));
            Set<UUID> desired = new HashSet<>(active.participants);
            double rangeSq = profile.range * profile.range;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) if (player.getWorld() == pokemon.getWorld() && pokemon.squaredDistanceTo(player) <= rangeSq) desired.add(player.getUuid());
            for (UUID id : desired) if (active.bossBarViewers.add(id)) { ServerPlayerEntity player = server.getPlayerManager().getPlayer(id); if (player != null) active.bossBar.addPlayer(player); }
            Iterator<UUID> it = active.bossBarViewers.iterator();
            while (it.hasNext()) { UUID id=it.next(); if(desired.contains(id))continue; ServerPlayerEntity player=server.getPlayerManager().getPlayer(id); if(player!=null)active.bossBar.removePlayer(player); it.remove(); }
        }
    }

    private void setupBossBar''')

regex(runtime, r'    private void setupBossBar\(ActiveEncounter active, EncounterDefinition def, TierConfig tier\) \{.*?\n    \}', '''    private void setupBossBar(ActiveEncounter active, EncounterDefinition def, TierConfig tier) {
        var profile = AlphaEncounterMod.UI.bossBar(def.bossBarProfile == null || def.bossBarProfile.isBlank() ? tier.bossBarProfile : def.bossBarProfile);
        if (!profile.enabled) return;
        BossBar.Color color; BossBar.Style style;
        try { color = BossBar.Color.valueOf(profile.color.toUpperCase(Locale.ROOT)); } catch(Exception ignored){ color=BossBar.Color.PURPLE; }
        try { style = BossBar.Style.valueOf(profile.style.toUpperCase(Locale.ROOT)); } catch(Exception ignored){ style=BossBar.Style.PROGRESS; }
        active.bossBar = new ServerBossBar(Text.literal(def.displayName), color, style);
    }''')

replace(runtime, '            broadcast(server, format(def.defeatMessage, active, null));', '            broadcastProfile(server, active, null, "defeat");')
replace(runtime, '                broadcast(server, format(def.catchMessage, active, null));', '                broadcastProfile(server, active, null, "catchAvailable");')
replace(runtime, '            if (active.state == EncounterState.PHASE_RECOVERY || active.state == EncounterState.BATTLE) continue;', '            if (active.state == EncounterState.BATTLE) continue;')
replace(runtime, 'outside battle/recovery', 'outside battle')

regex(runtime, r'    private String format\(String raw, ActiveEncounter active, ServerPlayerEntity player\) \{.*?\n    \}\n\n    private void broadcast\(MinecraftServer server, String message\) \{.*?\n    \}', '''    private void broadcastProfile(MinecraftServer server, ActiveEncounter active, UUID playerId, String key) {
        EncounterDefinition def = config.encounter(active.definitionId);
        if (def == null) return;
        var profile = AlphaEncounterMod.UI.messages(def.messageProfile);
        String raw = switch (key) { case "spawn" -> profile.spawn; case "aggro" -> profile.aggro; case "battleStart" -> profile.battleStart; case "battleEnd" -> profile.battleEnd; case "defeat" -> profile.defeat; case "catchAvailable" -> profile.catchAvailable; case "despawn" -> profile.despawn; default -> ""; };
        ServerPlayerEntity player = playerId == null ? null : server.getPlayerManager().getPlayer(playerId);
        AlphaEncounterMod.TEXT.broadcast(server, raw, textContext(server, active, player));
    }

    public dev.aristheg.alphaencounter.text.TextContext textContext(MinecraftServer server, ActiveEncounter active, ServerPlayerEntity player) {
        Pokemon pokemon = resolvePokemon(active);
        PokemonEntity entity = resolveEntity(server, active);
        EncounterDefinition def = config.encounter(active.definitionId);
        String biome = "";
        if (entity != null) biome = entity.getWorld().getBiome(entity.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("");
        return new dev.aristheg.alphaencounter.text.TextContext(server, player, active, def, pokemon, entity, biome);
    }''')

replace(runtime, '    public String perfLine() { return "AlphaEncounter active="+activeByPokemon.size()+" spawnChecks="+spawnChecks+" spawned="+spawnSuccess+" adminSpawned="+adminSpawns+" worldHits="+interceptedHits+" fieldAttacks="+fieldAttacks+" battlesQueued="+battlesQueued+" battlesStarted="+battlesStarted+" battleEnds="+battleEnds+" phaseRecoveries="+phaseRecoveries+" defeats="+defeats; }\n    public void resetCounters() { spawnChecks=spawnSuccess=interceptedHits=fieldAttacks=battlesQueued=battlesStarted=battleEnds=phaseRecoveries=defeats=adminSpawns=0; }', '    public int activeCount() { return activeByPokemon.size(); }\n    public long cooldownRemainingTicks(String id) { return Math.max(0L, globalCooldownUntil.getOrDefault(id, 0L) - tick); }\n    public ActiveEncounter resolveExternalTarget(net.minecraft.server.command.ServerCommandSource source, String token) { return resolveAdminTarget(source, token); }\n    public String perfLine() { return "AlphaEncounter active="+activeByPokemon.size()+" spawnChecks="+spawnChecks+" spawned="+spawnSuccess+" adminSpawned="+adminSpawns+" worldHits="+interceptedHits+" fieldAttacks="+fieldAttacks+" battlesQueued="+battlesQueued+" battlesStarted="+battlesStarted+" battleEnds="+battleEnds+" defeats="+defeats+" "+AlphaEncounterMod.TEXT.integrationStatus(); }\n    public void resetCounters() { spawnChecks=spawnSuccess=interceptedHits=fieldAttacks=battlesQueued=battlesStarted=battleEnds=defeats=adminSpawns=0; }')

# Bootstrap services.
write('src/main/java/dev/aristheg/alphaencounter/AlphaEncounterMod.java', '''package dev.aristheg.alphaencounter;

import dev.aristheg.alphaencounter.command.AdminCommands;
import dev.aristheg.alphaencounter.config.AlphaEncounterConfigManager;
import dev.aristheg.alphaencounter.config.UiConfigManager;
import dev.aristheg.alphaencounter.runtime.EncounterRuntime;
import dev.aristheg.alphaencounter.text.TextService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AlphaEncounterMod implements ModInitializer {
    public static final String MOD_ID="alpha_encounter";
    public static final Logger LOGGER=LoggerFactory.getLogger("Alpha-Encounter");
    public static final AlphaEncounterConfigManager CONFIG=new AlphaEncounterConfigManager();
    public static final UiConfigManager UI=new UiConfigManager(CONFIG);
    public static final EncounterRuntime RUNTIME=new EncounterRuntime(CONFIG);
    public static final TextService TEXT=new TextService(CONFIG);
    @Override public void onInitialize(){ CONFIG.load(); UI.load(); RUNTIME.initialize(); TEXT.initialize(RUNTIME); ServerLifecycleEvents.SERVER_STARTING.register(TEXT::start); ServerTickEvents.END_SERVER_TICK.register(RUNTIME::tick); ServerLifecycleEvents.SERVER_STOPPING.register(RUNTIME::saveState); ServerLifecycleEvents.SERVER_STOPPED.register(server->TEXT.stop()); CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->AdminCommands.register(dispatcher)); LOGGER.info("Alpha-Encounter initialized: single-KO shared HP, MiniMessage, optional Text Placeholder API, vanilla field combat."); }
}
''')

# Build: bundle Adventure, compile against optional Placeholder API.
build=Path('build.gradle').read_text(encoding='utf-8')
build=build.replace("    maven { url = 'https://maven.fabricmc.net/' }", "    maven { url = 'https://maven.fabricmc.net/' }\n    maven { url = 'https://maven.nucleoid.xyz/' }")
build=build.replace('    compileOnly "org.jetbrains.kotlin:kotlin-stdlib:2.2.20"', '    compileOnly "org.jetbrains.kotlin:kotlin-stdlib:2.2.20"\n    // Adventure/MiniMessage is bundled so formatting works without another server mod.\n    modImplementation include("net.kyori:adventure-platform-fabric:5.14.2")\n    // Text Placeholder API remains optional at runtime.\n    modCompileOnly "eu.pb4:placeholder-api:2.4.2+1.21"')
Path('build.gradle').write_text(build,encoding='utf-8')

# Optional dependency hint.
mod=Path('src/main/resources/fabric.mod.json').read_text(encoding='utf-8')
mod=mod.replace('  "depends": {', '  "suggests": {\n    "placeholder-api": ">=2.4.2"\n  },\n  "depends": {')
Path('src/main/resources/fabric.mod.json').write_text(mod,encoding='utf-8')

# README corrections: no multiphase, integration surface.
readme=Path('README.md').read_text(encoding='utf-8')
readme=re.sub(r'## Encounter lifecycle.*?## Performance behavior', '''## Encounter lifecycle\n\n`IDLE -> HUNT -> BATTLE_PENDING -> BATTLE -> HUNT/DEFEATED`\n\n- Alpha encounters attack players in the world with vanilla Minecraft damage and do not auto-start a battle by proximity.\n- A player damaging a managed Alpha queues Cobblemon PVE battle on the next server tick.\n- Shared HP is mapped proportionally to one Cobblemon battle HP bar. There is no multi-phase KO or phase respawn.\n- A local battle KO is the encounter defeat. If the battle ends before KO, remaining local HP maps back to shared HP and the encounter returns to HUNT.\n- Optional catch and reward phases run after the single final defeat.\n\n## Text and integrations\n\n- All encounter messages and bossbar titles use MiniMessage through bundled Adventure Fabric.\n- Text Placeholder API (`placeholder-api`) is a soft dependency. If installed, Alpha-Encounter registers global placeholders and parses placeholders from other mods in its configured messages.\n- Message files live under `config/alpha-encounter/messages/<language>.json`.\n- Bossbar profiles live under `config/alpha-encounter/bossbars/*.json`.\n- Encounter files reference `messageProfile` and `bossBarProfile` instead of carrying user-facing text.\n- Internal MiniMessage tags include `<ae_name>`, `<ae_id>`, `<ae_tier>`, `<ae_state>`, `<ae_hp>`, `<ae_max_hp>`, `<ae_hp_percent>`, `<ae_species>`, `<ae_level>`, `<ae_aspects>`, `<ae_dimension>`, `<ae_biome>`, `<ae_distance>`, and `<player_name>`.\n- Placeholder API exports `%alpha_encounter:active_count%`, `%alpha_encounter:cooldown_seconds <id>%`, and target-aware placeholders such as `%alpha_encounter:name nearest%`, `%alpha_encounter:hp_percent nearest%`, `%alpha_encounter:species nearest%`, and `%alpha_encounter:distance nearest%`.\n\n## Performance behavior''', readme, flags=re.S)
Path('README.md').write_text(readme,encoding='utf-8')

print('Alpha-Encounter integration refactor applied.')
