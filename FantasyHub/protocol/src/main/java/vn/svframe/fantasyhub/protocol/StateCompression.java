package vn.svframe.fantasyhub.protocol;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.zip.*;

/** Bounded catalog transport. Item-component previews compress without losing their data. */
public final class StateCompression {
  public static final int MAX_WIRE_BYTES = 262144;
  public static final int MAX_JSON_BYTES = 4194304;

  private StateCompression() {}

  public static byte[] encode(String json) {
    byte[] source = json.getBytes(StandardCharsets.UTF_8);
    if (source.length > MAX_JSON_BYTES)
      throw new IllegalArgumentException("State catalog too large");
    try {
      var output = new ByteArrayOutputStream();
      try (var compressed = new DeflaterOutputStream(output)) {
        compressed.write(source);
      }
      byte[] result = output.toByteArray();
      if (result.length > MAX_WIRE_BYTES)
        throw new IllegalArgumentException("State packet too large");
      return result;
    } catch (IOException failure) {
      throw new IllegalArgumentException("Unable to encode state catalog", failure);
    }
  }

  public static String decode(byte[] compressed) {
    if (compressed.length > MAX_WIRE_BYTES)
      throw new IllegalArgumentException("State packet too large");
    try (var input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
      byte[] result = input.readNBytes(MAX_JSON_BYTES + 1);
      if (result.length > MAX_JSON_BYTES)
        throw new IllegalArgumentException("State catalog too large");
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(result))
          .toString();
    } catch (IOException failure) {
      throw new IllegalArgumentException("Invalid compressed state catalog", failure);
    }
  }
}
