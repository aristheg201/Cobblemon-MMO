package vn.svframe.svframelib.player.resource;
public interface AbstractHealthUpdateEvent {
    double getOldAmount(); ResourceUpdateReason getUpdateReason(); double getNewAmount(); boolean isCancelled(); void setCancelled(boolean cancelled);
}
