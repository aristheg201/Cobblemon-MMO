package vn.svframe.svframemmo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.unlocking.ItemLockedEvent;
import vn.svframe.svframemmo.api.event.unlocking.ItemUnlockedEvent;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Locale;

/** Emits public unlock-state events after the native PlayerData mutation succeeds. */
@Mixin(value = PlayerData.class, remap = false)
public abstract class PlayerDataUnlockEventMixin {
    @Inject(method = "unlock", at = @At("RETURN"), remap = false)
    private void svframemmo$onUnlock(String key, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        PlayerData data = (PlayerData) (Object) this;
        String normalized = normalize(key);
        SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + 1L,
                () -> new ItemUnlockedEvent(data, normalized).call());
    }

    @Inject(method = "lock", at = @At("RETURN"), remap = false)
    private void svframemmo$onLock(String key, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        PlayerData data = (PlayerData) (Object) this;
        String normalized = normalize(key);
        SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + 1L,
                () -> new ItemLockedEvent(data, normalized).call());
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
    }
}
