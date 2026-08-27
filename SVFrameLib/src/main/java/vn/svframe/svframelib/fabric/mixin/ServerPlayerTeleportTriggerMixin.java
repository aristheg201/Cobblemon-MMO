package vn.svframe.svframelib.fabric.mixin;

import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframelib.fabric.SVFrameLibPassiveMod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Fires TELEPORT from the actual successful ServerPlayerEntity teleport operation. */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerTeleportTriggerMixin {
    @Unique private String svframelib$fromWorld;
    @Unique private double svframelib$fromX;
    @Unique private double svframelib$fromY;
    @Unique private double svframelib$fromZ;

    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z", at = @At("HEAD"))
    private void svframelib$captureTeleportOrigin(ServerWorld world,
                                                  double x,
                                                  double y,
                                                  double z,
                                                  Set<PositionFlag> flags,
                                                  float yaw,
                                                  float pitch,
                                                  CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = self();
        svframelib$fromWorld = player.getServerWorld().getRegistryKey().getValue().toString();
        svframelib$fromX = player.getX();
        svframelib$fromY = player.getY();
        svframelib$fromZ = player.getZ();
    }

    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z", at = @At("RETURN"))
    private void svframelib$fireTeleport(ServerWorld world,
                                         double x,
                                         double y,
                                         double z,
                                         Set<PositionFlag> flags,
                                         float yaw,
                                         float pitch,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        ServerPlayerEntity player = self();
        String toWorld = player.getServerWorld().getRegistryKey().getValue().toString();
        double toX = player.getX();
        double toY = player.getY();
        double toZ = player.getZ();
        if (svframelib$fromWorld != null
                && svframelib$fromWorld.equals(toWorld)
                && svframelib$fromX == toX && svframelib$fromY == toY && svframelib$fromZ == toZ) return;

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("from-world", svframelib$fromWorld == null ? toWorld : svframelib$fromWorld);
        context.put("to-world", toWorld);
        context.put("from-x", svframelib$fromX);
        context.put("from-y", svframelib$fromY);
        context.put("from-z", svframelib$fromZ);
        context.put("to-x", toX);
        context.put("to-y", toY);
        context.put("to-z", toZ);
        context.put("yaw", player.getYaw());
        context.put("pitch", player.getPitch());
        SVFrameLibPassiveMod.fire(player.getUuid(), "TELEPORT", player.getUuid(), context);
    }

    @Unique
    private ServerPlayerEntity self() {
        return (ServerPlayerEntity) (Object) this;
    }
}
