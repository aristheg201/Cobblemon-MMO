package vn.svframe.svframelib.profile;
import vn.svframe.svframelib.data.SynchronizedDataManager; import vn.svframe.svframelib.module.MMOPlugin; import net.minecraft.util.Identifier; import java.util.*;
public class DefaultProfileDataModule {
    private final MMOPlugin plugin; private final Identifier namespacedKey; private final SynchronizedDataManager<?,?> manager;
    public DefaultProfileDataModule(SynchronizedDataManager<?,?> manager){this.manager=Objects.requireNonNull(manager);this.plugin=manager.getOwningPlugin();this.namespacedKey=Identifier.of(plugin.getNamespacedKey(),"profile-data");}
    public MMOPlugin getOwningPlugin(){return plugin;} public Identifier getId(){return namespacedKey;}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void load(SynchronizedDataManager manager,Object data){manager.loadData((vn.svframe.svframelib.data.SynchronizedDataHolder)data);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void save(SynchronizedDataManager manager,Object data,SessionUpdateReason reason){manager.saveData((vn.svframe.svframelib.data.SynchronizedDataHolder)data,reason);}
    public void onProfileSelect(UUID player){if(manager.isLoaded(player)){var d=manager.get(player);load(manager,d);}}
    public void onProfileUnload(UUID player){var d=manager.getOrNull(player);if(d!=null)save(manager,d,SessionUpdateReason.QUIT_PROFILE);}
    public void onProfileCreate(UUID player){if(!manager.isLoaded(player))manager.setup(player);}
    public void onProfileDelete(UUID player){var d=manager.getOrNull(player);if(d!=null)save(manager,d,SessionUpdateReason.QUIT_PROFILE);}
}
