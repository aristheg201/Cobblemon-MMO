package vn.svframe.fantasyhub.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.core.SyncSignals;
import vn.svframe.fantasyhub.core.sync.Domain;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.skill.runtime.SkillRuntime;

/** Covers external-skill commands and providers without polling every learned definition. */
@Mixin(value=SkillRuntime.class,remap=false)
public abstract class SkillRuntimeSyncMixin {
  @Inject(method="refresh",at=@At("RETURN"),require=0,remap=false)
  private void fantasyhub$bindingsChanged(PlayerData data,CallbackInfo ci) {
    SyncSignals.changed(data.getUniqueId(),Domain.CLASS,Domain.COMBAT,Domain.GUI,Domain.BUNDLES);
  }
}
