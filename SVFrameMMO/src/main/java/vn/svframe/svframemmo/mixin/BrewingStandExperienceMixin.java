package vn.svframe.svframemmo.mixin;

import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.SVFrameMMO;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandExperienceMixin {
    @Unique private static final ThreadLocal<ItemStack> svframemmo$before = new ThreadLocal<>();

    @Inject(method = "craft", at = @At("HEAD"))
    private static void svframemmo$beforeBrew(World world, BlockPos pos, DefaultedList<ItemStack> slots, CallbackInfo ci) {
        ItemStack before = ItemStack.EMPTY;
        for (int i = 0; i < Math.min(3, slots.size()); i++) if (!slots.get(i).isEmpty()) { before = slots.get(i).copy(); break; }
        svframemmo$before.set(before);
    }

    @Inject(method = "craft", at = @At("TAIL"))
    private static void svframemmo$afterBrew(World world, BlockPos pos, DefaultedList<ItemStack> slots, CallbackInfo ci) {
        try {
            if (!(world instanceof ServerWorld serverWorld)) return;
            ItemStack after = ItemStack.EMPTY;
            for (int i = 0; i < Math.min(3, slots.size()); i++) if (!slots.get(i).isEmpty()) { after = slots.get(i).copy(); break; }
            if (after.isEmpty()) return;
            Vec3d center = Vec3d.ofCenter(pos);
            ServerPlayerEntity player = serverWorld.getPlayers().stream()
                    .filter(candidate -> candidate.squaredDistanceTo(center) < 100d)
                    .findFirst().orElse(null);
            if (player != null) SVFrameMMO.nativeExperience().onBrewed(player, svframemmo$before.get(), after);
        } finally {
            svframemmo$before.remove();
        }
    }
}
