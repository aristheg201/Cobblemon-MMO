package vn.svframe.fantasyhub.core.sync;

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinator belongs to the server thread. Workers see only Stamp + detached immutable JSON
 * values.
 */
public final class SnapshotEngine implements AutoCloseable {
  public record Stamp(
      UUID player,
      long generation,
      Domain domain,
      long revision,
      long sequence,
      long capturedAtNanos) {}

  public record Result(
      Stamp stamp, String json, byte[] encoded, long nanos, String worker, String error) {}

  private static final Gson JSON = new Gson();
  private static final AtomicInteger THREAD_IDS = new AtomicInteger();

  private static final class Session {
    final long generation;
    final long[] rev = new long[Domain.values().length];
    final boolean[] queued = new boolean[rev.length],
        running = new boolean[rev.length],
        enabled = new boolean[rev.length];

    Session(long g) {
      generation = g;
      Arrays.fill(enabled, true);
    }
  }

  private record Key(UUID player, Domain domain) {}

  private final Map<UUID, Session> sessions = new HashMap<>();
  private final ArrayDeque<Key>[] queues;
  private final ExecutorService executor;
  private final ArrayBlockingQueue<Result> results;
  private final int capacity;
  private long generation, sequence;
  private int inFlight, burst, fairnessCursor, inFlightAtReset;
  private volatile boolean closed;
  private final Thread owner = Thread.currentThread();

