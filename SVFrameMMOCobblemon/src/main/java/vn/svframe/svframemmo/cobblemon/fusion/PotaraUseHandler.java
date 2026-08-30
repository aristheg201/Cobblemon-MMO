package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.integration.LuckPermsIntegration;
import vn.svframe.svframemmo.cobblemon.integration.MegaShowdownEffects;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Potara activation/unfusion: configured vanilla item + CMD, interacting with the exact party Pokemon. */
public final class PotaraUseHandler {
    private static final Set<UUID> PENDING_FUSIONS = ConcurrentHashMap.newKeySet();

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

            UUID playerId = serverPlayer.getUuid();
            if (PENDING_FUSIONS.contains(playerId)) {
                serverPlayer.sendMessage(Text.literal("Potara fusion sequence is already in progress."), true);
                return ActionResult.FAIL;
            }

            long cooldown = fusions.cooldowns().potaraRemainingMillis(playerId);
            if (cooldown > 0L) {
                serverPlayer.sendMessage(Text.literal("Potara action cooldown: " + FusionCommands.formatSeconds(cooldown) + "."), true);
                return ActionResult.FAIL;
            }

            FusionSession active = fusions.session(playerId);
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
                fusions.cooldowns().markPotara(playerId, fusions.potaraCooldownSeconds());
                serverPlayer.sendMessage(Text.literal("Potara fusion ended."), true);
                return ActionResult.SUCCESS;
            }

            DeployedPartyPokemonResolver.Resolution selected = new DeployedPartyPokemonResolver().resolve(serverPlayer, pokemonEntity);
            if (!selected.accepted()) {
                serverPlayer.sendMessage(Text.literal(selected.rejection()), true);
                return ActionResult.FAIL;
            }
            if (!new FusionEligibility().allows(tier, selected.pokemon())) {
                serverPlayer.sendMessage(Text.literal("This Pokemon is not eligible for that fusion rank."), true);
                return ActionResult.FAIL;
            }
            if (fusions.isPokemonLocked(selected.pokemon().getUuid())) {
                serverPlayer.sendMessage(Text.literal("That Pokemon is already locked by a fusion."), true);
                return ActionResult.FAIL;
            }

            MegaShowdownEffects.PotaraPresentation presentation;
            try {
                presentation = MegaShowdownEffects.playPotaraFusionStart(selected.pokemon(), pokemonEntity);
            } catch (RuntimeException error) {
                SVFrameMMOCobblemon.LOG.warn("Could not play Potara Mega Showdown effect for {}", serverPlayer.getName().getString(), error);
                serverPlayer.sendMessage(Text.literal("Could not start the Potara fusion effect."), true);
                return ActionResult.FAIL;
            }

            if (!PENDING_FUSIONS.add(playerId)) return ActionResult.FAIL;
            serverPlayer.sendMessage(Text.literal("Potara fusion sequence started."), true);

            SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + presentation.delayTicks(), () -> {
                try {
                    if (!serverPlayer.networkHandler.isConnectionOpen()) return;
                    if (!pokemonEntity.isAlive() || pokemonEntity.isRemoved()) {
                        serverPlayer.sendMessage(Text.literal("Potara fusion cancelled: the Pokemon is no longer deployed."), true);
                        return;
                    }

                    // The VFX window is several seconds long. Re-run all ownership/deployment/rank checks before
                    // committing the session so movement/recall/other gameplay cannot leave a half-started fusion.
                    DeployedPartyPokemonResolver.Resolution current =
                            new DeployedPartyPokemonResolver().resolve(serverPlayer, pokemonEntity);
                    if (!current.accepted()) {
                        serverPlayer.sendMessage(Text.literal("Potara fusion cancelled: " + current.rejection()), true);
                        return;
                    }
                    if (!new FusionEligibility().allows(tier, current.pokemon())) {
                        serverPlayer.sendMessage(Text.literal("Potara fusion cancelled: Pokemon no longer satisfies this rank."), true);
                        return;
                    }
                    if (fusions.session(playerId) != null || fusions.isPokemonLocked(current.pokemon().getUuid())) {
                        serverPlayer.sendMessage(Text.literal("Potara fusion cancelled: fusion state changed during the sequence."), true);
                        return;
                    }

                    FusionService.StartResult result = fusions.startPotara(serverPlayer, pokemonEntity, tier);
                    if (!result.success()) {
                        serverPlayer.sendMessage(Text.literal(result.rejection()), true);
                        return;
                    }
                    fusions.cooldowns().markPotara(playerId, fusions.potaraCooldownSeconds());
                    serverPlayer.sendMessage(Text.literal(
                            "Potara fusion started with " + result.session().pokemonName() + "."), true);
                } catch (RuntimeException error) {
                    SVFrameMMOCobblemon.LOG.warn("Potara fusion commit failed after effect {}", presentation.effectId(), error);
                    if (serverPlayer.networkHandler.isConnectionOpen())
                        serverPlayer.sendMessage(Text.literal("Potara fusion failed after the visual sequence."), true);
                } finally {
                    PENDING_FUSIONS.remove(playerId);
                }
            });
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
