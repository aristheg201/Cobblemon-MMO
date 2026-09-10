package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;

public final class PlayerResourceUpdateEvent {
    @FunctionalInterface
    public interface Listener {
        void onResourceUpdate(PlayerResourceUpdateEvent event);
    }

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

    public PlayerResourceUpdateEvent(PlayerData data,
                                     PlayerResource resource,
                                     double oldAmount,
                                     double newAmount,
                                     ResourceUpdateReason reason) {
        this.data = data;
        this.resource = resource;
        this.oldAmount = oldAmount;
        this.originalNewAmount = newAmount;
        this.newAmount = newAmount;
        this.reason = reason;
    }

    public PlayerData getData() { return data; }
    public net.minecraft.server.network.ServerPlayerEntity getPlayer() { return data.getPlayer(); }
    public PlayerResource getResource() { return resource; }
    public ResourceUpdateReason getUpdateReason() { return reason; }
    public double getOldAmount() { return oldAmount; }
    public double getNewAmount() { return newAmount; }
    public double getOriginalNewAmount() { return originalNewAmount; }
    public double getDifference() { return newAmount - oldAmount; }
    public boolean isIncrease() { return Double.compare(newAmount, oldAmount) > 0; }
    public boolean isDecrease() { return Double.compare(newAmount, oldAmount) < 0; }
    public boolean isHealthGain() { return resource == PlayerResource.HEALTH && isIncrease(); }
    public boolean isHealthLoss() { return resource == PlayerResource.HEALTH && isDecrease(); }
    public void setNewAmount(double value) { newAmount = value; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean value) { cancelled = value; }

    public PlayerResourceUpdateEvent call() {
        // CLAMPING and other silent correction reasons are internal state maintenance,
        // not gameplay healing/damage. Respect ResourceUpdateReason's dispatch contract
        // so listeners cannot mistake a health correction for a damage event.
        if (reason.callsEvent()) EVENT.invoker().onResourceUpdate(this);
        return this;
    }
}
