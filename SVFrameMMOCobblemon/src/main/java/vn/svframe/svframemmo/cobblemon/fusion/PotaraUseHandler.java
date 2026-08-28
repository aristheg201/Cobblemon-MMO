package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.integration.LuckPermsIntegration;

/** Potara activation/unfusion: configured vanilla item + CMD, interacting with the exact party Pokemon. */
public final class PotaraUseHandler {
    private PotaraUseHandler() { }

    public static void register(FusionService fusions) {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer) || !(entity instanceof PokemonEntity pokemonEntity))
                return ActionResult.PASS;
            FusionTier tier = SVFrameMMOCobblemon.potara().resolve(serverPlayer.getStackInHand(hand));
            if (tier == null) return ActionResult.PASS;
            if (!LuckPermsIntegration.has(serverPlayer, LuckPermsIntegration.POTARA)
                    || !LuckPermsIntegration.has(serverPlayer, permission(tier))) {
                serverPlayer.sendMessage(Text.literal("You do not have permission to use this Potara."), true);
                return ActionResult.FAIL;
            }

            long cooldown = fusions.cooldowns().potaraRemainingMillis(serverPlayer.getUuid());
            if (cooldown > 0L) {
                serverPlayer.sendMessage(Text.literal("Potara action cooldown: " + FusionCommands.formatSeconds(cooldown) + "."), true);
                return ActionResult.FAIL;
            }

            FusionSession active = fusions.session(serverPlayer.getUuid());
            if (active != null) {
                if (active.dance()) {
                    serverPlayer.sendMessage(Text.literal("Fusion Dance cannot be manually unfused."), true);
                    return ActionResult.FAIL;
                }
                if (!active.pokemonUuid().equals(pokemonEntity.getPokemon().getUuid())) {
                    serverPlayer.sendMessage(Text.literal("Right-click the Pokemon you fused with to unfuse."), true);
                    return ActionResult.FAIL;
                }
                FusionService.EndResult ended = fusions.end(serverPlayer, true);
                if (!ended.success()) {
                    serverPlayer.sendMessage(Text.literal(ended.rejection()), true);
                    return ActionResult.FAIL;
                }
                fusions.cooldowns().markPotara(serverPlayer.getUuid(), fusions.potaraCooldownSeconds());
                serverPlayer.sendMessage(Text.literal("Potara fusion ended."), true);
                return ActionResult.SUCCESS;
            }

            FusionService.StartResult result = fusions.startPotara(serverPlayer, pokemonEntity, tier);
            if (!result.success()) {
                serverPlayer.sendMessage(Text.literal(result.rejection()), true);
                return ActionResult.FAIL;
            }
            fusions.cooldowns().markPotara(serverPlayer.getUuid(), fusions.potaraCooldownSeconds());
            serverPlayer.sendMessage(Text.literal("Potara fusion started with " + result.session().pokemonName() + "."), true);
            return ActionResult.SUCCESS;
        });
    }

    private static String permission(FusionTier tier) {
        return switch (tier) {
            case BASIC -> LuckPermsIntegration.POTARA_BASIC;
            case LEVEL_2 -> LuckPermsIntegration.POTARA_LEVEL2;
            case ADVANCEMENT -> LuckPermsIntegration.POTARA_ADVANCEMENT;
            case GOD -> LuckPermsIntegration.POTARA_GOD;
            case DANCE -> LuckPermsIntegration.DANCE;
        };
    }
}
