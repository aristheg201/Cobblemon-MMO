package vn.svframe.svframemmo.mixin;

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
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
import vn.svframe.svframemmo.skill.cast.PlayerKey;

/** Converts vanilla C2S input into the native SVFrameMMO casting key surface. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void svframemmo$playerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        // HEAD injections run once on the Netty channel before vanilla's forceMainThread guard.
        if (!player.getServer().isOnThread()) return;
        PlayerKey key = switch (packet.getAction()) {
            case SWAP_ITEM_WITH_OFFHAND -> PlayerKey.SWAP_HANDS;
            case DROP_ITEM, DROP_ALL_ITEMS -> PlayerKey.DROP;
            default -> null;
        };
        if (key != null && SVFrameMMO.skillBar().handleKey(player, key)) ci.cancel();
    }

    @Inject(method = "onClientCommand", at = @At("HEAD"), cancellable = true)
    private void svframemmo$clientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
        if (!player.getServer().isOnThread()) return;
        if (packet.getMode() == ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY
                && SVFrameMMO.skillBar().handleKey(player, PlayerKey.CROUCH)) ci.cancel();
    }

    /** Hand swing supplies LEFT_CLICK_AIR, while Fabric attack callbacks cover blocks/entities. Same-tick dedupe prevents doubles. */
    @Inject(method = "onHandSwing", at = @At("HEAD"), cancellable = true)
    private void svframemmo$handSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        if (!player.getServer().isOnThread()) return;
        if (SVFrameMMO.skillBar().handleKey(player, PlayerKey.LEFT_CLICK)) ci.cancel();
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void svframemmo$selectedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        if (!player.getServer().isOnThread()) return;
        if (SVFrameMMO.skillBar().handleSelectedSlot(player, packet.getSelectedSlot())) ci.cancel();
    }
}
