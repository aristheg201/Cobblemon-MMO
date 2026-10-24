package vn.svframe.fantasyhub.core;

import java.util.*;

/** Bounded sample ring. Sorting happens only when an operator requests diagnostics. */
final class SyncTimings {
  private final long[] ring = new long[2048];
  private long count, total, max;

  void add(long n) {
    ring[(int) (count++ % ring.length)] = n;
    total += n;
    max = Math.max(max, n);
  }

  void reset() {
    count = total = max = 0;
  }

  Map<String, Object> view() {
    int n = (int) Math.min(count, ring.length);
    long[] values = Arrays.copyOf(ring, n);
    Arrays.sort(values);
    return Map.of(
        "count",
        count,
        "meanMs",
        count == 0 ? 0 : total / 1e6 / count,
        "p50Ms", n == 0 ? 0 : values[(n-1)/2]/1e6,
        "p95Ms",
        n == 0 ? 0 : values[(n - 1) * 95 / 100] / 1e6,
        "p99Ms",
        n == 0 ? 0 : values[(n - 1) * 99 / 100] / 1e6,
        "maxMs",
        max / 1e6);
  }
}
