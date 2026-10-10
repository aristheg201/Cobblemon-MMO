package vn.svframe.svframelib.fabric.runtime;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.damage.DamageMetadata;
import vn.svframe.svframelib.element.Element;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RegisteredDamageMetadataBridgeTest {
    @Test
    void preservesRegisteredTypesElementAndVanillaScaling() {
        Element fire = new Element("FIRE", "Fire", "BLAZE_POWDER", "🔥", "&c", "svframelib:none", null);
        DamageMetadata registered = new DamageMetadata(20d, fire, List.of(
                vn.svframe.svframelib.damage.DamageType.SKILL,
                vn.svframe.svframelib.damage.DamageType.MAGIC));

        NativeDamageMetadata converted = RegisteredDamageMetadataBridge.convert(
                registered, 10d, List.of(DamageType.PHYSICAL));

        assertEquals(10d, converted.damage(), 1e-9);
        assertEquals(10d, converted.damage("FIRE"), 1e-9);
        assertTrue(converted.hasElement("fire"));
        assertTrue(converted.hasType(DamageType.SKILL));
        assertTrue(converted.hasType(DamageType.MAGIC));
        assertFalse(converted.hasType(DamageType.PHYSICAL));
    }

    @Test
    void preservesPacketProportionsAndCritMetadata() {
        Element water = new Element("WATER", "Water", "LILY_PAD", "🌊", "&3", "svframelib:none", null);
        DamageMetadata registered = new DamageMetadata(12d, water, List.of(vn.svframe.svframelib.damage.DamageType.SKILL));
        registered.add(8d, water, List.of(vn.svframe.svframelib.damage.DamageType.MAGIC));
        registered.registerSkillCriticalStrike();
        registered.registerElementalCriticalStrike(water);

        NativeDamageMetadata converted = RegisteredDamageMetadataBridge.convert(registered, 5d, List.of());

        assertEquals(5d, converted.damage(), 1e-9);
        assertEquals(5d, converted.damage("WATER"), 1e-9);
        assertEquals(3d, converted.damage(DamageType.SKILL), 1e-9);
        assertEquals(2d, converted.damage(DamageType.MAGIC), 1e-9);
        assertTrue(converted.isSkillCriticalStrike());
        assertTrue(converted.isElementalCriticalStrike("WATER"));
    }

    @Test
    void dynamicallyRegistersProviderElementsWithoutOverwritingConfiguredOnes() {
        NativeElementRegistry registry = new NativeElementRegistry();
        NativeElementRegistry.Element electric = registry.registerIfAbsent("electric", "Electric");

        assertEquals("ELECTRIC", electric.id());
        assertEquals("svframelib:none", electric.regularAttack());
        assertSame(electric, registry.registerIfAbsent("ELECTRIC", "Different Name"));
        assertEquals(1, registry.size());
    }
}
