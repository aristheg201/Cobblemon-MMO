package vn.svframe.svframemobs.mixin;
import net.minecraft.server.world.ServerChunkLoadingManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ServerChunkLoadingManager.class)
public interface ChunkTrackingAccessor {
    @Accessor("entityTrackers") Int2ObjectMap<?> svframe$trackers();
}
