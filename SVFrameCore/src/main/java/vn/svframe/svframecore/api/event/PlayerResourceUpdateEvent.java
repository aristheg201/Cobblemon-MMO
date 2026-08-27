package vn.svframe.svframecore.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframecore.api.player.PlayerData;
import vn.svframe.svframecore.api.player.resource.PlayerResource;
import vn.svframe.svframelib.player.resource.AbstractHealthUpdateEvent;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;

import java.util.Objects;

/** Fabric-native mutable/cancellable resource update event matching SVFrameCore 1.13.1 semantics. */
public final class PlayerResourceUpdateEvent implements AbstractHealthUpdateEvent {
    @FunctionalInterface
    public interface Listener { void onResourceUpdate(PlayerResourceUpdateEvent event); }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onResourceUpdate(event);
            });

    private final PlayerData data;
    private final PlayerResource resource;
    private final ResourceUpdateReason reason;
    private final double oldAmount;
    private final double originalNewAmount;
    private double newAmount;
    private boolean cancelled;

    public PlayerResourceUpdateEvent(PlayerData data, PlayerResource resource, double oldAmount, double newAmount, ResourceUpdateReason reason) {
        this.data = Objects.requireNonNull(data, "Player data cannot be null");
        this.resource = Objects.requireNonNull(resource, "Resource cannot be null");
        this.reason = Objects.requireNonNull(reason, "Update reason cannot be null");
        this.oldAmount = oldAmount;
        this.originalNewAmount = newAmount;
        this.newAmount = newAmount;
    }

    public PlayerResourceUpdateEvent call() { EVENT.invoker().onResourceUpdate(this); return this; }
    public PlayerData getData() { return data; }
    public PlayerResource getResource() { return resource; }
    public double getDifference() { return newAmount - oldAmount; }
    public double getOriginalNewAmount() { return originalNewAmount; }
    @Override public double getOldAmount() { return oldAmount; }
    @Override public double getNewAmount() { return newAmount; }
    public void setNewAmount(double newAmount) { this.newAmount = newAmount; }
    @Override public ResourceUpdateReason getUpdateReason() { return reason; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
