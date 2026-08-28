package vn.svframe.svframemmo.mixin;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.SVFrameMMO;

/** Converts vanilla swap-hands/held-slot packets into SVFrameMMO skill-bar input on the server thread. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void svframemmo$swapHands(PlayerActionC2SPacket packet, CallbackInfo ci) {
        // HEAD injections run once on the Netty channel before vanilla's forceMainThread guard.
        // Let vanilla reschedule that first invocation; only execute RPG logic after it re-enters on the server thread.
        if (!player.getServer().isOnThread()) return;
        if (packet.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && SVFrameMMO.skillBar().handleSwapHands(player)) ci.cancel();
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void svframemmo$selectedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        // Skill casting can damage entities, spawn indicators and run effects; all of that must stay on the server thread.
        if (!player.getServer().isOnThread()) return;
        if (SVFrameMMO.skillBar().handleSelectedSlot(player, packet.getSelectedSlot())) ci.cancel();
    }
}
