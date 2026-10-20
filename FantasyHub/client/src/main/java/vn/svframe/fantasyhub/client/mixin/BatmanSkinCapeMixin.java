package vn.svframe.fantasyhub.client.mixin;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.protocol.*;
@Mixin(CapeFeatureRenderer.class)
public abstract class BatmanSkinCapeMixin {
 @Inject(method="render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;FFFFFF)V",at=@At("HEAD"),cancellable=true)
 private void fantasyhub$batman(MatrixStack m,VertexConsumerProvider v,int light,AbstractClientPlayerEntity p,float a,float b,float c,float d,float e,float f,CallbackInfo ci){
  if(!BackEquipment.equipped(p,Protocol.batmanCape).isEmpty())ci.cancel();
 }
}
