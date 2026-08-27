package vn.svframe.svframemmo.trigger;

import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;

/** Native trigger contract used by quests, experience tables and skill trees. */
public interface Trigger {
    void apply(PlayerData player);
    default void remove(PlayerData player) { }
    default boolean removable() { return false; }
    default boolean temporary() { return false; }
    default long delayTicks() { return 0L; }
    default void schedule(PlayerData player) {
        long delay = delayTicks();
        if (delay <= 0L) apply(player);
        else SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + delay, () -> apply(player));
    }
}
