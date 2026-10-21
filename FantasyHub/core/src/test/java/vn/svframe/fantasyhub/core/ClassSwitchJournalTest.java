package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassSwitchJournalTest {
  @TempDir Path world;
  @Test void uncertainDebitSurvivesRestartAndCannotBeOverwrittenBySpam() throws Exception {
    UUID player = UUID.randomUUID();
    var journal = new ClassSwitchJournal(world, player);
    var entry = new ClassSwitchJournal.Entry(UUID.randomUUID().toString(), "THOR", "FLASH", "beconomy:secondary", "3000", "5000");
    journal.prepare(entry);
    var restarted = new ClassSwitchJournal(world, player);
    assertEquals(entry, restarted.read());
    assertThrows(java.io.IOException.class, () -> restarted.prepare(new ClassSwitchJournal.Entry("different", "THOR", "BATMAN", "beconomy:secondary", "3000", "2000")));
    assertEquals(entry, restarted.read());
    restarted.clear();
    assertNull(new ClassSwitchJournal(world, player).read());
  }
  @Test void reconciliationOfOnePlayerDoesNotEraseAnotherPlayerPendingDebit() throws Exception {
    var first = new ClassSwitchJournal(world, UUID.randomUUID());
    var second = new ClassSwitchJournal(world, UUID.randomUUID());
    var entry = new ClassSwitchJournal.Entry("tx", "THOR", "FLASH", "beconomy:secondary", "3000", "5000");
    first.prepare(entry); second.prepare(entry);
    first.clear();
    assertNull(first.read()); assertEquals(entry, second.read());
  }
}
