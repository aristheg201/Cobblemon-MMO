package vn.svframe.fantasyhub.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import vn.svframe.fantasyhub.core.SyncSignals;
import vn.svframe.fantasyhub.core.sync.Domain;
import vn.svframe.svframemmo.api.player.PlayerData;

/** Observe native mutations; no replacement player data or persistence format. */
@Mixin(value = PlayerData.class, remap = false)
public abstract class PlayerDataSyncMixin {
  @Inject(
      method = {
        "setClassId",
        "setLevel",
        "reloadDefinitions",
        "setSkillLevel",
        "setSkillPoints",
        "setAttributePoints",
        "setAttributeReallocationPoints",
        "setClaimCount",
        "bindSkill",
        "bindClassSkill",
        "resetSkills",
        "resetAttributes"
      },
      at = @At("RETURN"),
      require = 0,
      remap = false)
  private void fantasyhub$classDirty(CallbackInfo ci) {
    SyncSignals.changed(
        ((PlayerData) (Object) this).getUniqueId(),
        Domain.CLASS,
        Domain.COMBAT,
        Domain.GUI,
        Domain.BUNDLES);
  }

  @Inject(
      method = {"setPreference", "setClassSwitchAvailableAt", "restorePreferences"},
      at = @At("RETURN"),
      require = 0,
      remap = false)
  private void fantasyhub$commerceDirty(CallbackInfo ci) {
    SyncSignals.changed(((PlayerData) (Object) this).getUniqueId(), Domain.COMMERCE, Domain.GUI);
  }

  @Inject(method = "refreshClassStats", at = @At("RETURN"), require = 0, remap = false)
  private void fantasyhub$statsDirty(CallbackInfo ci) {
    SyncSignals.changed(
        ((PlayerData) (Object) this).getUniqueId(),
        Domain.EQUIPMENT,
        Domain.COMBAT,
        Domain.VITALS,
        Domain.GUI);
  }

  @Inject(
      method = {"unlock", "spendAttributePoints", "reallocateAttributes"},
      at = @At("RETURN"),
      require = 0,
      remap = false)
  private void fantasyhub$unlockDirty(CallbackInfoReturnable<Boolean> ci) {
    if (Boolean.TRUE.equals(ci.getReturnValue()))
      SyncSignals.changed(
          ((PlayerData) (Object) this).getUniqueId(), Domain.CLASS, Domain.COMBAT, Domain.GUI);
  }
}
