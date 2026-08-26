package vn.svframe.svframelib.player.resource;
import net.minecraft.server.network.ServerPlayerEntity;
@FunctionalInterface public interface HealthUpdateEventSupplier<T extends AbstractHealthUpdateEvent>{T onHealthUpdate(ServerPlayerEntity player,double oldAmount,double newAmount,ResourceUpdateReason reason);}
