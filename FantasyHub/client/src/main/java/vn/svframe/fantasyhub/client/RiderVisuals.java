package vn.svframe.fantasyhub.client;

import java.util.HashMap;
import java.util.Map;
import vn.svframe.fantasyhub.protocol.Protocol;

/** Immutable snapshots from the server; this client never chooses a gameplay form. */
public final class RiderVisuals {
  private static final Map<Integer, Protocol.Rider> STATES = new HashMap<>();

  private RiderVisuals() {}

  public static Protocol.Rider state(int entity) {
    return STATES.get(entity);
  }

  static void accept(Protocol.Rider packet) {
    if (!RiderModels.WEARABLE.contains(packet.form())) {
      STATES.remove(packet.entityId());
      return;
    }
    STATES.put(packet.entityId(), packet);
  }

  static void remove(int entity) {
    STATES.remove(entity);
  }

  static void clear() {
    STATES.clear();
  }
}
