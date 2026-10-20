package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.BetterCombatCompat;

/** Observe Better Combat's actual local and networked attack animations. */
@Mixin(value=AbstractClientPlayerEntity.class,priority=900)
abstract class BetterCombatAnimationMixin {
  @Inject(method="playAttackAnimation",at=@At("HEAD"),remap=false)
  private void fantasyhub$attack(String name,@Coerce Object hand,float length,float upswing,CallbackInfo ci){BetterCombatCompat.attackStarted((LivingEntity)(Object)this,length);}
  @Inject(method="stopAttackAnimation",at=@At("HEAD"),remap=false)
  private void fantasyhub$stop(float length,CallbackInfo ci){BetterCombatCompat.attackStopped((LivingEntity)(Object)this,length);}
}
