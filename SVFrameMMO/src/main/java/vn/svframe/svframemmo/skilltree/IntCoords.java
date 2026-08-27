package vn.svframe.svframemmo.skilltree;

import java.util.Map;
import java.util.Objects;

public record IntCoords(int x, int y) {
    public IntCoords offset(int dx, int dy) { return new IntCoords(x + dx, y + dy); }
    public IntCoords add(IntCoords other) { return new IntCoords(x + other.x, y + other.y); }
    public static IntCoords from(Object raw) {
        Objects.requireNonNull(raw, "coordinates");
        if (raw instanceof Map<?, ?> map) return new IntCoords(integer(map.get("x")), integer(map.get("y")));
        String[] split = String.valueOf(raw).trim().split("[:., ]+");
        if (split.length < 2) throw new IllegalArgumentException("Coordinates require x and y: " + raw);
        return new IntCoords(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }
    private static int integer(Object raw) { return raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw)); }
}
