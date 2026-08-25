package io.lumine.mythic.lib.damage.mitigation;

import java.util.Locale;
import java.util.Objects;

public class MitigationType {
    private final String id;
    public MitigationType(String id) { this.id = Objects.requireNonNull(id).trim().toUpperCase(Locale.ROOT); }
    public String getId() { return id; }
    public String name() { return id; }
    @Override public String toString() { return id; }
    @Override public boolean equals(Object o) { return o instanceof MitigationType t && id.equals(t.id); }
    @Override public int hashCode() { return id.hashCode(); }
}
