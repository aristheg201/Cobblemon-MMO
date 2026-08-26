package vn.svframe.svframelib.message.actionbar;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import net.minecraft.text.Text;

import java.util.Objects;
import java.util.function.Supplier;

/** Native Fabric action-bar arbitration with MythicLib 1.7.1 priority/timeout semantics. */
public class ActionBarHandler {
    public static final long DEFAULT_TIME_OUT = 30L;

    private final MMOPlayerData playerData;
    private int lastPriority;
    private long timeOut;

    public ActionBarHandler(MMOPlayerData playerData) {
        this.playerData = Objects.requireNonNull(playerData, "playerData");
    }

    public boolean canShow(int priority) { return !isBusy() || priority >= lastPriority; }

    public boolean hide(int priority, long duration) { return show(priority + 1, duration, (Supplier<String>) null); }
    public boolean show(String message) { return show(ActionBarPriority.NORMAL, DEFAULT_TIME_OUT, message); }
    public boolean show(int priority, String message) { return show(priority, DEFAULT_TIME_OUT, message); }
    public boolean show(int priority, long duration, String message) { return show(priority, duration, () -> message); }
    public boolean show(int priority, Supplier<String> message) { return show(priority, DEFAULT_TIME_OUT, message); }

    public boolean show(int priority, long duration, Supplier<String> message) {
        if (!canShow(priority)) return false;
        lastPriority = priority;
        timeOut = System.currentTimeMillis() + Math.max(0L, duration) * 50L;
        if (message != null && playerData.isOnline()) {
            String resolved = message.get();
            if (resolved != null) playerData.getPlayer().sendMessage(Text.literal(resolved), true);
        }
        return true;
    }

    public void reset(int priority) {
        if (!canShow(priority)) return;
        lastPriority = priority;
        timeOut = 0L;
    }

    public int getCurrentPriority() { return lastPriority; }
    public boolean isBusy() { return System.currentTimeMillis() < timeOut; }
}
