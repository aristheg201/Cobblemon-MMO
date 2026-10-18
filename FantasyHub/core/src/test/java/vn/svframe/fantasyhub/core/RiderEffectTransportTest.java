package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;
import vn.svframe.fantasyhub.protocol.Protocol;

class RiderEffectTransportTest {
  @Test
  void configuredCardCountSurvivesTheRealPacketCodec() {
    for (int count : new int[] {5, 6, 7}) {
      var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
      try {
        var event = new Protocol.RiderFx(12, "DECADE", "DIMENSION", 1, 65, 2, 1, 65, 18, 60, count);
        Protocol.RiderFx.CODEC.encode(buffer, event);
        assertEquals(event, Protocol.RiderFx.CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
      } finally {
        buffer.release();
      }
    }
  }
}
