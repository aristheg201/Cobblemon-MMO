package io.lumine.mythic.lib.data;
import io.lumine.mythic.lib.data.queue.DataLoadResult; import io.lumine.mythic.lib.module.MMOPlugin; import io.lumine.mythic.lib.profile.SessionUpdateReason;
import java.util.*;
public interface Database<H extends SynchronizedDataHolder,O extends OfflineDataHolder> extends io.lumine.mythic.lib.util.Closeable {
    MMOPlugin getPlugin(); void setup(); List<UUID> retrieveAllPlayerIds(); void saveData(H data,SessionUpdateReason reason); DataLoadResult loadData(H data,boolean sync); void confirmReception(H data); O getOffline(UUID uniqueId);
}
