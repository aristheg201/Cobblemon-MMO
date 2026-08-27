package vn.svframe.svframelib.api.event;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.mitigation.MitigationType;
import vn.svframe.mythiclibfabric.runtime.MythicLibEventHub;

import java.util.Objects;
import java.util.function.Consumer;

/** Native Fabric form of the source 1.7.1 cancellable mitigation event. */
public class DamageMitigationEvent extends MMOPlayerDataEvent {
    private final AttackMetadata attack;
    private final MitigationType type;
    private boolean cancelled;

    public DamageMitigationEvent(MMOPlayerData player, MitigationType type, AttackMetadata attack) {
        super(Objects.requireNonNull(player, "player"));
        this.type = Objects.requireNonNull(type, "type");
        this.attack = Objects.requireNonNull(attack, "attack");
    }

    public AttackMetadata getAttack() { return attack; }
    public MitigationType getType() { return type; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public DamageMitigationEvent call() { MythicLibEventHub.events().publish(this); return this; }
    public static AutoCloseable subscribe(Consumer<? super DamageMitigationEvent> listener) {
        return MythicLibEventHub.events().subscribe(DamageMitigationEvent.class, listener);
    }
}
