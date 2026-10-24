package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.core.SyncSignals;
import vn.svframe.fantasyhub.core.sync.Domain;

@Mixin(LivingEntity.class)
public abstract class EquipmentSyncMixin {
  @Inject(method="setHealth",at=@At("HEAD"))
  private void fantasyhub$health(float health,CallbackInfo ci){
    if((Object)this instanceof ServerPlayerEntity p && Float.compare(p.getHealth(),health)!=0)
      SyncSignals.changed(p.getUuid(),Domain.VITALS);
  }

  @Inject(method = "onEquipStack", at = @At("TAIL"))
  private void fantasyhub$equipment(
      EquipmentSlot slot, ItemStack oldStack, ItemStack next, CallbackInfo ci) {
    if ((Object) this instanceof ServerPlayerEntity p)
      SyncSignals.changed(p.getUuid(), Domain.EQUIPMENT, Domain.COMBAT, Domain.VITALS, Domain.GUI);
  }
}
