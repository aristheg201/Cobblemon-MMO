package vn.svframe.svframemmo.cobblemon.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;import com.cobblemon.mod.common.api.storage.adapter.flatfile.FileStoreAdapter;import java.util.concurrent.ExecutorService;
@Mixin(FileBackedPokemonStoreFactory.class)
public interface PokemonFileStoreAccess {@Accessor("adapter") FileStoreAdapter<Object> svframe$adapter();@Accessor("saveExecutor") ExecutorService svframe$writer();}
