package vn.svframe.fantasyhub.core.sync;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vn.svframe.fantasyhub.protocol.SnapshotEncoding;

class SnapshotEncodingTest {
  @Test
  void smallAndLargeUnicodeSnapshotsRoundTrip() {
    for (String json : new String[] {"{\"title\":\"Sấm chớp\"}", "Đá vô cực ".repeat(10000)}) {
      byte[] wire = SnapshotEncoding.encode(json);
      assertEquals(json, SnapshotEncoding.decode(wire));
      if (json.length() > 1024) assertTrue(wire.length < json.length() / 10);
    }
  }

  @Test
  void invalidModesAndOversizedPayloadsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> SnapshotEncoding.decode(new byte[0]));
    assertThrows(IllegalArgumentException.class, () -> SnapshotEncoding.decode(new byte[] {2}));
    assertThrows(IllegalArgumentException.class, () -> SnapshotEncoding.decode(new byte[1026]));
    assertThrows(
        IllegalArgumentException.class, () -> SnapshotEncoding.encode("x".repeat(4_194_305)));
  }
}
