package vn.svframe.fantasyhub.client;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import vn.svframe.fantasyhub.protocol.Protocol;
final class DragonbornHatFeature extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    DragonbornHatFeature(FeatureRendererContext<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> context){super(context);}
    @Override public void render(MatrixStack matrices,VertexConsumerProvider vertices,int light,AbstractClientPlayerEntity entity,float limbAngle,float limbDistance,float tickDelta,float age,float headYaw,float headPitch){
        var stack=entity.getEquippedStack(EquipmentSlot.HEAD);if((!stack.isOf(Protocol.dragonbornHat)&&!HunterArmorFeature.hat(stack))||entity.isInvisible())return;
        matrices.push();getContextModel().head.rotate(matrices);HeadFeatureRenderer.translate(matrices,false);
        MinecraftClient.getInstance().getItemRenderer().renderItem(stack,ModelTransformationMode.HEAD,light,OverlayTexture.DEFAULT_UV,matrices,vertices,entity.getWorld(),entity.getId());matrices.pop();
    }
}
