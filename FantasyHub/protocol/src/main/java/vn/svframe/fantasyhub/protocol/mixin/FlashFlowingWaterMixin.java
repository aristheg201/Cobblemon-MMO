package vn.svframe.fantasyhub.protocol.mixin;
import net.minecraft.block.*;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.shape.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FluidBlock.class)
abstract class FlashFlowingWaterMixin {
 @Inject(method="getCollisionShape",at=@At("HEAD"),cancellable=true)
 private void fantasyhub$flowingSurface(BlockState state,BlockView world,BlockPos pos,ShapeContext context,CallbackInfoReturnable<VoxelShape> cir){
  var fluid=state.getFluidState();if(!fluid.isIn(FluidTags.WATER)||!context.canWalkOnFluid(world.getFluidState(pos.up()),fluid))return;
  var surface=VoxelShapes.cuboid(0,0,0,1,fluid.getHeight(world,pos),1);if(context.isAbove(surface,pos,true))cir.setReturnValue(surface);
 }
}
