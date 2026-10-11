package vn.svframe.svframemobs.mixin;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;
@Mixin(targets="net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public interface EntityTrackingAccessor {
    @Accessor("entry") EntityTrackerEntry svframe$entry();
    @Accessor("listeners") Set<PlayerAssociatedNetworkHandler> svframe$listeners();
}
