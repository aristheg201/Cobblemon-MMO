package vn.svframe.svframemmo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.fabric.runtime.RpgProfileRegistry;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.rpg.ManaModule;
import vn.svframe.svframemmo.api.integration.SkillSourceBootstrap;
import vn.svframe.svframemmo.command.SVFrameMMOCommands;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.config.SVFrameMMOConfig;
import vn.svframe.svframemmo.manager.AttributeManager;
import vn.svframe.svframemmo.manager.BoosterManager;
import vn.svframe.svframemmo.manager.ClassManager;
import vn.svframe.svframemmo.manager.ExperienceTableManager;
import vn.svframe.svframemmo.manager.PermissionRegistry;
import vn.svframe.svframemmo.manager.PlayerDataManager;
import vn.svframe.svframemmo.manager.ProfessionManager;
import vn.svframe.svframemmo.manager.SkillTreeManager;
import vn.svframe.svframemmo.player.DelayedActionRuntime;
import vn.svframe.svframemmo.player.ResourceRegenRuntime;
import vn.svframe.svframemmo.skill.SVFrameMMOSkillBootstrap;
import vn.svframe.svframemmo.skill.runtime.SkillBarRuntime;
import vn.svframe.svframemmo.skill.runtime.SkillRuntime;
import vn.svframe.svframemmo.skill.runtime.TemporarySkillOverlayRuntime;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native Fabric RPG progression core. Integration/gameplay mods consume its public API. */
public final class SVFrameMMO implements ModInitializer {
    public static final String ID = "svframemmo";
    private static final Logger LOG = Logger.getLogger("SVFrameMMO");
    private static final PlayerDataManager PLAYER_DATA = new PlayerDataManager();
    private static final ResourceRegenRuntime REGEN = new ResourceRegenRuntime();
    private static final DelayedActionRuntime DELAYED_ACTIONS = new DelayedActionRuntime();
    private static final BoosterManager BOOSTERS = new BoosterManager();
    private static final PermissionRegistry PERMISSIONS = new PermissionRegistry();
    private static final SkillRuntime SKILL_RUNTIME = new SkillRuntime();
    private static final SkillBarRuntime SKILL_BAR = new SkillBarRuntime();
    private static final TemporarySkillOverlayRuntime TEMPORARY_SKILLS = new TemporarySkillOverlayRuntime();

    private static volatile ClassManager classes = new ClassManager();
    private static volatile AttributeManager attributes = new AttributeManager();
    private static volatile ProfessionManager professions = new ProfessionManager();
    private static volatile ExperienceTableManager experienceTables = new ExperienceTableManager();
    private static volatile SkillTreeManager skillTrees = new SkillTreeManager();
    private static volatile SVFrameMMOConfig config;
    private static long tick;
    private static AutoCloseable profileRegistration;

    @Override
    public void onInitialize() {
        try {
            DefaultFiles.ensure();
            SVFrameLib.bootstrap();
            for (SkillSourceBootstrap bootstrap : FabricLoader.getInstance().getEntrypoints("svframemmo-skill-source", SkillSourceBootstrap.class)) {
                bootstrap.registerSkillSources();
            }
            SVFrameMMOSkillBootstrap.register(DefaultFiles.ROOT.resolve("skills"));
            loadDefinitions();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize SVFrameMMO native progression data", exception);
        }

        profileRegistration = RpgProfileRegistry.register(id -> PLAYER_DATA.get(id).snapshot());
        SVFrameLib.inst().setClassModule(data -> PLAYER_DATA.get(data.getUniqueId()).getProfess().getId());
        SVFrameLib.inst().setLevelModule(data -> PLAYER_DATA.get(data.getUniqueId()).getLevel());
        SVFrameLib.inst().setManaModule(new ManaModule() {
            @Override public double getMana(vn.svframe.svframelib.api.player.MMOPlayerData data) { return PLAYER_DATA.get(data.getUniqueId()).getMana(); }
            @Override public double getStamina(vn.svframe.svframelib.api.player.MMOPlayerData data) { return PLAYER_DATA.get(data.getUniqueId()).getStamina(); }
            @Override public boolean setMana(vn.svframe.svframelib.api.player.MMOPlayerData data, double value, ResourceUpdateReason reason) { return PLAYER_DATA.get(data.getUniqueId()).setMana(value, reason); }
            @Override public boolean setStamina(vn.svframe.svframelib.api.player.MMOPlayerData data, double value, ResourceUpdateReason reason) { return PLAYER_DATA.get(data.getUniqueId()).setStamina(value, reason); }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SVFrameMMOCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PLAYER_DATA.start(server);
            LOG.info("SVFrameMMO Fabric online; " + definitionSummary() + ",players=" + PLAYER_DATA.all().size());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SKILL_BAR.clear();
            TEMPORARY_SKILLS.clear();
            PLAYER_DATA.save();
            for (var data : PLAYER_DATA.all()) SKILL_RUNTIME.detach(data);
            try { if (profileRegistration != null) profileRegistration.close(); }
            catch (Exception ignored) { }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PLAYER_DATA.join(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SKILL_BAR.detach(handler.player.getUuid());
            TEMPORARY_SKILLS.clear(handler.player.getUuid());
            PLAYER_DATA.quit(handler.player);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            REGEN.tick(tick);
            DELAYED_ACTIONS.tick(tick);
            SKILL_BAR.tick(tick);
            SVFrameMMOConfig live = config;
            if (live.autosaveSeconds() > 0 && tick % (live.autosaveSeconds() * 20L) == 0) PLAYER_DATA.save();
        });
        PlayerAttackEvent.EVENT.register(event -> {
            PLAYER_DATA.get(event.getPlayer()).markCombat();
            var target = event.getAttack().getTarget();
            if (target instanceof net.minecraft.server.network.ServerPlayerEntity player) PLAYER_DATA.get(player).markCombat();
        });
    }

    public static synchronized boolean reload() {
        try {
            for (var data : PLAYER_DATA.all()) data.prepareReload();
            loadDefinitions();
            for (var data : PLAYER_DATA.all()) data.reloadDefinitions();
            SKILL_BAR.clear();
            TEMPORARY_SKILLS.clear();
            PLAYER_DATA.save();
            LOG.info("SVFrameMMO reloaded; " + definitionSummary());
            return true;
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "SVFrameMMO reload failed", exception);
            for (var data : PLAYER_DATA.all()) {
                try { data.reloadDefinitions(); } catch (RuntimeException recovery) { LOG.log(Level.SEVERE, "Could not recover player runtime after failed reload", recovery); }
            }
            return false;
        }
    }

