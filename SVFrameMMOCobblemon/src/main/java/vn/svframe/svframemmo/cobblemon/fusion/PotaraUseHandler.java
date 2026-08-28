package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

/** Potara activation: hold the configured vanilla item and right-click the exact deployed Pokemon. */
public final class PotaraUseHandler {
    private PotaraUseHandler() { }

    public static void register(FusionService fusions) {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer) || !(entity instanceof PokemonEntity pokemonEntity))
                return ActionResult.PASS;
            FusionTier tier = SVFrameMMOCobblemon.potara().resolve(serverPlayer.getStackInHand(hand));
            if (tier == null) return ActionResult.PASS;
            FusionService.StartResult result = fusions.startPotara(serverPlayer, pokemonEntity, tier);
            if (!result.success()) {
                serverPlayer.sendMessage(Text.literal(result.rejection()), true);
                return ActionResult.FAIL;
            }
            serverPlayer.sendMessage(Text.literal("Fusion started with " + pokemonEntity.getPokemon().getDisplayName(false).getString() + "."), true);
            return ActionResult.SUCCESS;
        });
    }
}
