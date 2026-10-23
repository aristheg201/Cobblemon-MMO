package vn.svframe.svframemmo.cobblemon.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;import com.cobblemon.mod.common.api.PrioritizedList;import com.cobblemon.mod.common.api.storage.PokemonStoreManager;import com.cobblemon.mod.common.api.storage.factory.PokemonStoreFactory;
@Mixin(PokemonStoreManager.class)
public interface PokemonStoreAccess {@Accessor("factories") PrioritizedList<PokemonStoreFactory> svframe$factories();}
