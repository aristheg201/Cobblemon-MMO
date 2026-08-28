package vn.svframe.svframemmo.cobblemon.mixin;

import com.cobblemon.mod.common.net.messages.server.storage.pc.MovePartyPokemonToPCPacket;
import com.cobblemon.mod.common.net.serverhandling.storage.pc.MovePartyPokemonToPCHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

@Mixin(value = MovePartyPokemonToPCHandler.class, remap = false)
abstract class MovePartyPokemonToPCHandlerMixin {
    @Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/server/storage/pc/MovePartyPokemonToPCPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void svframe$lockFusionPokemon(MovePartyPokemonToPCPacket packet, MinecraftServer server, ServerPlayerEntity player, CallbackInfo ci) {
        if (SVFrameMMOCobblemon.fusions().isPokemonLocked(packet.getPokemonID())) ci.cancel();
    }
}
