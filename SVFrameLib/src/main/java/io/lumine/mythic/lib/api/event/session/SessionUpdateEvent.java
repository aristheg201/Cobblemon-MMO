package io.lumine.mythic.lib.api.event.session;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.profile.ProfileSession;
import io.lumine.mythic.lib.profile.ProfileSessionState;
import io.lumine.mythic.lib.profile.SessionUpdateReason;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Native Fabric replacement for MythicLib's Bukkit SessionUpdateEvent. */
public final class SessionUpdateEvent {
    @FunctionalInterface
    public interface Listener {
        void onSessionUpdate(SessionUpdateEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onSessionUpdate(event);
            });

    private final MMOPlayerData player;
    private final ProfileSession session;
    private final ProfileSessionState oldState;
    private final ProfileSessionState newState;
    private final SessionUpdateReason reason;

    public SessionUpdateEvent(MMOPlayerData player, ProfileSession session, SessionUpdateReason reason,
                              ProfileSessionState oldState, ProfileSessionState newState) {
        this.player = player;
        this.session = session;
        this.reason = reason;
        this.oldState = oldState;
        this.newState = newState;
    }

    public MMOPlayerData getPlayerData() { return player; }
    public ProfileSession getSession() { return session; }
    public SessionUpdateReason getReason() { return reason; }
    public ProfileSessionState getOldState() { return oldState; }
    public ProfileSessionState getNewState() { return newState; }

    public void call() {
        EVENT.invoker().onSessionUpdate(this);
    }
}
