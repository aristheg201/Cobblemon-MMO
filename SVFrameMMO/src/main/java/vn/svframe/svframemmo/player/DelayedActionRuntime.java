package vn.svframe.svframemmo.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Server-thread delayed action queue used by native quest/experience triggers. */
public final class DelayedActionRuntime {
    private record Scheduled(long tick, long sequence, Runnable action) { }
    private final PriorityQueue<Scheduled> queue = new PriorityQueue<>(Comparator.comparingLong(Scheduled::tick).thenComparingLong(Scheduled::sequence));
    private long sequence;

    public synchronized void schedule(long tick, Runnable action) {
        if (action == null) throw new IllegalArgumentException("action");
        queue.add(new Scheduled(tick, sequence++, action));
    }

    public void tick(long currentTick) {
        List<Runnable> due = new ArrayList<>();
        synchronized (this) {
            while (!queue.isEmpty() && queue.peek().tick() <= currentTick) due.add(queue.remove().action());
        }
        for (Runnable action : due) action.run();
    }

    public synchronized int size() { return queue.size(); }
    public synchronized void clear() { queue.clear(); }
}