    private static void loadDefinitions() throws IOException {
        SVFrameMMOConfig nextConfig = SVFrameMMOConfig.load(DefaultFiles.ROOT.resolve("config.yml"));
        ClassManager nextClasses = new ClassManager();
        AttributeManager nextAttributes = new AttributeManager();
        ProfessionManager nextProfessions = new ProfessionManager();
        ExperienceTableManager nextExperienceTables = new ExperienceTableManager();
        SkillTreeManager nextSkillTrees = new SkillTreeManager();

        nextClasses.reload(DefaultFiles.ROOT.resolve("classes"), DefaultFiles.ROOT.resolve("stats.yml"),
                DefaultFiles.ROOT.resolve("exp-curves"), nextConfig.passiveSkillNeedsBinding());
        nextAttributes.reload(DefaultFiles.ROOT.resolve("attributes"));
        nextProfessions.reload(DefaultFiles.ROOT.resolve("professions"), nextClasses.getCurves());
        nextExperienceTables.reload(DefaultFiles.ROOT.resolve("exp-tables"));
        nextSkillTrees.reload(DefaultFiles.ROOT.resolve("skill-trees"));

        for (var playerClass : nextClasses.getAll()) {
            if (playerClass.hasExperienceTable()) nextExperienceTables.getOrThrow(playerClass.getExperienceTableId());
            for (String treeId : playerClass.getSkillTreeIds()) nextSkillTrees.getOrThrow(treeId);
        }
        for (var profession : nextProfessions.getAll()) if (profession.hasExperienceTable()) nextExperienceTables.getOrThrow(profession.getExperienceTableId());
        for (var attribute : nextAttributes.getAll()) if (attribute.hasExperienceTable()) nextExperienceTables.getOrThrow(attribute.getExperienceTableId());
        config = nextConfig;
        classes = nextClasses;
        attributes = nextAttributes;
        professions = nextProfessions;
        experienceTables = nextExperienceTables;
        skillTrees = nextSkillTrees;
    }

    public static String definitionSummary() {
        return "classes=" + classes.size() + ",attributes=" + attributes.size() + ",professions=" + professions.size()
                + ",expTables=" + experienceTables.size() + ",skillTrees=" + skillTrees.size()
                + ",skills=" + SVFrameLib.inst().getSkills().getHandlers().size();
    }

    public static long currentTick() { return tick; }
    public static SVFrameMMOConfig config() {
        SVFrameMMOConfig value = config;
        if (value == null) throw new IllegalStateException("SVFrameMMO not initialized");
        return value;
    }
    public static PlayerDataManager playerData() { return PLAYER_DATA; }
    public static ClassManager classes() { return classes; }
    public static AttributeManager attributes() { return attributes; }
    public static ProfessionManager professions() { return professions; }
    public static ExperienceTableManager experienceTables() { return experienceTables; }
    public static BoosterManager boosters() { return BOOSTERS; }
    public static PermissionRegistry permissions() { return PERMISSIONS; }
    public static SkillTreeManager skillTrees() { return skillTrees; }
    public static SkillRuntime skillRuntime() { return SKILL_RUNTIME; }
    public static SkillBarRuntime skillBar() { return SKILL_BAR; }
    public static TemporarySkillOverlayRuntime temporarySkills() { return TEMPORARY_SKILLS; }
    public static DelayedActionRuntime delayedActions() { return DELAYED_ACTIONS; }
}
