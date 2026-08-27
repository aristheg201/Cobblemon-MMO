package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.attribute.PlayerAttribute;

import java.util.Objects;

public final class PlayerAttributeUseEvent {
    @FunctionalInterface public interface Listener { void onAttributeUse(PlayerAttributeUseEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> { for (Listener listener : listeners) listener.onAttributeUse(event); });

    private final PlayerData data;
    private final PlayerAttribute attribute;
    private final int amount;

    public PlayerAttributeUseEvent(PlayerData data, PlayerAttribute attribute, int amount) {
        this.data = Objects.requireNonNull(data, "data");
        this.attribute = Objects.requireNonNull(attribute, "attribute");
        this.amount = Math.max(1, amount);
    }
    public PlayerData getData() { return data; }
    public PlayerAttribute getAttribute() { return attribute; }
    public int getAmount() { return amount; }
    public PlayerAttributeUseEvent call() { EVENT.invoker().onAttributeUse(this); return this; }
}
