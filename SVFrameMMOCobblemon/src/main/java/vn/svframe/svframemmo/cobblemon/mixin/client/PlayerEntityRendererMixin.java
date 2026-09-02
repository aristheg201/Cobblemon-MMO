package vn.svframe.svframemmo.cobblemon.mixin.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.cobblemon.fusion.render.ClientFusionMorphState;

/** Directly replaces the player's vanilla renderer; no invisible player and no self-view puppet entity are used. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void svframe$renderFusionMorph(AbstractClientPlayerEntity player, float entityYaw, float tickDelta,
                                           MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                           CallbackInfo ci) {
        PokemonEntity morph = ClientFusionMorphState.renderable(player);
        if (morph == null) return;
        matrices.push();
        try {
            MinecraftClient.getInstance().getEntityRenderDispatcher().render(
                    morph, 0.0, 0.0, 0.0, entityYaw, tickDelta, matrices, vertexConsumers, light);
            ci.cancel();
        } finally {
            matrices.pop();
        }
    }
}
