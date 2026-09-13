package dev.aristheg.alphaencounter.integration;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/** Backwards-compatible facade for the data-driven Alpha drop policy. */
public final class AlphaLootGuard {
    private AlphaLootGuard() {}

    public static void initialize() {
        AlphaDropPolicy.initialize();
    }

    public static void reload() {
        AlphaDropPolicy.reload();
    }

    public static void track(String definitionId, UUID entityId, UUID pokemonId) {
        AlphaDropPolicy.track(definitionId, entityId, pokemonId);
    }

    public static boolean shouldBlockItemDrop(LivingEntity entity, Object itemId) {
        return AlphaDropPolicy.shouldFilter(entity, itemId);
    }

    public static boolean shouldBlockNativeAlphaLoot(ServerWorld world, double x, double y, double z, Object itemId) {
        return AlphaDropPolicy.shouldFilterNativeAlphaLoot(world, x, y, z, itemId);
    }
}
