package vn.svframe.svframelib;

import vn.svframe.svframelib.comp.adventure.AdventureParser;
import vn.svframe.svframelib.comp.flags.FlagHandler;
import vn.svframe.svframelib.comp.flags.FlagPlugin;
import vn.svframe.svframelib.comp.placeholder.PlaceholderParser;
import vn.svframe.svframelib.comp.profile.ProfileMode;
import vn.svframe.svframelib.glow.GlowModule;
import vn.svframe.svframelib.damage.mitigation.MitigationModule;
import vn.svframe.svframelib.damage.onhit.OnHitModule;
import vn.svframe.svframelib.gson.Gson;
import vn.svframe.svframelib.manager.*;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframelib.profile.handler.ProfileHandler;
import vn.svframe.svframelib.rpg.ClassModule;
import vn.svframe.svframelib.rpg.LevelModule;
import vn.svframe.svframelib.rpg.ManaModule;
import vn.svframe.svframelib.skill.handler.NativeBuiltinSkillBootstrap;
import vn.svframe.svframelib.version.ServerVersion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframelib.fabric.runtime.NativePlaceholderRegistry;

import java.util.List;
import java.util.logging.Logger;

public class SVFrameLib extends MMOPlugin {
    public static SVFrameLib plugin;
    private final DamageManager damageManager = new DamageManager(this);
    private final EntityManager entityManager = new EntityManager(this);
    private final StatManager statManager = new StatManager(this);
    private final ConfigManager configManager = new ConfigManager(this);
    private final ElementManager elementManager = new ElementManager(this);
    private final SkillManager skillManager = new SkillManager(this);
    private final JsonManager jsonManager = new JsonManager(this);
    private final SkillModifierManager skillModifierManager = new SkillModifierManager(this);
    private final MitigationModule mitigationModule = new MitigationModule(this);
    private final OnHitModule onHitModule = new OnHitModule(this);
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

    public SVFrameLib() { plugin = this; NativeBuiltinSkillBootstrap.register(skillManager); }
    public static synchronized SVFrameLib bootstrap() { return plugin == null ? new SVFrameLib() : plugin; }
    public static SVFrameLib inst() { return bootstrap(); }
    public void onLoad() { plugin = this; }
    public void onEnable() { initializeProfiles(); glowModule.enable(); mitigationModule.reload(); onHitModule.reload(); }
    public void reload() { skillManager.reload(); NativeBuiltinSkillBootstrap.materializeDefaultHandlers(skillManager); elementManager.reset(); mitigationModule.reload(); onHitModule.reload(); }
    public void onDisable() { glowModule.disable(); }
    public Logger getLogger() { return logger; }
    public DamageManager getDamage() { return damageManager; }
    public EntityManager getEntities() { return entityManager; }
    public StatManager getStats() { return statManager; }
    public ConfigManager getMMOConfig() { return configManager; }
    public ElementManager getElements() { return elementManager; }
    public SkillManager getSkills() { return skillManager; }
    public JsonManager getJson() { return jsonManager; }
    public Gson getGson() { return gson; }
    public SkillModifierManager getModifiers() { return skillModifierManager; }
    public MitigationModule getMitigation() { return mitigationModule; }
    public OnHitModule getOnHit() { return onHitModule; }
    public ServerVersion getVersion() { return version; }
    public FlagHandler getFlags() { return flagHandler; }
    public PlaceholderParser getPlaceholderParser() { return placeholderParser; }
    public AdventureParser getAdventureParser() { return adventureParser; }
    public GlowModule getGlowing() { return glowModule; }
    public MinecraftServer getServer() { return SVFrameLibFabricMod.server(); }
    public void handleFlags(FlagPlugin plugin) { flagHandler.registerPlugin(plugin); }
    public synchronized void useLegacyProfiles() { profileMode = ProfileMode.LEGACY; initializeProfiles(); }
    public synchronized void useNoProfiles() { profileMode = ProfileMode.NONE; initializeProfiles(); }
    public synchronized void useProxyProfiles() { profileMode = ProfileMode.PROXY; initializeProfiles(); }
    private void initializeProfiles() { try { profileHandler = profileMode.newProfileHandler(); } catch (Throwable ignored) { profileHandler = emptyProfileHandler(); } profileHandler.onStartup(); }
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
    public List<String> parseColors(String... text) { return text == null ? List.of() : java.util.Arrays.stream(text).map(this::parseColors).toList(); }
    public List<String> parseColors(List<String> text) { return text == null ? List.of() : text.stream().map(this::parseColors).toList(); }
    public List<MMOPlugin> getMMOPlugins() { return List.of(this); }
    private static ProfileHandler emptyProfileHandler() { return new ProfileHandler() { @Override public void onStartup() { } @Override public List<Identifier> collectModules() { return List.of(); } }; }
}
