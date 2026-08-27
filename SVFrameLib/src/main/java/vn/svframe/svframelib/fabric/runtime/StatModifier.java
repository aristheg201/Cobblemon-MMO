package vn.svframe.svframelib.fabric.runtime;

import java.util.Objects;

public record StatModifier(String key, String source, double value, ModifierOperation operation, long expiresAtMillis) {
    public StatModifier {
        key = Objects.requireNonNull(key, "key");
        source = Objects.requireNonNull(source, "source");
        operation = Objects.requireNonNull(operation, "operation");
    }

    public static StatModifier permanent(String key, String source, double value, ModifierOperation operation) {
        return new StatModifier(key, source, value, operation, Long.MAX_VALUE);
    }

    public boolean expired(long nowMillis) {
        return expiresAtMillis != Long.MAX_VALUE && nowMillis >= expiresAtMillis;
    }
}
