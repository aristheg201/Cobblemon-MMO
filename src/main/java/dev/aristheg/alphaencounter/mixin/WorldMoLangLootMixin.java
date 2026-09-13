package dev.aristheg.alphaencounter.mixin;

import com.bedrockk.molang.runtime.MoParams;
import com.cobblemon.mod.common.api.molang.function.WorldMoLangFunctions;
import dev.aristheg.alphaencounter.integration.AlphaLootGuard;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Cobblemon 1.8 gives Alpha battle rewards from
 * callbacks/battle_fainted/pokemon_alpha_drops.molang. That callback invokes
 * WorldMoLangFunctions.spawn_loot_table_items, which creates ItemEntity values
 * directly and therefore bypasses both Pokemon DropTable and Entity#dropItem.
 *
 * This redirect sits at the final ServerWorld spawn boundary and only filters
 * items from Cobblemon Alpha loot tables when the callback position resolves
 * to an Alpha-Encounter managed Pokemon. Other Cobblemon Alphas and all other
 * loot-table spawns are untouched.
 */
@Mixin(value = WorldMoLangFunctions.class, remap = false)
public abstract class WorldMoLangLootMixin {
    @Redirect(
        method = "moLangFunctions$lambda$20",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_3218;method_8649(Lnet/minecraft/class_1297;)Z",
            remap = false
        ),
        remap = false
    )
    private static boolean alphaEncounter$filterNativeAlphaReward(
        ServerWorld serverWorld,
        Entity entity,
        World ignoredWorld,
        MoParams params
    ) {
        if (!(entity instanceof ItemEntity itemEntity) || params == null) {
            return serverWorld.spawnEntity(entity);
        }

        String lootTable = params.getString(0);
        if (lootTable == null || !lootTable.startsWith("cobblemon:alpha/")) {
            return serverWorld.spawnEntity(entity);
        }

        Identifier itemId = Registries.ITEM.getId(itemEntity.getStack().getItem());
        if (itemId == null) return serverWorld.spawnEntity(entity);

        double x = params.getDouble(1);
        double y = params.getDouble(2);
        double z = params.getDouble(3);
        if (AlphaLootGuard.shouldBlockNativeAlphaLoot(serverWorld, x, y, z, itemId)) {
            return false;
        }

        return serverWorld.spawnEntity(entity);
    }
}
