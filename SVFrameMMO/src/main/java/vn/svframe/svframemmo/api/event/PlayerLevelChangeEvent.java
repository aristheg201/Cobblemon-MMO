package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.experience.Profession;

/** Fired after native class/profession level state changes. */
public final class PlayerLevelChangeEvent {
    public enum Reason { LEVEL_UP, COMMAND, RESET, CHOOSE_CLASS, CHOOSE_PROFILE, UNKNOWN, OTHER }
    @FunctionalInterface public interface Listener { void onLevelChange(PlayerLevelChangeEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onLevelChange(event);
    });

    private final PlayerData data;
    private final Profession profession;
    private final int oldLevel;
    private final int newLevel;
    private final Reason reason;

    public PlayerLevelChangeEvent(PlayerData data, Profession profession, int oldLevel, int newLevel, Reason reason) {
        this.data = data;
        this.profession = profession;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.reason = reason;
    }

    public PlayerData getData() { return data; }
    public net.minecraft.server.network.ServerPlayerEntity getPlayer() { return data.getPlayer(); }
    public Profession getProfession() { return profession; }
    public boolean isMainLevel() { return profession == null; }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }
    public Reason getReason() { return reason; }
    public PlayerLevelChangeEvent call() { EVENT.invoker().onLevelChange(this); return this; }
}
