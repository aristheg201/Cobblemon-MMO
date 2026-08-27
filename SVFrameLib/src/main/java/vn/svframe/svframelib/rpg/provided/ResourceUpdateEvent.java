package vn.svframe.svframelib.rpg.provided;

import vn.svframe.svframelib.api.event.MMOPlayerDataEvent;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Fabric-native cancellable resource update event replacing server-plugin platform's event bus. */
public class ResourceUpdateEvent extends MMOPlayerDataEvent {
    @FunctionalInterface
    public interface Listener {
        void onResourceUpdate(ResourceUpdateEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onResourceUpdate(event);
            });

    private final PlayerResource type;
    private final ResourceUpdateReason reason;
    private final double oldAmount;
    private double newAmount;
    private boolean cancelled;

    public ResourceUpdateEvent(MMOPlayerData playerData, double oldAmount, double newAmount,
                               ResourceUpdateReason reason, PlayerResource type) {
        super(playerData);
        this.oldAmount = oldAmount;
        this.newAmount = newAmount;
        this.reason = reason;
        this.type = type;
    }

    public double getOldAmount() { return oldAmount; }
    public double getNewAmount() { return newAmount; }
    public void setNewAmount(double newAmount) { this.newAmount = newAmount; }
    public ResourceUpdateReason getReason() { return reason; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public PlayerResource getType() { return type; }

    public ResourceUpdateEvent call() {
        EVENT.invoker().onResourceUpdate(this);
        return this;
    }
}
