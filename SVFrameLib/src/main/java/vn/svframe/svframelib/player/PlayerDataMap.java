package vn.svframe.svframelib.player;

import vn.svframe.svframelib.SVFrameLib;

import java.util.logging.Level;

public abstract class PlayerDataMap {
    protected boolean sessionOpen = false;

    public void openSession() {
        if (sessionOpen) {
            throw new IllegalStateException("Session already open");
        }

        sessionOpen = true;
        try {
            onSessionOpen();
        } catch (Exception exception) {
            SVFrameLib.plugin.getLogger().log(Level.WARNING,
                    "Exception while opening data session of " + getClass().getSimpleName(), exception);
        }
    }

    public void closeSession() {
        if (!sessionOpen) {
            throw new IllegalStateException("Session not open");
        }

        sessionOpen = false;
        try {
            onSessionClose();
        } catch (Exception exception) {
            SVFrameLib.plugin.getLogger().log(Level.WARNING,
                    "Exception while closing data session of " + getClass().getSimpleName(), exception);
        }
    }

    protected void onSessionOpen() {
    }

    protected void onSessionClose() {
    }
}
