package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;
import vn.svframe.fantasyhub.protocol.StateCompression;

class StateCompressionTest {
  @Test
  void catalogsBeyondOldStringLimitPreserveVietnameseAndItemComponents() {
    String json = "{\"items\":[" + "\"Giáp Kỵ Sĩ • components\",".repeat(16000) + "\"end\"]}";
    assertTrue(json.length() > 262144);
    byte[] wire = StateCompression.encode(json);
    assertTrue(wire.length < 10000);
    assertEquals(json, StateCompression.decode(wire));
  }

  @Test
  void completeBundleCatalogPreservesNativeStacksWithinWireBudget() {
    String json = "{\"bundleItems\":[" + "\"Giáp Agito • components:model,lore,custom_data\",".repeat(40000) + "\"end\"]}";
    assertTrue(json.getBytes(StandardCharsets.UTF_8).length > 1048576);
    byte[] wire = StateCompression.encode(json);
    assertTrue(wire.length < StateCompression.MAX_WIRE_BYTES);
    assertEquals(json, StateCompression.decode(wire));
  }

  @Test
  void malformedAndOversizedInputIsRejected() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> StateCompression.decode(new byte[] {1, 2, 3}));
    assertThrows(IllegalArgumentException.class, () -> StateCompression.decode(new byte[262145]));
    assertThrows(
        IllegalArgumentException.class, () -> StateCompression.encode("x".repeat(StateCompression.MAX_JSON_BYTES + 1)));
    var output = new ByteArrayOutputStream();
    try (var compressed = new DeflaterOutputStream(output)) {
      compressed.write("x".repeat(StateCompression.MAX_JSON_BYTES + 1).getBytes(StandardCharsets.UTF_8));
    }
    assertThrows(
        IllegalArgumentException.class, () -> StateCompression.decode(output.toByteArray()));
  }
}
