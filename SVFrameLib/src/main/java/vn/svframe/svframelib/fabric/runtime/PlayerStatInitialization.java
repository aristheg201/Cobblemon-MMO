package vn.svframe.svframelib.fabric.runtime;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Equipment integrations run after persistent progression is loaded, before login packets. */
public final class PlayerStatInitialization {
    private static final CopyOnWriteArrayList<Consumer<ServerPlayerEntity>> equipment = new CopyOnWriteArrayList<>();
    private PlayerStatInitialization() {}
    public static void registerEquipment(Consumer<ServerPlayerEntity> initializer) {
        equipment.addIfAbsent(java.util.Objects.requireNonNull(initializer));
    }
    public static void restoreEquipment(ServerPlayerEntity player) {
        for (var initializer : equipment) initializer.accept(player);
    }
}
