package vn.svframe.svframelib.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerVersionTest {
    @Test
    void reportsTheActualSingleTargetPlatform() {
        ServerVersion version = ServerVersion.get();
        assertTrue(version.isFabric());
        assertEquals("fabric-1.21.1", version.getLoaderPlatform());
        assertArrayEquals(new int[]{1, 21, 1}, version.getGameVersion());
        assertEquals("1.21.1-fabric", version.toString());
        assertDoesNotThrow(version::validateMappings);
    }

    @Test
    void preservesVersionComparisonSemantics() {
        ServerVersion version = ServerVersion.get();
        assertTrue(version.isAbove(1, 21, 0));
        assertFalse(version.isAbove(1, 21, 1));
        assertTrue(version.isUnder(1, 21, 2));
        assertTrue(version.isBelowOrEqual(1, 21, 1));
        assertThrows(IllegalArgumentException.class, () -> version.isAbove());
        assertThrows(IllegalArgumentException.class, () -> version.isAbove(1, 21, 1, 1));
    }
}
