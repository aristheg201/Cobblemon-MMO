package vn.svframe.compat;

import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A failed write leaves the in-memory quest dirty and the previous JSON intact for retry. */
public final class SafeQuestStore {
    private static final System.Logger LOG = System.getLogger("CobblePass-QuestStorage");
    private static final ConcurrentHashMap<UUID, Long> FAILURES = new ConcurrentHashMap<>();
    private SafeQuestStore() {}

    public static boolean save(Gson gson, Path root, UUID player, Object snapshot) {
        Path temporary = null;
        try {
            byte[] json = gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(root);
            Path target = root.resolve(player + ".json");
            temporary = Files.createTempFile(root, player + "-", ".tmp");
            try (FileChannel out = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(json);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (FAILURES.remove(player) != null)
                LOG.log(System.Logger.Level.INFO, "Quest save recovered for " + player);
            return true;
        } catch (Exception failure) {
            long now = System.nanoTime();
            Long previous = FAILURES.get(player);
            if (previous == null || now - previous >= 60_000_000_000L) {
                FAILURES.put(player, now);
                LOG.log(System.Logger.Level.ERROR,
                    "Quest save failed for " + player + "; state remains dirty and will retry. Target: " + root, failure);
            }
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (java.io.IOException cleanup) {
                LOG.log(System.Logger.Level.WARNING, "Could not remove quest temporary file " + temporary, cleanup);
            }
        }
    }
}
