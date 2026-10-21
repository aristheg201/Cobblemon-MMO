package vn.svframe.fantasyhub.core;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** A paid switch crosses two stores. Preserve uncertain debits for explicit administrator recovery. */
final class ClassSwitchJournal {
  record Entry(String transactionId, String sourceClass, String targetClass, String currency,
               String amount, String balanceBefore) {}
  private static final Gson GSON = new Gson();
  private final Path file;
  ClassSwitchJournal(Path world, UUID player) { file = world.resolve("fantasyhub-transactions").resolve(player + ".json"); }
  Entry read() throws IOException {
    if (!Files.exists(file)) return null;
    Entry entry = GSON.fromJson(Files.readString(file), Entry.class);
    if (entry == null || entry.transactionId() == null || entry.currency() == null || entry.amount() == null)
      throw new IOException("Invalid class switch journal: " + file);
    return entry;
  }
  void prepare(Entry entry) throws IOException {
    if (Files.exists(file)) throw new IOException("Unreconciled class switch journal: " + file);
    Files.createDirectories(file.getParent());
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    byte[] bytes = GSON.toJson(entry).getBytes(StandardCharsets.UTF_8);
    try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
      var buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) channel.write(buffer);
      channel.force(true);
    }
    try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
    catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file); }
  }
  void clear() throws IOException { Files.deleteIfExists(file); }
}
