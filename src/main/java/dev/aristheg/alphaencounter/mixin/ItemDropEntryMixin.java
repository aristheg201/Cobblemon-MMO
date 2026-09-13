package dev.aristheg.alphaencounter.mixin;

import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import dev.aristheg.alphaencounter.integration.AlphaLootGuard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Final Alpha IV-candy enforcement at the point where Cobblemon is about to
 * materialize an ItemDropEntry. The earlier LOOT_DROPPED filter remains useful
 * for keeping event state clean, while this prevents later subscribers from
 * re-adding a forbidden candy.
 */
@Mixin(ItemDropEntry.class)
public abstract class ItemDropEntryMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true, remap = false)
    private void alphaEncounter$blockIvCandyForManagedAlpha(
        LivingEntity entity,
        ServerWorld world,
        Vec3d pos,
        ServerPlayerEntity player,
        CallbackInfo ci
    ) {
        ItemDropEntry self = (ItemDropEntry) (Object) this;
        if (AlphaLootGuard.shouldBlockItemDrop(entity, self.getItem())) {
            ci.cancel();
        }
    }
}
