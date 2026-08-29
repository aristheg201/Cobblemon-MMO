package vn.svframe.svframemmo.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.FurnaceOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.experience.source.ExperienceHologramRuntime;

@Mixin(FurnaceOutputSlot.class)
public abstract class FurnaceOutputSlotExperienceMixin {
    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void svframemmo$smelted(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        ExperienceHologramRuntime.HologramLocation location = null;
        FurnaceOutputSlot self = (FurnaceOutputSlot) (Object) this;
        if (self.inventory instanceof AbstractFurnaceBlockEntity furnace && furnace.getWorld() instanceof ServerWorld world)
            location = ExperienceHologramRuntime.HologramLocation.block(world, furnace.getPos());
        SVFrameMMO.nativeExperience().onSmelted(serverPlayer, stack.copy(), location);
    }
}
