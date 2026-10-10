package vn.svframe.svframelib.fabric;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HeroEntranceSettingsTest {
    @Test void boundsWorkAndRejectsNonFiniteMotion() {
        var settings = HeroEntranceSettings.from(Map.of("range", Double.POSITIVE_INFINITY, "radius", 5000,
                "height", Double.NaN, "aim_ticks", -10, "descent_ticks", 0, "damage", -1));
        assertEquals(32, settings.range());
        assertEquals(12, settings.radius());
        assertEquals(12, settings.height());
        assertEquals(20, settings.aimTicks());
        assertEquals(6, settings.descentTicks());
        assertEquals(0, settings.damage());
    }
}
