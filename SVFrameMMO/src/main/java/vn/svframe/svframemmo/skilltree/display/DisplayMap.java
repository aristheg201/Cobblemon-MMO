package vn.svframe.svframemmo.skilltree.display;

import vn.svframe.svframelib.gui.util.IconOptions;
import net.minecraft.item.Items;
import vn.svframe.svframemmo.skilltree.NodeState;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Config-driven node/path texture map, matching MMOCore's tree -> node -> GUI fallback chain. */
public final class DisplayMap {
    public static final IconOptions DEFAULT_ICON = new IconOptions(Items.BARRIER);
    public static final DisplayMap EMPTY = new DisplayMap(Map.of());
    private final Map<Object, IconOptions> icons = new LinkedHashMap<>();

    private DisplayMap(Map<String, Object> config) {
        Map<String, Object> paths = map(config.get("paths"));
        for (PathState state : PathState.values()) {
            Map<String, Object> byShape = map(value(paths, key(state)));
            for (PathShape shape : PathShape.values()) {
                Object raw = value(byShape, key(shape));
                if (raw != null) icons.put(new PathDisplayInfo(shape, state), IconOptions.from(raw));
            }
        }

        Map<String, Object> nodes = map(config.get("nodes"));
        if (nodes.isEmpty()) nodes = config;
        for (NodeState state : NodeState.values()) {
            Object rawState = value(nodes, key(state));
            if (rawState == null) continue;
            Map<String, Object> byShape = map(rawState);
            boolean shapeDependent = false;
            for (NodeShape shape : NodeShape.values()) if (value(byShape, key(shape)) != null) { shapeDependent = true; break; }
            if (shapeDependent) {
                for (NodeShape shape : NodeShape.values()) {
                    Object raw = value(byShape, key(shape));
                    if (raw != null) icons.put(new NodeDisplayInfo(shape, state), IconOptions.from(raw));
                }
            } else {
                IconOptions icon = IconOptions.from(rawState);
                for (NodeShape shape : NodeShape.values()) icons.put(new NodeDisplayInfo(shape, state), icon);
            }
        }
    }

    public IconOptions get(Object info) { return icons.get(info); }

    public static IconOptions getIcon(Object info, DisplayMap... maps) {
        if (maps == null) return null;
        for (DisplayMap map : maps) if (map != null) {
            IconOptions icon = map.icons.get(info);
            if (icon != null) return icon;
        }
        return null;
    }

    public static DisplayMap from(Object raw) {
        Map<String, Object> config = map(raw);
        return config.isEmpty() ? EMPTY : new DisplayMap(config);
    }

    private static String key(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    private static Object value(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet())
            if (normalize(entry.getKey()).equals(normalize(key))) return entry.getValue();
        return null;
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replace('_', '-'); }
    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        source.forEach((key, value) -> out.put(String.valueOf(key), value));
        return out;
    }
}
