package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.experience.EXPSource;
import vn.svframe.svframemmo.experience.Profession;

/** Cancellable, mutable experience event matching SVFrameMMO's progression contract. */
public final class PlayerExperienceGainEvent {
    @FunctionalInterface public interface Listener { void onExperience(PlayerExperienceGainEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onExperience(event);
    });

    private final PlayerData data;
    private final Profession profession;
    private final EXPSource source;
    private final double originalExperience;
    private double experience;
    private boolean cancelled;

    public PlayerExperienceGainEvent(PlayerData data, Profession profession, double experience, EXPSource source) {
        this.data = data;
        this.profession = profession;
        this.originalExperience = experience;
        this.experience = experience;
        this.source = source;
    }

    public PlayerData getData() { return data; }
    public net.minecraft.server.network.ServerPlayerEntity getPlayer() { return data.getPlayer(); }
    public Profession getProfession() { return profession; }
    public boolean isMainExperience() { return profession == null; }
    public EXPSource getSource() { return source; }
    public double getOriginalExperience() { return originalExperience; }
    public double getExperience() { return experience; }
    public void setExperience(double experience) { this.experience = experience; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public PlayerExperienceGainEvent call() { EVENT.invoker().onExperience(this); return this; }
}
