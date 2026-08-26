package io.lumine.mythic.lib.data;
import io.lumine.mythic.lib.profile.SessionUpdateReason; import net.minecraft.server.command.ServerCommandSource;
import java.util.*; import java.util.function.*;
public class DataExport<H extends SynchronizedDataHolder,O extends OfflineDataHolder> {
    private final SynchronizedDataManager<H,O> manager; private final Consumer<String> output; private Runnable callback=()->{};
    public DataExport(SynchronizedDataManager<H,O> manager,Consumer<String> output){this.manager=Objects.requireNonNull(manager);this.output=output==null?s->{}:output;}
    public DataExport(SynchronizedDataManager<H,O> manager,ServerCommandSource output){this(manager,s->output.sendFeedback(()->net.minecraft.text.Text.literal(s),false));}
    public void setCallback(Runnable callback){this.callback=callback==null?()->{}:callback;}
    public boolean start(Supplier<Database<H,O>> from,Supplier<Database<H,O>> to){
        Database<H,O> source=null,target=null;
        try{
            source=from.get();target=to.get();source.setup();target.setup();List<UUID> ids=source.retrieveAllPlayerIds();int done=0;
            for(UUID id:ids){H holder=manager.setup(id);DataLoadResultCompat.load(source,holder);target.saveData(holder,SessionUpdateReason.AUTOSAVE);done++;if(done%50==0)output.accept("Exported "+done+"/"+ids.size());}
            output.accept("Export complete: "+done+" player record(s)");callback.run();return true;
        }catch(RuntimeException e){output.accept("Export failed: "+e.getMessage());return false;}
        finally{if(source!=null)source.close();if(target!=null)target.close();}
    }
    private static final class DataLoadResultCompat{static <H extends SynchronizedDataHolder,O extends OfflineDataHolder> void load(Database<H,O> db,H h){var r=db.loadData(h,true);if(r==null)throw new IllegalStateException("Database returned no result");}}
}
