package vn.svframe.svframelib.util;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/** Fabric-native temporary lifecycle handler matching the 1.7.1 open/close contract. */
public abstract class TemporaryHandler {
    private final MMOPlayerData attachedPlayer;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean open;

    protected TemporaryHandler() { this(null); }
    protected TemporaryHandler(MMOPlayerData attachedPlayer) {
        this.attachedPlayer = attachedPlayer;
        open();
    }

    public final MMOPlayerData getAttachedPlayer() { return attachedPlayer; }
    public final boolean isOpen() { return open; }

    public void open() {
        if (open) throw new IllegalStateException("Handler is already open");
        open = true;
        generation.incrementAndGet();
        if (attachedPlayer != null) attachedPlayer.addTemporaryHandler(this);
        onOpen();
    }

    public boolean close() { return closeNow(false); }

    public boolean closeNow(boolean shutdown) {
        if (!open) return false;
        open = false;
        generation.incrementAndGet();
        if (!shutdown && attachedPlayer != null) attachedPlayer.removeTemporaryHandler(this);
        onClose();
        return true;
    }

    public void closeAfter(long ticks) {
        long token = generation.get();
        SVFrameLibFabricMod.schedule((int) Math.min(Integer.MAX_VALUE, Math.max(0L, ticks)), () -> {
            if (open && generation.get() == token) close();
        });
    }

    public void runTask(Consumer<TemporaryHandler> task) { Objects.requireNonNull(task, "task").accept(this); }
    protected void onClose() { }
    protected void onOpen() { }

    public static TemporaryHandler timerTask(MMOPlayerData playerData, long timer, Function<TemporaryHandler, Runnable> taskFactory) {
        return task(playerData, handler -> handler.closeAfter(timer), taskFactory);
    }

    public static TemporaryHandler task(MMOPlayerData playerData, Consumer<TemporaryHandler> starter, Function<TemporaryHandler, Runnable> taskFactory) {
        TemporaryHandler handler = new TemporaryHandler(playerData) { };
        if (starter != null) starter.accept(handler);
        Runnable task = taskFactory == null ? null : taskFactory.apply(handler);
        if (task != null) task.run();
        return handler;
    }
}
