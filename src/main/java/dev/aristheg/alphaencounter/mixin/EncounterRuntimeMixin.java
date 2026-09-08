package dev.aristheg.alphaencounter.mixin;

import dev.aristheg.alphaencounter.integration.RankScalingService;
import dev.aristheg.alphaencounter.runtime.EncounterRuntime;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(value = EncounterRuntime.class, remap = false)
public abstract class EncounterRuntimeMixin {
    @Unique
    private static final ThreadLocal<Double> ALPHA_ENCOUNTER$SPAWN_MULTIPLIER = ThreadLocal.withInitial(() -> 1.0);

    @Inject(method = "initialize", at = @At("TAIL"))
    private void alphaEncounter$loadRankScaling(CallbackInfo ci) {
        RankScalingService.instance().reload();
    }

    @Inject(method = "reloadConfig", at = @At("TAIL"))
    private void alphaEncounter$reloadRankScaling(CallbackInfo ci) {
        RankScalingService.instance().reload();
    }

    @Inject(method = "trySpawnNear", at = @At("HEAD"))
    private void alphaEncounter$resolveSpawnMultiplier(ServerPlayerEntity player, CallbackInfo ci) {
        ALPHA_ENCOUNTER$SPAWN_MULTIPLIER.set(RankScalingService.instance().spawnChanceMultiplier(player));
    }

    @Redirect(
        method = "trySpawnNear",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/ThreadLocalRandom;nextDouble()D",
            ordinal = 0
        )
    )
    private double alphaEncounter$scaleSpawnAttemptRoll(ThreadLocalRandom random) {
        double roll = random.nextDouble();
        double multiplier = ALPHA_ENCOUNTER$SPAWN_MULTIPLIER.get();
        if (!Double.isFinite(multiplier) || multiplier <= 0.0) return 2.0;
        return roll / multiplier;
    }

    @Inject(method = "trySpawnNear", at = @At("RETURN"))
    private void alphaEncounter$clearSpawnMultiplier(ServerPlayerEntity player, CallbackInfo ci) {
        ALPHA_ENCOUNTER$SPAWN_MULTIPLIER.remove();
    }
}
