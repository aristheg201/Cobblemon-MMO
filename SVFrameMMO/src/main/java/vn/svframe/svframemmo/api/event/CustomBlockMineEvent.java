package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Cancellable native custom-mining event. Drops are mutable before the block is committed. */
public final class CustomBlockMineEvent {
    @FunctionalInterface public interface Listener { void onCustomBlockMine(CustomBlockMineEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> { for (Listener listener : listeners) listener.onCustomBlockMine(event); });

    private final PlayerData data;
    private final ServerWorld world;
    private final BlockPos blockPos;
    private final BlockState block;
    private final BlockInfo blockInfo;
    private List<ItemStack> drops;
    private boolean cancelled;

    public CustomBlockMineEvent(PlayerData data, ServerWorld world, BlockPos blockPos, BlockState block,
                                BlockInfo blockInfo, List<ItemStack> drops, boolean cancelled) {
        this.data = Objects.requireNonNull(data, "data");
        this.world = Objects.requireNonNull(world, "world");
        this.blockPos = Objects.requireNonNull(blockPos, "blockPos").toImmutable();
        this.block = Objects.requireNonNull(block, "block");
        this.blockInfo = Objects.requireNonNull(blockInfo, "blockInfo");
        setDrops(drops);
        this.cancelled = cancelled;
    }

    public PlayerData getData() { return data; }
    public ServerWorld getWorld() { return world; }
    public BlockPos getBlockPos() { return blockPos; }
    public BlockState getBlock() { return block; }
    public BlockInfo getBlockInfo() { return blockInfo; }
    public List<ItemStack> getDrops() { return drops; }
    public void setDrops(List<ItemStack> drops) {
        Objects.requireNonNull(drops, "drops");
        this.drops = new ArrayList<>(drops);
    }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public CustomBlockMineEvent call() { EVENT.invoker().onCustomBlockMine(this); return this; }

    /** Stable public view of the configured mining definition. */
    public record BlockInfo(String id, Identifier blockId, boolean vanillaDrops, long regenerationTicks) {
        public BlockInfo {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(blockId, "blockId");
        }
    }
}
