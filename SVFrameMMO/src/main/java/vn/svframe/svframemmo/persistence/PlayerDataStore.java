package vn.svframe.svframemmo.persistence;

import java.util.Map;
import java.util.UUID;

/** Storage contract for native player snapshots. */
public interface PlayerDataStore extends AutoCloseable {
    String id();
    Map<UUID, PlayerDataSnapshot> loadAll() throws Exception;

    /** Replaces the complete logical dataset. Used for migration, export and final shutdown flushes. */
    void saveAll(Map<UUID, PlayerDataSnapshot> snapshots) throws Exception;

    /** Upserts only the supplied records without touching other persisted players. */
    void saveSome(Map<UUID, PlayerDataSnapshot> snapshots) throws Exception;

    default void saveOne(UUID id, PlayerDataSnapshot snapshot) throws Exception {
        if (id == null || snapshot == null) return;
        saveSome(Map.of(id, snapshot));
    }

    @Override default void close() throws Exception { }
}
