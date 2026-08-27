package vn.svframe.svframelib.fabric.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeStatEngineTest {
    @Test
    void appliesFlatAdditiveAndRelativeModifiersInOrder() {
        NativeStatEngine engine = new NativeStatEngine();
        UUID player = UUID.randomUUID();
        engine.setBase(player, "POWER", 100d);
        engine.register(player, "POWER", "flat", 20d, NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER);
        engine.register(player, "POWER", "add", 50d, NativeStatEngine.ModifierType.ADDITIVE_MULTIPLIER,
                NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER);
        engine.register(player, "POWER", "relative", 10d, NativeStatEngine.ModifierType.RELATIVE,
                NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER);

        assertEquals(198d, engine.stat(player, "POWER"), 1.0e-9);
    }

    @Test
    void filtersHandSpecificModifiers() {
        NativeStatEngine engine = new NativeStatEngine();
        UUID player = UUID.randomUUID();
        engine.setBase(player, "ATTACK_DAMAGE", 10d);
        engine.register(player, "ATTACK_DAMAGE", "main-hand", 5d, NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.MAIN_HAND, NativeStatEngine.ModifierSource.MELEE_WEAPON);

        assertEquals(15d, engine.finalValue(player, "ATTACK_DAMAGE", NativeStatEngine.EquipmentSlot.MAIN_HAND), 1.0e-9);
        assertEquals(10d, engine.finalValue(player, "ATTACK_DAMAGE", NativeStatEngine.EquipmentSlot.OFF_HAND), 1.0e-9);
    }

    @Test
    void expiresTemporaryModifiersOnTheirExactTick() {
        NativeStatEngine engine = new NativeStatEngine();
        UUID player = UUID.randomUUID();
        engine.setBase(player, "DEFENSE", 10d);
        engine.registerTemporary(player, "DEFENSE", "temporary", 5d, NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER, 5L, 10L);

        assertEquals(15d, engine.stat(player, "DEFENSE"), 1.0e-9);
        assertEquals(0, engine.tick(14L));
        assertEquals(15d, engine.stat(player, "DEFENSE"), 1.0e-9);
        assertEquals(1, engine.tick(15L));
        assertEquals(10d, engine.stat(player, "DEFENSE"), 1.0e-9);
    }
}
