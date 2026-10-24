package vn.svframe.fantasyhub.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.svframe.fantasyhub.core.sync.Domain;

/**
 * UUID-only mutation mailbox. Coalesces off-thread notifications; never schedules Minecraft work
 * off-thread.
 */
public final class SyncSignals {
  private static final ConcurrentHashMap<UUID, AtomicInteger> signals = new ConcurrentHashMap<>();

  static void join(UUID id) {
    signals.put(id, new AtomicInteger());
  }

  static void leave(UUID id) {
    signals.remove(id);
  }

  static void clear() {
    signals.clear();
  }

  public static void changed(UUID id, Domain... domains) {
    var slot = signals.get(id);
    if (slot == null) return;
    int bits = 0;
    for (var d : domains) bits |= 1 << d.ordinal();
    final int mask = bits;
    slot.getAndUpdate(n -> n | mask);
  }

  static int take(UUID id) {
    var a = signals.get(id);
    return a == null ? 0 : a.getAndSet(0);
  }
}
