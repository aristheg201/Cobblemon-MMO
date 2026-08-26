package vn.svframe.svframelib.data;
import vn.svframe.svframelib.data.queue.DataLoadResult; import vn.svframe.svframelib.module.MMOPlugin; import vn.svframe.svframelib.profile.SessionUpdateReason;
import java.util.*;
public interface Database<H extends SynchronizedDataHolder,O extends OfflineDataHolder> extends vn.svframe.svframelib.util.Closeable {
    MMOPlugin getPlugin(); void setup(); List<UUID> retrieveAllPlayerIds(); void saveData(H data,SessionUpdateReason reason); DataLoadResult loadData(H data,boolean sync); void confirmReception(H data); O getOffline(UUID uniqueId);
}
