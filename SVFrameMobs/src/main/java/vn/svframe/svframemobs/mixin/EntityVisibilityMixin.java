package vn.svframe.svframemobs.mixin;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemobs.mythic.NativeVisibility;
import java.util.function.Consumer;
@Mixin(EntityTrackerEntry.class)
public abstract class EntityVisibilityMixin {
    @Shadow @Final private Entity entity;
    @Inject(method="sendPackets",at=@At("HEAD"),cancellable=true)
    private void svframe$visibility(ServerPlayerEntity observer,Consumer<Packet<ClientPlayPacketListener>> sender,CallbackInfo ci) {
        if(NativeVisibility.hidden(entity,observer))ci.cancel();
    }
}
