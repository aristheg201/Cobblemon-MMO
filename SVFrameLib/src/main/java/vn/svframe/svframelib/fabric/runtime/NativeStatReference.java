package vn.svframe.svframelib.fabric.runtime;

import java.util.Objects;
import java.util.UUID;

/** Stable public handle across native engine generations (logout, restart, reload).
 * Never retain a removed engine instance inside a longer-lived player profile. */
public record NativeStatReference(NativeStatEngine engine, UUID playerId, String stat) {
    public NativeStatReference {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(stat);
    }

    public NativeStatEngine.StatInstance resolve() {
        return engine.instance(playerId, stat);
    }
}
