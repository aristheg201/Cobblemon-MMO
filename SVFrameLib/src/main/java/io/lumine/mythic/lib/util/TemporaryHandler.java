package io.lumine.mythic.lib.util;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Native lifecycle equivalent of MythicLib's temporary listener/task handler.
 * Fabric callbacks owned by subclasses must be registered in onOpen and released
 * in onClose; delayed closes are generation-guarded so reopening cancels stale timers.
 */
public abstract class TemporaryHandler implements Closeable {
    private final MMOPlayerData attachedPlayer;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean open;

    protected TemporaryHandler() { this(null); }
    protected TemporaryHandler(MMOPlayerData attachedPlayer) { this.attachedPlayer = attachedPlayer; }

    public final MMOPlayerData getAttachedPlayer() { return attachedPlayer; }
    public final boolean isOpen() { return open; }

    public void open() {
        if (open) return;
        open = true;
        generation.incrementAndGet();
        if (attachedPlayer != null) attachedPlayer.addTemporaryHandler(this);
        onOpen();
    }

    @Override public boolean close() { return closeNow(false); }

    public boolean closeNow(boolean shutdown) {
        if (!open) return false;
        open = false;
        generation.incrementAndGet();
        try { onClose(); }
        finally { if (attachedPlayer != null) attachedPlayer.removeTemporaryHandler(this); }
        return true;
    }

    public void closeAfter(long ticks) {
        long token = generation.get();
        MythicLibFabricMod.schedule((int) Math.min(Integer.MAX_VALUE, Math.max(0L, ticks)), () -> {
            if (open && generation.get() == token) close();
        });
    }

    /** Executes a task immediately while this handler is open. */
    public void runTask(Consumer<TemporaryHandler> task) {
        Objects.requireNonNull(task, "task").accept(this);
    }

    protected void onClose() {}
    protected void onOpen() {}

    public static TemporaryHandler timerTask(MMOPlayerData playerData, long timer,
                                             Function<TemporaryHandler, Runnable> taskFactory) {
        TemporaryHandler handler = new TemporaryHandler(playerData) {};
        handler.open();
        Runnable task = Objects.requireNonNull(taskFactory, "taskFactory").apply(handler);
        if (task != null) task.run();
        handler.closeAfter(timer);
        return handler;
    }

    public static TemporaryHandler task(MMOPlayerData playerData,
                                        Consumer<TemporaryHandler> starter,
                                        Function<TemporaryHandler, Runnable> taskFactory) {
        TemporaryHandler handler = new TemporaryHandler(playerData) {};
        handler.open();
        if (starter != null) starter.accept(handler);
        Runnable task = taskFactory == null ? null : taskFactory.apply(handler);
        if (task != null) task.run();
        return handler;
    }
}
