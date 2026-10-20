package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.fantasyhub.client.BetterCombatCompat;

@Pseudo
@Mixin(targets="net.bettercombat.logic.PlayerAttackHelper",remap=false)
abstract class BetterCombatPoseMixin {
  @Inject(method="poseForPlayer",at=@At("HEAD"),cancellable=true,remap=false)
  private static void fantasyhub$skillPose(PlayerEntity player,CallbackInfoReturnable<Object> ci){
    if(BetterCombatCompat.skillOwnsPose(player)){var pose=BetterCombatCompat.emptyPose();if(pose!=null)ci.setReturnValue(pose);}
  }
}