  private void requireOwner() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("Snapshot coordinator requires its server thread");
  }

  private long submitted, completed, coalesced, stale, rejected, errors, serializedNanos, bytes;

  @SuppressWarnings("unchecked")
  public SnapshotEngine(ExecutorService e, int capacity) {
    this.executor = e;
    this.capacity = capacity;
    results = new ArrayBlockingQueue<>(capacity);
    queues = new ArrayDeque[Domain.values().length];
    Arrays.setAll(queues, i -> new ArrayDeque<>());
  }

  public SnapshotEngine(int workers, int capacity) {
    this(
        new ThreadPoolExecutor(
            workers,
            workers,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(capacity),
            r -> {
              var t = new Thread(r, "fantasyhub-json-" + THREAD_IDS.incrementAndGet());
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy()),
        capacity);
  }

  public void join(UUID p) {
    requireOwner();
    leave(p);
    if (sessions.size() >= 1024)
      throw new IllegalStateException("FantasyHub session limit reached");
    sessions.put(p, new Session(++generation));
  }

  public void leave(UUID p) {
    requireOwner();
    sessions.remove(p);
    for (var q : queues) q.removeIf(k -> k.player.equals(p));
  }

  public void dirty(UUID p, Domain d) {
    requireOwner();
    var s = sessions.get(p);
    if (s == null || closed) return;
    int n = d.ordinal();
    s.enabled[n] = true;
    s.rev[n]++;
    if (s.queued[n] || s.running[n]) {
      coalesced++;
      return;
    }
    enqueue(p, s, d);
  }

  public void cancel(UUID p, Domain d) {
    requireOwner();
    var s = sessions.get(p);
    if (s == null) return;
    int n = d.ordinal();
    s.rev[n]++;
    s.enabled[n] = false;
    s.queued[n] = false;
    queues[n].removeIf(k -> k.player.equals(p));
  }

  private void enqueue(UUID p, Session s, Domain d) {
    s.queued[d.ordinal()] = true;
    queues[d.ordinal()].add(new Key(p, d));
  }

  public Stamp poll() {
    requireOwner();
    if (closed || inFlight >= capacity) return null;
    int index = -1;
    for (int i = 0; i < queues.length; i++)
      if (!queues[i].isEmpty()) {
        index = i;
        break;
      }
    if (index < 0) return null;
    // Reserve one of every eight picks for an aging domain, rotating between them.
    // Always choosing the very last domain would starve GUI under continuous catalog load.
    if (++burst >= 8) {
      for (int offset = 1; offset <= queues.length; offset++) {
        int candidate = (fairnessCursor + offset) % queues.length;
        if (candidate > index && !queues[candidate].isEmpty()) {
          index = candidate;
          fairnessCursor = candidate;
          break;
        }
      }
      burst = 0;
    }
    Key k = queues[index].remove();
    var s = sessions.get(k.player);
    if (s == null) return poll();
    s.queued[index] = false;
    s.running[index] = true;
    inFlight++;
    return new Stamp(k.player, s.generation, k.domain, s.rev[index], ++sequence, System.nanoTime());
  }

  public void submit(Stamp t, Map<String, Object> captured) {
    requireOwner();
    final Object frozen;
    try {
      frozen = FrozenValues.freeze(captured);
    } catch (RuntimeException e) {
      throw e;
    }
    try {
      executor.execute(
          () -> {
            long start = System.nanoTime();
            String json = null, error = null;
            byte[] encoded = null;
            try {
              json = JSON.toJson(transform(frozen));
              encoded = vn.svframe.fantasyhub.protocol.SnapshotEncoding.encode(json);
            } catch (RuntimeException e) {
              error = e.toString();
            }
            if (!closed)
              results.offer(
                  new Result(
                      t,
                      json,
                      encoded,
                      System.nanoTime() - start,
                      Thread.currentThread().getName(),
                      error));
          });
      submitted++;
    } catch (RejectedExecutionException e) {
      rejected++;
      retry(t);
    }
  }

  public void skip(Stamp t) {
    requireOwner();
    inFlight--;
    var s = sessions.get(t.player);
    if (s != null && s.generation == t.generation) s.running[t.domain.ordinal()] = false;
  }

  @SuppressWarnings("unchecked")
  private static Object transform(Object value) {
    if(value instanceof Map<?,?> profile && profile.get("_profile") instanceof Map<?,?> details) return details;
    if (!(value instanceof Map<?, ?> root)
        || !(root.get("fantasyShop") instanceof Map<?, ?> shop)
        || !(shop.get("_templates") instanceof Map<?, ?> templates)) return value;
    var result = new LinkedHashMap<String, Object>((Map<String, Object>) root);
    var transformed = new LinkedHashMap<String, Object>((Map<String, Object>) shop);
    transformed.remove("_templates");
    var rows = new ArrayList<Map<String, Object>>();
    for (var raw : (List<?>) shop.get("offers")) {
      var overlay = (Map<String, Object>) raw;
      var row =
          new LinkedHashMap<String, Object>(
              (Map<String, Object>) templates.get(overlay.get("_template")));
      row.putAll(overlay);
      row.remove("_template");
      rows.add(row);
    }
    transformed.put("offers", rows);
    result.put("fantasyShop", transformed);
    return result;
  }

  public void abandon(Stamp t) {
    requireOwner();
    retry(t);
  }

  private void retry(Stamp t) {
    inFlight--;
    var s = sessions.get(t.player);
    if (s != null && s.generation == t.generation) {
      s.running[t.domain.ordinal()] = false;
      if (s.enabled[t.domain.ordinal()] && !s.queued[t.domain.ordinal()])
        enqueue(t.player, s, t.domain);
    }
  }

  public Result completed() {
    requireOwner();
    Result r;
    while ((r = results.poll()) != null) {
      inFlight--;
      serializedNanos += r.nanos;
      var t = r.stamp;
      var s = sessions.get(t.player);
      if (s == null || s.generation != t.generation) {
        stale++;
        continue;
      }
      int n = t.domain.ordinal();
      s.running[n] = false;
      if (t.revision != s.rev[n]) {
        stale++;
        if (s.enabled[n] && !s.queued[n]) enqueue(t.player, s, t.domain);
        continue;
      }
      if (r.error != null) {
        errors++;
        // Surface one diagnostic. A later actual event/request may retry repaired input.
        s.enabled[n] = false;
        return r;
      }
      completed++;
      bytes += r.json.length();
      return r;
    }
    return null;
  }

  public Map<String, Object> metrics() {
    requireOwner();
    var m = new LinkedHashMap<String, Object>();
    m.put("sessions", sessions.size());
    m.put("pending", Arrays.stream(queues).mapToInt(ArrayDeque::size).sum());
    m.put("asyncQueue", inFlight);
    m.put("inFlightAtReset",inFlightAtReset);
    m.put("completionQueue", results.size());
    if (executor instanceof ThreadPoolExecutor pool) {
      m.put("workerQueue", pool.getQueue().size());
      m.put("activeWorkers", pool.getActiveCount());
    }
    m.put("submitted", submitted);
    m.put("completed", completed);
    m.put("coalesced", coalesced);
    m.put("stale", stale);
    m.put("tasksDropped", stale);
    m.put("rejected", rejected);
    m.put("workerErrors", errors);
    m.put("serializationMs", serializedNanos / 1e6);
    m.put("jsonChars", bytes);
    return m;
  }

  public void reset() {
    requireOwner();
    inFlightAtReset=inFlight;
    submitted = completed = coalesced = stale = rejected = errors = serializedNanos = bytes = 0;
  }

  public boolean pending(UUID p, Domain d) {
    var s = sessions.get(p);
    return s != null && (s.queued[d.ordinal()] || s.running[d.ordinal()]);
  }

  public boolean hasSession(UUID p) {
    return sessions.containsKey(p);
  }

  @Override
  public void close() {
    requireOwner();
    closed = true;
    executor.shutdownNow();
    sessions.clear();
    for (var q : queues) q.clear();
    results.clear();
    inFlight = 0;
    try {
      executor.awaitTermination(500, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
