package vn.svframe.svframelib.fabric.runtime;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NativeStatReferenceTest {
    @Test void reconnectMutationsReachTheLiveGenerationAndPublishOnce() {
        var engine = new NativeStatEngine();
        var id = UUID.randomUUID();
        var handle = new NativeStatReference(engine, id, "MAX_HEALTH");
        var published = new AtomicReference<Double>();
        var handler = new NativeStatHandler("MAX_HEALTH", 20d, null, null, new java.text.DecimalFormat("0.#"));
        handler.addUpdateListener(instance -> published.set(instance.total()));
        engine.registerHandler(handler);
        UUID modifierId = UUID.nameUUIDFromBytes("persistent-class-health".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var modifier = new NativeStatEngine.Modifier(modifierId, "class", 28d,
                NativeStatEngine.ModifierType.FLAT, NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER);
        for (int attempt=0; attempt<6; attempt++) {
            engine.onSessionOpen(id);
            handle.resolve().register(modifier);
            handle.resolve().register(modifier);
            assertEquals(48d, engine.stat(id, "MAX_HEALTH"));
            assertEquals(48d, published.get());
            assertEquals(1, handle.resolve().modifiers().size());
            engine.onSessionClose(id);
            engine.clear(id);
        }
    }

    @Test void clearedTemporaryBuffCannotReturnThroughCachedProfileHandle() {
        var engine = new NativeStatEngine();
        var id = UUID.randomUUID();
        var handle = new NativeStatReference(engine, id, "HEALTH");
        engine.onSessionOpen(id);
        handle.resolve().setBase(20);
        engine.registerTemporary(id, "HEALTH", "buff", 10d, NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER, 20L, 100L);
        assertEquals(30d, handle.resolve().total());
        engine.clear();
        engine.onSessionOpen(id);
        handle.resolve().setBase(20);
        assertEquals(20d, handle.resolve().total());
        assertTrue(handle.resolve().modifiers().isEmpty());
    }
}
