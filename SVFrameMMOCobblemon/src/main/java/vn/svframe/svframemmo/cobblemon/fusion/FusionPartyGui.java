package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.callback.PartySelectCallbacks;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.util.ArrayList;
import java.util.List;

/** Uses Cobblemon's native party selector, including the real Pokemon party models, for /fusion. */
public final class FusionPartyGui {
    private final FusionService fusions;

    public FusionPartyGui(FusionService fusions) {
        this.fusions = fusions;
    }

    public void open(ServerPlayerEntity player) {
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        List<Pokemon> pokemon = new ArrayList<>(6);
        for (int i = 0; i < Math.min(6, party.size()); i++) {
            Pokemon member = party.get(i);
            if (member != null) pokemon.add(member);
        }

        if (pokemon.isEmpty()) {
            player.sendMessage(Text.literal("Your party is empty."), true);
            return;
        }

        PartySelectCallbacks.INSTANCE.createFromPokemon(
                player,
                Text.literal("Fusion Dance - Select Pokemon"),
                pokemon,
                member -> member.getEntity() == null || !member.getEntity().isBattling(),
                ignored -> Unit.INSTANCE,
                selected -> {
                    FusionService.StartResult result = fusions.startDance(player, selected);
                    if (!result.success()) {
                        player.sendMessage(Text.literal(result.rejection()), true);
                    } else {
                        int duration = SVFrameMMOCobblemon.config().fusion.danceDurationSeconds;
                        player.sendMessage(Text.literal("Fusion Dance started with " + result.session().pokemonName()
                                + " for " + formatDuration(duration) + "."), true);
                    }
                    return Unit.INSTANCE;
                }
        );
    }

    private static String formatDuration(int seconds) {
        if (seconds % 60 == 0) return (seconds / 60) + " minute" + (seconds == 60 ? "" : "s");
        return seconds + " seconds";
    }
}
