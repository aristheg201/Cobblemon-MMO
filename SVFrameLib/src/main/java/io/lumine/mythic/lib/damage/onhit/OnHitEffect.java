package io.lumine.mythic.lib.damage.onhit;

import java.util.Locale;
import java.util.Objects;

public class OnHitEffect {
    private final String id;
    public OnHitEffect(String id) { this.id = Objects.requireNonNull(id).trim().toUpperCase(Locale.ROOT); }
    public String getId() { return id; }
    public String name() { return id; }
    @Override public String toString() { return id; }
    @Override public boolean equals(Object o) { return o instanceof OnHitEffect e && id.equals(e.id); }
    @Override public int hashCode() { return id.hashCode(); }
}
