package vn.svframe.svframemmo.cobblemon.mixin;

import com.cobblemon.mod.common.net.messages.server.pasture.PasturePokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.PasturePokemonHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

@Mixin(value = PasturePokemonHandler.class, remap = false)
abstract class PasturePokemonHandlerMixin {
    @Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/server/pasture/PasturePokemonPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void svframe$lockFusionPokemon(PasturePokemonPacket packet, MinecraftServer server, ServerPlayerEntity player, CallbackInfo ci) {
        if (SVFrameMMOCobblemon.fusions().isPokemonLocked(packet.getPokemonId())) ci.cancel();
    }
}
