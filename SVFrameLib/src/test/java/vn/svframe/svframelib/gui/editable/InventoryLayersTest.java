package vn.svframe.svframelib.gui.editable;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InventoryLayersTest {
    record Layer(String name, int slot, boolean visible) { }
    private static Layer resolve(List<Layer> layers, int slot) {
        return InventoryLayers.topmost(layers, layer -> layer.slot() == slot && layer.visible());
    }
    @Test void upgradeReceivesClickAboveLevelPreview() {
        var preview = new Layer("level", 15, true);
        var upgrade = new Layer("upgrade", 15, true);
        assertSame(upgrade, resolve(List.of(preview, upgrade), 15));
    }
    @Test void hiddenOverlayDoesNotStealClick() {
        var preview = new Layer("level", 15, true);
        assertSame(preview, resolve(List.of(preview, new Layer("hidden", 15, false)), 15));
    }
    @Test void otherSlotsDoNotStealClick() {
        var skill = new Layer("skill", 10, true);
        assertSame(skill, resolve(List.of(skill, new Layer("upgrade", 15, true)), 10));
    }
    @Test void emptyAndUnmatchedSlotsHaveNoHandler() {
        assertNull(resolve(List.of(), 15));
        assertNull(resolve(List.of(new Layer("upgrade", 15, true)), 16));
    }
}
