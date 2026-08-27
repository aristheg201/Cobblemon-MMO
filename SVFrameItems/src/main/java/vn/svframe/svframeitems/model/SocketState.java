package vn.svframe.svframeitems.model;

import java.util.*;

public record SocketState(String color, EmbeddedGem gem) {
    public SocketState { color = EmbeddedGem.normalizeColor(color); }
    public boolean empty() { return gem == null; }
    public boolean accepts(String gemColor) {
        String normalized = EmbeddedGem.normalizeColor(gemColor);
        return empty() && (color.equals("any") || normalized.equals("any") || color.equals(normalized));
    }
    public SocketState insert(EmbeddedGem value) {
        Objects.requireNonNull(value, "value");
        if (!accepts(value.color())) throw new IllegalArgumentException("Gem color " + value.color() + " does not match socket " + color);
        return new SocketState(color, value);
    }
    public SocketState clear() { return new SocketState(color, null); }
}
