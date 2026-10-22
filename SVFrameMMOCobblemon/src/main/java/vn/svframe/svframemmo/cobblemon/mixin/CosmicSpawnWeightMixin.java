package vn.svframe.svframemmo.cobblemon.mixin;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;import vn.svframe.svframemmo.cobblemon.gameplay.*;
@Mixin(SpawnablePosition.class)
public abstract class CosmicSpawnWeightMixin {
 @Inject(method="getWeight",at=@At("RETURN"),cancellable=true)
 private void svframe$cosmicWeight(SpawnDetail detail,CallbackInfoReturnable<Float> cir){cir.setReturnValue(CosmicPokemonBridge.weight((SpawnablePosition)(Object)this,detail,cir.getReturnValue()));}
}
