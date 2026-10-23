package vn.svframe.svframemmo.cobblemon.mixin;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;import com.cobblemon.mod.common.pokemon.Pokemon;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=PokedexManager.class,remap=false)
public abstract class PokedexRescanMixin {
 @ModifyVariable(method="scheduleFullSyncFromStores",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
 private Iterable<? extends Pokemon> svframe$partyRescan(Iterable<? extends Pokemon> party){return vn.svframe.svframemmo.cobblemon.gameplay.DexWipeGuard.filter(((PokedexManager)(Object)this).getUuid(),party);}
 @ModifyVariable(method="scheduleFullSyncFromStores",at=@At("HEAD"),argsOnly=true,ordinal=1,remap=false)
 private Iterable<? extends Pokemon> svframe$pcRescan(Iterable<? extends Pokemon> pc){return vn.svframe.svframemmo.cobblemon.gameplay.DexWipeGuard.filter(((PokedexManager)(Object)this).getUuid(),pc);}
}
