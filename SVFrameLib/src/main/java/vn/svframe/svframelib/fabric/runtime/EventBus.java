package vn.svframe.svframelib.fabric.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class EventBus {
    private record Listener(long id, Consumer<Object> consumer) {}
    private final AtomicLong ids = new AtomicLong();
    private final Map<Class<?>, List<Listener>> listeners = new HashMap<>();

    public synchronized <T> AutoCloseable subscribe(Class<T> type, Consumer<? super T> listener) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(listener);
        long id = ids.incrementAndGet();
        Consumer<Object> erased = event -> listener.accept(type.cast(event));
        listeners.computeIfAbsent(type, ignored -> new ArrayList<>()).add(new Listener(id, erased));
        return () -> unsubscribe(type, id);
    }

    private synchronized void unsubscribe(Class<?> type, long id) {
        List<Listener> list = listeners.get(type);
        if (list == null) return;
        list.removeIf(listener -> listener.id() == id);
        if (list.isEmpty()) listeners.remove(type);
    }

    public void publish(Object event) {
        Objects.requireNonNull(event);
        List<Listener> snapshot = new ArrayList<>();
        synchronized (this) {
            for (Map.Entry<Class<?>, List<Listener>> entry : listeners.entrySet()) {
                if (entry.getKey().isAssignableFrom(event.getClass())) snapshot.addAll(entry.getValue());
            }
        }
        for (Listener listener : snapshot) listener.consumer().accept(event);
    }
}
