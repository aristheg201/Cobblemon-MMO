package vn.svframe.svframecore.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframecore.SVFrameCore;
import vn.svframe.svframecore.api.event.PlayerResourceUpdateEvent;
import vn.svframe.svframecore.api.player.PlayerData;
import vn.svframe.svframecore.api.player.resource.PlayerResource;
import vn.svframe.svframecore.skill.list.Ambers;
import vn.svframe.svframecore.skill.list.Neptune_Gift;
import vn.svframe.svframecore.skill.list.Sneaky_Picky;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.Resources;
import vn.svframe.svframelib.skill.handler.SkillHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/** Native Fabric bootstrap for SVFrameCore. */
public final class SVFrameCoreFabricMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("SVFrameCore");
    private static final ConcurrentLinkedQueue<Scheduled> SCHEDULED = new ConcurrentLinkedQueue<>();
    private static volatile MinecraftServer server;
    private static volatile long tick;

    @Override
    public void onInitialize() {
        SVFrameCore core = SVFrameCore.bootstrap();
        installDefaultConfig(core.configRoot());
        core.loadConfig();
        core.installRpgProviders();

        Ambers ambers = new Ambers();
        Neptune_Gift neptune = new Neptune_Gift();
        Sneaky_Picky sneaky = new Sneaky_Picky();
        registerExternalBuiltin(ambers);
        registerExternalBuiltin(neptune);
        registerExternalBuiltin(sneaky);

        PlayerAttackEvent.EVENT.register(ambers::onPlayerAttack);
        PlayerAttackEvent.EVENT.register(sneaky::onPlayerAttack);
        PlayerResourceUpdateEvent.EVENT.register(neptune::onResourceUpdate);
        PlayerAttackEvent.EVENT.register(SVFrameCoreFabricMod::markCombat);

        Resources.setResourceEventCaller((player, oldAmount, newAmount, reason) ->
                new PlayerResourceUpdateEvent(PlayerData.getOrCreate(player), PlayerResource.HEALTH, oldAmount, newAmount, reason).call());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, value) -> PlayerData.getOrCreate(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, value) -> {
            MMOPlayerData data = MMOPlayerData.getOrNull(handler.player.getUuid());
            if (data != null) data.updatePlayer(null);
        });

        ServerTickEvents.END_SERVER_TICK.register(value -> {
            server = value;
            tick++;
            runScheduled();
            if (tick % SVFrameCore.config().playerResourceTickPeriod() == 0L) tickResourceRegeneration();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(value -> {
            server = value;
            LOG.info("SVFrameCore Fabric online; externalBuiltins=3,resources=4,rpgProviders=3");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(value -> {
            server = null;
            SCHEDULED.clear();
        });
    }

    public static long currentTick() { return tick; }
    public static MinecraftServer server() { return server; }

    public static void schedule(int delayTicks, Runnable runnable) {
        Objects.requireNonNull(runnable, "Scheduled task cannot be null");
        if (delayTicks <= 0) {
            MinecraftServer current = server;
            if (current != null) current.execute(runnable); else runnable.run();
            return;
        }
        SCHEDULED.add(new Scheduled(tick + delayTicks, runnable));
    }

    private static void runScheduled() {
        int size = SCHEDULED.size();
        for (int i = 0; i < size; i++) {
            Scheduled scheduled = SCHEDULED.poll();
            if (scheduled == null) break;
            if (scheduled.tick <= tick) scheduled.task.run(); else SCHEDULED.add(scheduled);
        }
    }

    private static void tickResourceRegeneration() {
        double multiplier = SVFrameCore.config().playerResourceTickPeriod() / 20d;
        for (PlayerData data : PlayerData.getAll()) {
            if (!data.isOnline() || data.getPlayer().isDead()) continue;
            for (PlayerResource resource : PlayerResource.values()) {
                double flat = data.getMMOPlayerData().getStatMap().getStat(resource.getRegenStat());
                double scaling = data.getMMOPlayerData().getStatMap().getStat(resource.getMaxRegenStat()) / 100d * resource.getMax(data);
                double regen = (flat + scaling) * multiplier;
                if (regen != 0d) resource.regen(data, regen);
            }
        }
    }

    private static void markCombat(PlayerAttackEvent event) {
        PlayerData.get(event.getData()).updateCombat();
        if (event.getAttack().getTarget() instanceof ServerPlayerEntity target) PlayerData.getOrCreate(target).updateCombat();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerExternalBuiltin(SkillHandler<?> handler) {
        var skills = SVFrameLib.inst().getSkills();
        skills.registerBuiltinSkillHandlerSource((Class) handler.getClass());
        if (skills.getHandler(handler.getId()) == null) skills.registerSkillHandler(handler);
    }

    private static void installDefaultConfig(Path root) {
        try {
            Files.createDirectories(root);
            Path target = root.resolve("config.yml");
            if (Files.exists(target)) return;
            try (var input = SVFrameCoreFabricMod.class.getClassLoader().getResourceAsStream("svframecore-default-config.yml")) {
                if (input == null) throw new IOException("Missing svframecore-default-config.yml");
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not install SVFrameCore default config", exception);
        }
    }

    private record Scheduled(long tick, Runnable task) { }
}
