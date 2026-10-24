package vn.svframe.fantasyhub.core.sync;

import java.util.*;

/** Only detached JSON values cross the executor boundary; live game objects are rejected. */
public final class FrozenValues {
  private FrozenValues() {}

  public static final class Shared {
    private final Object value;

    private Shared(Object value) {
      this.value = value;
    }
  }

  public static Shared share(Object value) {
    return new Shared(freeze(value));
  }

  public static Object freeze(Object value) {
    return freeze(value, 0, new int[] {0});
  }

  private static Object freeze(Object v, int depth, int[] nodes) {
    if (depth > 48 || ++nodes[0] > 150000)
      throw new IllegalArgumentException("Snapshot exceeds structural bounds");
    if (v instanceof Shared shared) return shared.value;
    if (v == null
        || v instanceof String
        || v instanceof Boolean
        || v instanceof Byte
        || v instanceof Short
        || v instanceof Integer
        || v instanceof Long
        || v instanceof Float
        || v instanceof Double
        || v instanceof java.math.BigDecimal) return v;
    if (v instanceof Map<?, ?> m) {
      var out = new LinkedHashMap<String, Object>();
      for (var e : m.entrySet()) {
        if (!(e.getKey() instanceof String k))
          throw new IllegalArgumentException("Snapshot key must be a string");
        out.put(k, freeze(e.getValue(), depth + 1, nodes));
      }
      return Collections.unmodifiableMap(out);
    }
    if (v instanceof Collection<?> c) {
      var out = new ArrayList<>();
      for (var x : c) out.add(freeze(x, depth + 1, nodes));
      return Collections.unmodifiableList(out);
    }
    throw new IllegalArgumentException("Unsafe snapshot value: " + v.getClass().getName());
  }
}
