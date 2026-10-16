package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.core.KabutoRuntime;

@Mixin(ServerPlayNetworkHandler.class)
abstract class KunaiSwingMixin {
  @Shadow public ServerPlayerEntity player;
  @Inject(method="onHandSwing", at=@At("TAIL"))
  private void fantasyhub$swing(HandSwingC2SPacket packet, CallbackInfo ci) {
    KabutoRuntime.swing(player, packet.getHand());
  }
}
