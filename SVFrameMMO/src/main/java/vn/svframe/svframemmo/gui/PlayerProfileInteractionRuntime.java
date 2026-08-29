package vn.svframe.svframemmo.gui;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import vn.svframe.svframemmo.SVFrameMMO;

/** Native equivalent of MMOCore's optional sneak-right-click player profile check. */
public final class PlayerProfileInteractionRuntime {
    private static boolean installed;

    private PlayerProfileInteractionRuntime() { }

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || !SVFrameMMO.config().shiftClickPlayerProfileCheck()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity viewer) || !(entity instanceof ServerPlayerEntity target) || !viewer.isSneaking())
                return ActionResult.PASS;

            var targetData = SVFrameMMO.playerData().get(target);
            var viewerData = SVFrameMMO.playerData().get(viewer);
            SVFrameMMO.gui().playerStats().newInventory(targetData, viewerData).open();
            return ActionResult.PASS;
        });
    }
}
