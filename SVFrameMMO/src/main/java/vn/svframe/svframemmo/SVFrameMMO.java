package vn.svframe.svframemmo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.fabric.runtime.RpgProfileRegistry;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.rpg.ManaModule;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.config.SVFrameMMOConfig;
import vn.svframe.svframemmo.manager.AttributeManager;
import vn.svframe.svframemmo.manager.ClassManager;
import vn.svframe.svframemmo.manager.PlayerDataManager;
import vn.svframe.svframemmo.player.ResourceRegenRuntime;
import vn.svframe.svframemmo.skill.SVFrameMMOSkillBootstrap;

import java.util.logging.Logger;

public final class SVFrameMMO implements ModInitializer {
    public static final String ID = "svframemmo";
    private static final Logger LOG = Logger.getLogger("SVFrameMMO");
    private static final PlayerDataManager PLAYER_DATA = new PlayerDataManager();
    private static final ClassManager CLASSES = new ClassManager();
    private static final AttributeManager ATTRIBUTES = new AttributeManager();
    private static final ResourceRegenRuntime REGEN = new ResourceRegenRuntime();
    private static SVFrameMMOConfig config;
    private static long tick;
    private static AutoCloseable profileRegistration;

    @Override
    public void onInitialize() {
        try {
            DefaultFiles.ensure();
            config = SVFrameMMOConfig.load(DefaultFiles.ROOT.resolve("config.yml"));
            SVFrameLib.bootstrap();
            SVFrameMMOSkillBootstrap.register(DefaultFiles.ROOT.resolve("skills"));
            CLASSES.reload(DefaultFiles.ROOT.resolve("classes"), DefaultFiles.ROOT.resolve("stats.yml"),
                    DefaultFiles.ROOT.resolve("exp-curves"), config.passiveSkillNeedsBinding());
            ATTRIBUTES.reload(DefaultFiles.ROOT.resolve("attributes"));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize SVFrameMMO native data", exception);
        }

        profileRegistration = RpgProfileRegistry.register(id -> PLAYER_DATA.get(id).snapshot());
        SVFrameLib.inst().setClassModule(data -> PLAYER_DATA.get(data.getUniqueId()).getProfess().getId());
        SVFrameLib.inst().setLevelModule(data -> PLAYER_DATA.get(data.getUniqueId()).getLevel());
        SVFrameLib.inst().setManaModule(new ManaModule() {
            @Override public double getMana(vn.svframe.svframelib.api.player.MMOPlayerData data) { return PLAYER_DATA.get(data.getUniqueId()).getMana(); }
            @Override public double getStamina(vn.svframe.svframelib.api.player.MMOPlayerData data) { return PLAYER_DATA.get(data.getUniqueId()).getStamina(); }
            @Override public boolean setMana(vn.svframe.svframelib.api.player.MMOPlayerData data, double value, ResourceUpdateReason reason) {
                return PLAYER_DATA.get(data.getUniqueId()).setMana(value, reason);
            }
            @Override public boolean setStamina(vn.svframe.svframelib.api.player.MMOPlayerData data, double value, ResourceUpdateReason reason) {
                return PLAYER_DATA.get(data.getUniqueId()).setStamina(value, reason);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PLAYER_DATA.start(server);
            LOG.info("SVFrameMMO Fabric online; classes=" + CLASSES.size()
                    + ",attributes=" + ATTRIBUTES.size()
                    + ",skills=" + SVFrameLib.inst().getSkills().getHandlers().size()
                    + ",players=" + PLAYER_DATA.all().size());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PLAYER_DATA.save();
            try { if (profileRegistration != null) profileRegistration.close(); }
            catch (Exception ignored) { }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PLAYER_DATA.join(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAYER_DATA.quit(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            REGEN.tick(tick);
            if (config.autosaveSeconds() > 0 && tick % (config.autosaveSeconds() * 20L) == 0) PLAYER_DATA.save();
        });
        PlayerAttackEvent.EVENT.register(event -> {
            PLAYER_DATA.get(event.getPlayer()).markCombat();
            var target = event.getAttack().getTarget();
            if (target instanceof net.minecraft.server.network.ServerPlayerEntity player) PLAYER_DATA.get(player).markCombat();
        });
    }

    public static long currentTick() { return tick; }
    public static SVFrameMMOConfig config() {
        if (config == null) throw new IllegalStateException("SVFrameMMO not initialized");
        return config;
    }
    public static PlayerDataManager playerData() { return PLAYER_DATA; }
    public static ClassManager classes() { return CLASSES; }
    public static AttributeManager attributes() { return ATTRIBUTES; }
}
