package vn.svframe.svframelib.fabric.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class CombatPipeline {
    @FunctionalInterface
    public interface Handler {
        Result apply(Context context, DamagePacket damage);
    }

    public record Context(String attackerId, String targetId, boolean projectile, boolean skill) {}
    public record Result(boolean cancelled, DamagePacket damage) {
        public static Result allow(DamagePacket damage) { return new Result(false, damage); }
        public static Result cancel(DamagePacket damage) { return new Result(true, damage); }
    }

    private record Registered(long order, int priority, Handler handler) {}

    private final AtomicLong order = new AtomicLong();
    private final List<Registered> handlers = new ArrayList<>();

    public synchronized AutoCloseable register(int priority, Handler handler) {
        Registered registered = new Registered(order.getAndIncrement(), priority, Objects.requireNonNull(handler));
        handlers.add(registered);
        handlers.sort(Comparator.comparingInt(Registered::priority).reversed().thenComparingLong(Registered::order));
        return () -> unregister(registered);
    }

    private synchronized void unregister(Registered registered) {
        handlers.remove(registered);
    }

    public Result process(Context context, DamagePacket original) {
        DamagePacket current = Objects.requireNonNull(original).copy();
        List<Registered> snapshot;
        synchronized (this) { snapshot = List.copyOf(handlers); }
        for (Registered entry : snapshot) {
            Result result = Objects.requireNonNull(entry.handler().apply(context, current.copy()), "handler result");
            current = result.damage().copy();
            if (result.cancelled()) return new Result(true, current);
        }
        return new Result(false, current);
    }
}
