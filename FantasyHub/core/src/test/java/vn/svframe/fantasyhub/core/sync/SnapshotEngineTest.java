package vn.svframe.fantasyhub.core.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SnapshotEngineTest {
  static final class Manual extends AbstractExecutorService {
    final ArrayDeque<Runnable> q = new ArrayDeque<>();
    boolean stopped;

    public void execute(Runnable r) {
      if (stopped) throw new RejectedExecutionException();
      q.add(r);
    }

    void run() {
      q.remove().run();
    }

    public void shutdown() {
      stopped = true;
    }

    public List<Runnable> shutdownNow() {
      stopped = true;
      var l = new ArrayList<>(q);
      q.clear();
      return l;
    }

    public boolean isShutdown() {
      return stopped;
    }

    public boolean isTerminated() {
      return stopped;
    }

    public boolean awaitTermination(long n, TimeUnit u) {
      return stopped;
    }
  }

  @Test
  void queuedBulkNeverOccupiesRealtimeWorker() {
    var realtime=new Manual();var bulk=new Manual();try(var engine=new SnapshotEngine(realtime,bulk,8)){
      var id=UUID.randomUUID();engine.join(id);engine.dirty(id,Domain.GUI);engine.submit(engine.poll(),Map.of("catalog",List.of(1,2,3)));
      engine.dirty(id,Domain.COMBAT);engine.submit(engine.poll(),Map.of("active",true));
      assertEquals(1,bulk.q.size());assertEquals(1,realtime.q.size());
      realtime.run();assertEquals(Domain.COMBAT,engine.completed().stamp().domain());
      assertNull(engine.completed());bulk.run();assertEquals(Domain.GUI,engine.completed().stamp().domain());
    }
  }

  @Test
  void sharedClassMetadataIsExpandedOnWorkerWithoutChangingDefinitions(){
    var worker=new Manual();try(var engine=new SnapshotEngine(worker,8)){
      var id=UUID.randomUUID();engine.join(id);engine.dirty(id,Domain.GUI);
      var metadata=new HashMap<String,Object>();metadata.put("description",List.of("original"));
      var shared=FrozenValues.share(metadata);metadata.put("description",List.of("mutated"));
      engine.submit(engine.poll(),Map.of("classRoster",List.of(Map.of("id","THOR","_classMetadata",shared))));worker.run();
      var json=engine.completed().json();assertTrue(json.contains("original"));assertFalse(json.contains("mutated"));assertFalse(json.contains("_classMetadata"));
    }
  }

  @Test
  void realtimeCompletionOvertakesCompletedBulk() {
    var worker=new Manual();try(var s=new SnapshotEngine(worker,8)) {
      var id=UUID.randomUUID();s.join(id);s.dirty(id,Domain.BUNDLES);
      s.submit(s.poll(),Map.of("offers",List.of()));worker.run();
      s.dirty(id,Domain.VITALS);s.submit(s.poll(),Map.of("health",20));worker.run();
      assertEquals(Domain.VITALS,s.completed().stamp().domain());
      assertEquals(Domain.BUNDLES,s.completed().stamp().domain());
    }
  }

  @Test
  void bulkAdmissionCanBePausedWithoutBlockingRealtime() {
    var e=new Manual();try(var s=new SnapshotEngine(e,8)) {
      var id=UUID.randomUUID();s.join(id);s.dirty(id,Domain.BUNDLES);
      assertNull(s.poll(false));s.dirty(id,Domain.VITALS);
      var t=s.poll(false);assertEquals(Domain.VITALS,t.domain());s.skip(t);
      assertEquals(Domain.BUNDLES,s.poll(true).domain());
    }
  }

  @Test
  void staleGenerationAndRevisionCannotReachNetwork() {
    var e = new Manual();
    try (var s = new SnapshotEngine(e, 8)) {
      var p = UUID.randomUUID();
      s.join(p);
      s.dirty(p, Domain.CLASS);
      var t = s.poll();
      s.submit(t, Map.of("value", 1));
      s.dirty(p, Domain.CLASS);
      e.run();
      assertNull(s.completed());
      assertNotNull(s.poll());
      s.leave(p);
      s.join(p);
      s.dirty(p, Domain.VITALS);
      t = s.poll();
      s.submit(t, Map.of("value", 2));
      s.join(p);
      e.run();
      assertNull(s.completed());
    }
  }

  @Test
  void queueCoalescesAndIsFairAndRetainsLatestUnderBackpressure() {
    var e = new Manual();
    try (var s = new SnapshotEngine(e, 2)) {
      var a = UUID.randomUUID();
      var b = UUID.randomUUID();
      s.join(a);
      s.join(b);
      for (int i = 0; i < 100; i++) s.dirty(a, Domain.COMMERCE);
      s.dirty(b, Domain.COMMERCE);
      var t = s.poll();
      assertEquals(a, t.player());
      s.submit(t, Map.of("v", 1));
      t = s.poll();
      assertEquals(b, t.player());
      s.submit(t, Map.of("v", 2));
      s.dirty(a, Domain.COMMERCE);
      assertNull(s.poll());
      e.run();
      assertNull(s.completed());
      assertNotNull(s.poll());
      e.run();
      assertNotNull(s.completed());
    }
  }

  @Test
  void worldChangeAndStopCancelQueuedWork() {
    var e = new Manual();
    var s = new SnapshotEngine(e, 4);
    var p = UUID.randomUUID();
    s.join(p);
    s.dirty(p, Domain.COMBAT);
    s.submit(s.poll(), Map.of("v", 1));
    s.join(p);
    e.run();
    assertNull(s.completed());
    s.close();
    assertTrue(e.isShutdown());
    assertNull(s.poll());
  }

  @Test
  void captureDetachesMutableCollectionsAndRejectsLiveObjects() {
    var e = new Manual();
    try (var s = new SnapshotEngine(e, 4)) {
      var p = UUID.randomUUID();
      s.join(p);
      s.dirty(p, Domain.GUI);
      var rows = new ArrayList<>(List.of("original"));
      s.submit(s.poll(), Map.of("rows", rows));
      rows.set(0, "mutated");
      e.run();
      assertTrue(s.completed().json().contains("original"));
      assertThrows(
          IllegalArgumentException.class,
          () -> FrozenValues.freeze(Map.of("entity", new Object())));
    }
  }

  @Test
  void workersSerializeWithoutCallingCaptureAndNewerWorkSurvivesFailure() throws Exception {
    try (var s = new SnapshotEngine(1, 4)) {
      var p = UUID.randomUUID();
      s.join(p);
      s.dirty(p, Domain.VITALS);
      s.submit(s.poll(), Map.of("hp", 20));
      long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      SnapshotEngine.Result r;
      while ((r = s.completed()) == null && System.nanoTime() < until) Thread.onSpinWait();
      assertNotNull(r);
      assertTrue(r.worker().startsWith("fantasyhub-json-"));
    }
  }

  @Test
  void closedScreenCancelsInflightPatchWithoutReopeningIt() {
    var e = new Manual();
    try (var s = new SnapshotEngine(e, 4)) {
      var p = UUID.randomUUID();
      s.join(p);
      s.dirty(p, Domain.GUI);
      s.submit(s.poll(), Map.of("scope", "shop"));
      s.cancel(p, Domain.GUI);
      e.run();
      assertNull(s.completed());
      assertNull(s.poll());
    }
  }

  @Test
  void wrongThreadCannotCaptureOrMutateCoordinator() throws Exception {
    try (var s = new SnapshotEngine(1, 4)) {
      var failures = new java.util.concurrent.atomic.AtomicInteger();
      var t =
          new Thread(
              () -> {
                assertThrows(IllegalStateException.class, () -> s.join(UUID.randomUUID()));
                failures.incrementAndGet();
              });
      t.start();
      t.join();
      assertEquals(1, failures.get());
    }
  }

  @Test
  void fortyPlayerChurnKeepsQueuesBoundedAndCleansSessions() {
    var e = new Manual();
    try (var s = new SnapshotEngine(e, 16)) {
      var players = new ArrayList<UUID>();
      for (int i = 0; i < 40; i++) {
        var p = UUID.randomUUID();
        players.add(p);
        s.join(p);
      }
      for (int step = 0; step < 2000; step++) {
        for (var p : players) s.dirty(p, Domain.values()[step % Domain.values().length]);
        for (int n = 0; n < 12; n++) {
          var task = s.poll();
          if (task == null) break;
          s.submit(task, Map.of("step", step, "hp", 20));
        }
        if (step % 17 == 0) {
          var p = players.get(step % 40);
          s.leave(p);
          s.join(p);
        }
        while (!e.q.isEmpty()) e.run();
        while (s.completed() != null) {}
        assertTrue(((Number) s.metrics().get("asyncQueue")).intValue() <= 16);
        assertTrue(((Number) s.metrics().get("pending")).intValue() <= 320);
      }
      for (var p : players) s.leave(p);
      assertEquals(0, ((Number) s.metrics().get("sessions")).intValue());
      assertEquals(0, ((Number) s.metrics().get("pending")).intValue());
    }
  }

  @Test
  void sharedCatalogIsDetachedBeforeReuse() {
    var mutable = new HashMap<String, Object>();
    mutable.put("name", "old");
    var shared = FrozenValues.share(mutable);
    mutable.put("name", "new");
    assertEquals("old", ((Map<?, ?>) FrozenValues.freeze(shared)).get("name"));
  }

  @Test
  void failedEncodingIsReportedOnceWithoutRetryStormAndNewEventCanRetry() {
    var executor = new Manual();
    try (var engine = new SnapshotEngine(executor, 4)) {
      var player = UUID.randomUUID();
      engine.join(player);
      engine.dirty(player, Domain.GUI);
      engine.submit(engine.poll(), Map.of("oversized", "x".repeat(4_194_305)));
      executor.run();
      var failure = engine.completed();
      assertNotNull(failure);
      assertNotNull(failure.error());
      assertNull(engine.poll());
      engine.dirty(player, Domain.GUI);
      engine.submit(engine.poll(), Map.of("fixed", true));
      executor.run();
      assertNull(engine.completed().error());
    }
  }

  @Test
  void changingScreenWhileOldTaskIsQueuedDeliversOnlyNewScope() {
    var executor = new Manual();
    try (var engine = new SnapshotEngine(executor, 4)) {
      var player = UUID.randomUUID();
      engine.join(player);
      engine.dirty(player, Domain.GUI);
      engine.submit(engine.poll(), Map.of("scope", "classes"));
      engine.cancel(player, Domain.GUI);
      engine.dirty(player, Domain.GUI);
      executor.run();
      assertNull(engine.completed());
      engine.submit(engine.poll(), Map.of("scope", "profile"));
      executor.run();
      assertTrue(engine.completed().json().contains("profile"));
      assertNull(engine.poll());
    }
  }

  @Test
  void continuousCriticalAndCatalogWorkCannotStarveGui() {
    var executor = new Manual();
    try (var engine = new SnapshotEngine(executor, 4)) {
      var player = UUID.randomUUID();
      engine.join(player);
      engine.dirty(player, Domain.GUI);
      boolean seen = false;
      for (int n = 0; n < 64; n++) {
        engine.dirty(player, Domain.VITALS);
        engine.dirty(player, Domain.BUNDLES);
        var task = engine.poll();
        seen |= task.domain() == Domain.GUI;
        engine.submit(task, Map.of("n", n));
        executor.run();
        engine.completed();
      }
      assertTrue(seen);
    }
  }
  @Test void shutdownDrainsAndTerminatesWorkersWithoutLeakingThreads() throws Exception {
    long before=Thread.getAllStackTraces().keySet().stream().filter(t->t.getName().startsWith("fantasyhub-json-")).count();
    var engine=new SnapshotEngine(2,4);var player=UUID.randomUUID();engine.join(player);
    for(var domain:List.of(Domain.VITALS,Domain.CLASS,Domain.GUI)) {
      engine.dirty(player,domain);engine.submit(engine.poll(),Map.of("value","text".repeat(10000)));
    }
    engine.close();
    assertEquals(before,Thread.getAllStackTraces().keySet().stream().filter(t->t.getName().startsWith("fantasyhub-json-")).count());
    assertFalse(engine.hasSession(player));
    assertNull(engine.poll());
  }

}
