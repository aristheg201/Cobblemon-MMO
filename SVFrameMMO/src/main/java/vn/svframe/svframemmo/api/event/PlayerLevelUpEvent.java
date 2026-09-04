package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.experience.Profession;

import java.util.Objects;

/** Deprecated level-up compatibility event. Prefer PlayerLevelChangeEvent with reason LEVEL_UP. */
@Deprecated
public final class PlayerLevelUpEvent {
    @FunctionalInterface public interface Listener { void onLevelUp(PlayerLevelUpEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> { for (Listener listener : listeners) listener.onLevelUp(event); });

    private final PlayerData data;
    private final Profession profession;
    private final int oldLevel;
    private final int newLevel;

    public PlayerLevelUpEvent(PlayerData data, int oldLevel, int newLevel) { this(data, null, oldLevel, newLevel); }
    public PlayerLevelUpEvent(PlayerData data, Profession profession, int oldLevel, int newLevel) {
        this.data = Objects.requireNonNull(data, "data");
        this.profession = profession;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }
    public PlayerData getData() { return data; }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }
    public boolean hasProfession() { return profession != null; }
    public Profession getProfession() { return profession; }
    public PlayerLevelUpEvent call() { EVENT.invoker().onLevelUp(this); return this; }
}
