package io.lumine.mythic.lib.comp.interaction;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
@FunctionalInterface
public interface TargetRestriction {
    boolean canTarget(ServerPlayerEntity source, LivingEntity target, InteractionType type);
}
