package vn.svframe.svframelib.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import vn.svframe.svframelib.fabric.runtime.NativeStatHandler;
import vn.svframe.svframelib.fabric.runtime.NativeTemporaryStatModifier;
import vn.svframe.svframelib.fabric.runtime.StatProviderRegistry;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Connects the native SVFrameLib stat runtime to Fabric player lifecycle and Minecraft attributes. */
public final class SVFrameLibStatMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("SVFrameLib-Fabric/Stats");
    private static final Path STATS_FILE = FabricLoader.getInstance().getConfigDir().resolve("SVFrameLib").resolve("stats.yml");
    private static final NativeStatEngine ENGINE = new NativeStatEngine();
    private static final Set<String> OWNED_HANDLERS = new LinkedHashSet<>();
    private static volatile AutoCloseable providerRegistration;
    private static volatile SVFrameLibStatSettings settings = SVFrameLibStatSettings.empty();

    @Override
    public void onInitialize() {
        if (!reload()) throw new IllegalStateException("Could not load SVFrameLib stats.yml");
        registerProvider();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long tick = SVFrameLibFabricMod.currentTick();
            NativeTemporaryStatModifier.tick(tick);
            ENGINE.tick(tick);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ENGINE.onSessionOpen(handler.player.getUuid()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ENGINE.onSessionClose(handler.player.getUuid());
            ENGINE.clear(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            NativeTemporaryStatModifier.cancelAll();
            ENGINE.clear();
        });
    }

    public static NativeStatEngine engine() {
        return ENGINE;
    }

    public static SVFrameLibStatSettings settings() {
        return settings;
    }

    public static synchronized boolean reload() {
        try {
            SVFrameLibStatSettings next = SVFrameLibStatSettings.load(STATS_FILE);
            for (String stat : OWNED_HANDLERS) ENGINE.removeHandler(stat);
            OWNED_HANDLERS.clear();

            registerDefaultHandlers(next);
            for (String stat : next.configuredStats()) {
                if (ENGINE.handler(stat) != null) continue;
                SVFrameLibStatSettings.Entry entry = next.entry(stat);
                registerOwned(new NativeStatHandler(
                        stat,
                        entry.baseValue(),
                        entry.minValue(),
                        entry.maxValue(),
                        entry.decimalFormat()));
            }
            settings = next;

            MinecraftServer server = SVFrameLibFabricMod.server();
            if (server != null) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    ENGINE.onSessionOpen(player.getUuid());
                }
            }
            return true;
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Failed to load SVFrameLib legacy stats.yml", exception);
            return false;
        }
    }

    private static void registerDefaultHandlers(SVFrameLibStatSettings config) {
        register("ARMOR", EntityAttributes.GENERIC_ARMOR, 0.0d, config);
        register("ARMOR_TOUGHNESS", EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 0.0d, config);
        register("ATTACK_DAMAGE", EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0d, config);
        register("ATTACK_SPEED", EntityAttributes.GENERIC_ATTACK_SPEED, 4.0d, config);
        register("KNOCKBACK_RESISTANCE", EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.0d, config);
        register("LUCK", EntityAttributes.GENERIC_LUCK, 0.0d, config);
        register("MAX_HEALTH", EntityAttributes.GENERIC_MAX_HEALTH, 20.0d, config);
        registerOwned(new FabricMovementSpeedStatHandler(ENGINE, config.entry("MOVEMENT_SPEED")));
        register("MAX_ABSORPTION", EntityAttributes.GENERIC_MAX_ABSORPTION, 0.0d, config);

        register("BLOCK_BREAK_SPEED", EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, 1.0d, config);
        register("BLOCK_INTERACTION_RANGE", EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, 4.5d, config);
        register("ENTITY_INTERACTION_RANGE", EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, 3.0d, config);
        register("FALL_DAMAGE_MULTIPLIER", EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, 1.0d, config);
        register("GRAVITY", EntityAttributes.GENERIC_GRAVITY, 0.08d, config);
        register("JUMP_STRENGTH", EntityAttributes.GENERIC_JUMP_STRENGTH, 0.42d, config);
        register("SAFE_FALL_DISTANCE", EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 3.0d, config);
        register("SCALE", EntityAttributes.GENERIC_SCALE, 1.0d, config);
        register("STEP_HEIGHT", EntityAttributes.GENERIC_STEP_HEIGHT, 0.6d, config);

        register("BURNING_TIME", EntityAttributes.GENERIC_BURNING_TIME, 1.0d, config);
        register("EXPLOSION_KNOCKBACK_RESISTANCE", EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE, 0.0d, config);
        register("MINING_EFFICIENCY", EntityAttributes.PLAYER_MINING_EFFICIENCY, 0.0d, config);
        register("MOVEMENT_EFFICIENCY", EntityAttributes.GENERIC_MOVEMENT_EFFICIENCY, 0.0d, config);
        register("OXYGEN_BONUS", EntityAttributes.GENERIC_OXYGEN_BONUS, 0.0d, config);
        register("SNEAKING_SPEED", EntityAttributes.PLAYER_SNEAKING_SPEED, 0.3d, config);
        register("SUBMERGED_MINING_SPEED", EntityAttributes.PLAYER_SUBMERGED_MINING_SPEED, 0.2d, config);
        register("SWEEPING_DAMAGE_RATIO", EntityAttributes.PLAYER_SWEEPING_DAMAGE_RATIO, 0.0d, config);
        register("WATER_MOVEMENT_EFFICIENCY", EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY, 0.0d, config);
    }

    private static void register(String stat,
                                 RegistryEntry<EntityAttribute> attribute,
                                 double playerDefaultBase,
                                 SVFrameLibStatSettings config) {
        registerOwned(new FabricAttributeStatHandler(stat, attribute, playerDefaultBase, config.entry(stat)));
    }

    private static void registerOwned(NativeStatHandler handler) {
        ENGINE.registerHandler(handler);
        OWNED_HANDLERS.add(handler.stat());
    }

    private static void registerProvider() {
        if (providerRegistration != null) return;
        synchronized (SVFrameLibStatMod.class) {
            if (providerRegistration == null) providerRegistration = StatProviderRegistry.register(ENGINE::stat);
        }
    }
}
