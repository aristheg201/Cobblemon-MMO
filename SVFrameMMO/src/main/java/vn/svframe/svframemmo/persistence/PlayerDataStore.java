package vn.svframe.svframemmo.persistence;

import java.util.Map;
import java.util.UUID;

/** Storage contract for native player snapshots. Implementations must fully replace a logical save transaction. */
public interface PlayerDataStore extends AutoCloseable {
    String id();
    Map<UUID, PlayerDataSnapshot> loadAll() throws Exception;
    void saveAll(Map<UUID, PlayerDataSnapshot> snapshots) throws Exception;
    @Override default void close() throws Exception { }
}
