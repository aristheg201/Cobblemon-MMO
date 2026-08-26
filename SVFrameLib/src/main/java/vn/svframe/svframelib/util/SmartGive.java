package vn.svframe.svframelib.util;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Native Fabric implementation of MythicLib 1.7.1 SmartGive. */
public class SmartGive {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final Vec3d location;

    public SmartGive(ServerPlayerEntity player) {
        this.player = Objects.requireNonNull(player, "Player cannot be null");
        this.world = (ServerWorld) player.getWorld();
        this.location = player.getPos();
    }

    public void give(ItemStack... items) {
        if (items == null || items.length == 0) return;
        give(Arrays.asList(items));
    }

    public void give(List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;
        for (ItemStack input : items) {
            if (input == null || input.isEmpty()) continue;
            ItemStack remaining = input.copy();
            player.getInventory().insertStack(remaining);
            if (remaining.isEmpty()) continue;

            ItemEntity dropped = new ItemEntity(world, location.x, location.y, location.z, remaining);
            dropped.setToDefaultPickupDelay();
            world.spawnEntity(dropped);
        }
    }
}
