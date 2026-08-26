package io.lumine.mythic.lib.util;

import java.util.Objects;
import java.util.function.Supplier;

/** Lazy value semantics matching MythicLib 1.7.1. */
public class Lazy<T> implements Supplier<T> {
    private final boolean persistent;
    private Supplier<T> expression;
    private boolean evaluated;
    private T value;

    public Lazy(Supplier<T> expression) {
        this(Objects.requireNonNull(expression, "expression"), true);
    }

    private Lazy(Supplier<T> expression, boolean persistent) {
        this.expression = expression;
        this.persistent = persistent;
    }

    private Lazy(T value) {
        this.expression = null;
        this.persistent = false;
        this.evaluated = true;
        this.value = value;
    }

    public void flush() {
        if (!persistent) throw new IllegalStateException("Non persistent lazy value");
        value = null;
        evaluated = false;
    }

    public static <T> Lazy<T> persistent(Supplier<T> expression) { return new Lazy<>(Objects.requireNonNull(expression), true); }
    public static <T> Lazy<T> of(Supplier<T> expression) { return new Lazy<>(Objects.requireNonNull(expression), true); }
    public static <T> Lazy<T> of(T value) { return new Lazy<>(value); }

    @Override
    public T get() {
        if (evaluated) return value;
        if (expression == null) throw new IllegalStateException("Non persistent lazy value");
        value = expression.get();
        evaluated = true;
        if (!persistent) expression = null;
        return value;
    }

    public boolean isInitialized() { return evaluated; }
}
