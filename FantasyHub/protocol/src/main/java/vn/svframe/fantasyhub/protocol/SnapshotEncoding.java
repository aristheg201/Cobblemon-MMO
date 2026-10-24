package vn.svframe.fantasyhub.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Pure transport work. Called by the bounded snapshot worker, never by the server packet codec. */
public final class SnapshotEncoding {
  private SnapshotEncoding() {}

  public static byte[] encode(String json) {
    byte[] raw = json.getBytes(StandardCharsets.UTF_8);
    byte[] content = raw.length <= 1024 ? raw : StateCompression.encode(json);
    byte[] wire = new byte[content.length + 1];
    wire[0] = (byte) (raw.length <= 1024 ? 0 : 1);
    System.arraycopy(content, 0, wire, 1, content.length);
    return wire;
  }

  public static String decode(byte[] wire) {
    if (wire.length < 1 || wire.length > StateCompression.MAX_WIRE_BYTES + 1)
      throw new IllegalArgumentException("Invalid sync payload length");
    byte[] content = Arrays.copyOfRange(wire, 1, wire.length);
    return switch (wire[0]) {
      case 0 -> {
        if (content.length > 1024) throw new IllegalArgumentException("Oversized raw sync payload");
        yield new String(content, StandardCharsets.UTF_8);
      }
      case 1 -> StateCompression.decode(content);
      default -> throw new IllegalArgumentException("Unknown sync payload encoding");
    };
  }
}
