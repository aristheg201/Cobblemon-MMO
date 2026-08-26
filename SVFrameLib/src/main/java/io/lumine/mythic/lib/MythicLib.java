package io.lumine.mythic.lib;

import io.lumine.mythic.lib.comp.adventure.AdventureParser;
import io.lumine.mythic.lib.comp.flags.FlagHandler;
import io.lumine.mythic.lib.comp.flags.FlagPlugin;
import io.lumine.mythic.lib.comp.placeholder.PlaceholderParser;
import io.lumine.mythic.lib.comp.profile.ProfileMode;
import io.lumine.mythic.lib.glow.GlowModule;
import io.lumine.mythic.lib.gson.Gson;
import io.lumine.mythic.lib.manager.*;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.profile.handler.ProfileHandler;
import io.lumine.mythic.lib.rpg.ClassModule;
import io.lumine.mythic.lib.rpg.LevelModule;
import io.lumine.mythic.lib.rpg.ManaModule;
import io.lumine.mythic.lib.version.ServerVersion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;
import vn.svframe.mythiclibfabric.runtime.NativePlaceholderRegistry;

import java.util.List;
import java.util.logging.Logger;

/** Root native Fabric implementation of the MythicLib 1.7.1 service surface. */
public class MythicLib extends MMOPlugin {
    public static MythicLib plugin;

    private final DamageManager damageManager = new DamageManager(this);
    private final EntityManager entityManager = new EntityManager(this);
    private final StatManager statManager = new StatManager(this);
    private final ConfigManager configManager = new ConfigManager(this);
    private final ElementManager elementManager = new ElementManager(this);
    private final SkillManager skillManager = new SkillManager(this);
    private final FakeEventManager fakeEventManager = new FakeEventManager(this);
    private final JsonManager jsonManager = new JsonManager(this);
    private final SkillModifierManager skillModifierManager = new SkillModifierManager(this);
    private final Gson gson = new Gson();
    private final ServerVersion version = ServerVersion.get();
    private final FlagHandler flagHandler = new FlagHandler();
    private final AdventureParser adventureParser = new AdventureParser();
    private final GlowModule glowModule = new GlowModule.Native();
    private final PlaceholderParser placeholderParser = NativePlaceholderRegistry::parse;
    private final Logger logger = Logger.getLogger("SVFrameLib");

    private volatile ProfileMode profileMode = ProfileMode.NONE;
    private volatile ProfileHandler profileHandler = emptyProfileHandler();
    private volatile ClassModule classModule;
    private volatile LevelModule levelModule;
    private volatile ManaModule manaModule;

    public MythicLib() { plugin = this; }
    public static synchronized MythicLib bootstrap() { return plugin == null ? new MythicLib() : plugin; }
    public static MythicLib inst() { return bootstrap(); }

    public void onLoad() { plugin = this; }
    public void onEnable() { initializeProfiles(); glowModule.enable(); }
    public void reload() { skillManager.reload(); elementManager.reset(); }
    public void onDisable() { glowModule.disable(); }

    public Logger getLogger() { return logger; }
    public DamageManager getDamage() { return damageManager; }
    public EntityManager getEntities() { return entityManager; }
    public StatManager getStats() { return statManager; }
    public ConfigManager getMMOConfig() { return configManager; }
    public ElementManager getElements() { return elementManager; }
    public SkillManager getSkills() { return skillManager; }
    public FakeEventManager getFakeEvents() { return fakeEventManager; }
    public JsonManager getJson() { return jsonManager; }
    public Gson getGson() { return gson; }
    public SkillModifierManager getModifiers() { return skillModifierManager; }
    public ServerVersion getVersion() { return version; }
    public FlagHandler getFlags() { return flagHandler; }
    public PlaceholderParser getPlaceholderParser() { return placeholderParser; }
    public AdventureParser getAdventureParser() { return adventureParser; }
    public GlowModule getGlowing() { return glowModule; }
    public MinecraftServer getServer() { return MythicLibFabricMod.server(); }
    public void handleFlags(FlagPlugin plugin) { flagHandler.registerPlugin(plugin); }

    public synchronized void useLegacyProfiles() { profileMode = ProfileMode.LEGACY; initializeProfiles(); }
    public synchronized void useNoProfiles() { profileMode = ProfileMode.NONE; initializeProfiles(); }
    public synchronized void useProxyProfiles() { profileMode = ProfileMode.PROXY; initializeProfiles(); }
    private void initializeProfiles() {
        try { profileHandler = profileMode.newProfileHandler(); }
        catch (Throwable ignored) { profileHandler = emptyProfileHandler(); }
        profileHandler.onStartup();
    }

    public boolean hasProfiles() { return profileMode != ProfileMode.NONE; }
    public ProfileMode getProfileMode() { return profileMode; }
    public ProfileHandler getProfileHandler() { return profileHandler; }
    public ClassModule getClassModule() { return classModule; }
    public LevelModule getLevelModule() { return levelModule; }
    public ManaModule getManaModule() { return manaModule; }
    public void setClassModule(ClassModule module) { classModule = module; }
    public void setLevelModule(LevelModule module) { levelModule = module; }
    public void setManaModule(ManaModule module) { manaModule = module; }

    @Override public boolean hasData() { return true; }
    @Override public String getNamespacedKey() { return "svframelib"; }
    @Override public void debug(String message) { logger.info(message); }
    @Override public void debug(String context, String message) { logger.info("[" + context + "] " + message); }

    public String parseColors(String text) { return adventureParser.parse(text); }
    public List<String> parseColors(String... text) {
        return text == null ? List.of() : java.util.Arrays.stream(text).map(this::parseColors).toList();
    }
    public List<String> parseColors(List<String> text) {
        return text == null ? List.of() : text.stream().map(this::parseColors).toList();
    }
    public List<MMOPlugin> getMMOPlugins() { return List.of(this); }

    private static ProfileHandler emptyProfileHandler() {
        return new ProfileHandler() {
            @Override public void onStartup() { }
            @Override public List<Identifier> collectModules() { return List.of(); }
        };
    }
}
