package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;

public final class PlayerResourceUpdateEvent {
    @FunctionalInterface public interface Listener { void onResourceUpdate(PlayerResourceUpdateEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> { for (Listener l:listeners) l.onResourceUpdate(event); });
    private final PlayerData data; private final PlayerResource resource; private final ResourceUpdateReason reason; private final double oldAmount, originalNewAmount; private double newAmount; private boolean cancelled;
    public PlayerResourceUpdateEvent(PlayerData data, PlayerResource resource, double oldAmount, double newAmount, ResourceUpdateReason reason){this.data=data;this.resource=resource;this.oldAmount=oldAmount;this.originalNewAmount=newAmount;this.newAmount=newAmount;this.reason=reason;}
    public PlayerData getData(){return data;} public net.minecraft.server.network.ServerPlayerEntity getPlayer(){return data.getPlayer();} public PlayerResource getResource(){return resource;} public ResourceUpdateReason getUpdateReason(){return reason;}
    public double getOldAmount(){return oldAmount;} public double getNewAmount(){return newAmount;} public double getOriginalNewAmount(){return originalNewAmount;} public double getDifference(){return newAmount-oldAmount;} public void setNewAmount(double v){newAmount=v;} public boolean isCancelled(){return cancelled;} public void setCancelled(boolean v){cancelled=v;}
    public PlayerResourceUpdateEvent call(){EVENT.invoker().onResourceUpdate(this);return this;}
}
