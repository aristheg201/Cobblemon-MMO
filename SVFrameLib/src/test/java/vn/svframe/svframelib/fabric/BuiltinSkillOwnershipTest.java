package vn.svframe.svframelib.fabric;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinSkillOwnershipTest {
    @Test
    void keepsNativeAndExternalSkillOwnershipDisjoint() {
        assertEquals(90, BuiltinSkillOwnership.nativeIds().size());
        assertEquals(3, BuiltinSkillOwnership.externalProviderIds().size());
        HashSet<String> overlap = new HashSet<>(BuiltinSkillOwnership.nativeIds());
        overlap.retainAll(BuiltinSkillOwnership.externalProviderIds());
        assertTrue(overlap.isEmpty());
        assertTrue(BuiltinSkillOwnership.isNative("fireball"));
        assertTrue(BuiltinSkillOwnership.isExternalProvider("ambers"));
        assertFalse(BuiltinSkillOwnership.isNative("ambers"));
    }
}
