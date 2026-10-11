package vn.svframe.svframelib.gui.editable;

import java.util.List;
import java.util.function.Predicate;

/** Resolve input against layers in reverse rendering order. */
public final class InventoryLayers {
    private InventoryLayers() { }

    public static <T> T topmost(List<T> layers, Predicate<T> matches) {
        for (int index = layers.size() - 1; index >= 0; index--) {
            T layer = layers.get(index);
            if (matches.test(layer)) return layer;
        }
        return null;
    }
}
