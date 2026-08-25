package io.lumine.mythic.lib.util;

import java.util.Objects;
import java.util.function.Supplier;

public final class Lazy<T> implements Supplier<T> {
    private Supplier<T> supplier;
    private volatile T value;
    private volatile boolean initialized;
    public Lazy(Supplier<T> supplier){this.supplier=Objects.requireNonNull(supplier,"supplier");}
    @Override public T get(){if(!initialized)synchronized(this){if(!initialized){value=supplier.get();supplier=null;initialized=true;}}return value;}
    public boolean isInitialized(){return initialized;}
}
