package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.api.codec.Effect;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;
import java.util.Optional;

/** Optional native Mega Showdown effect bridge. This class is only executed when Mega Showdown is loaded. */
public final class MegaShowdownEffects {
    private static final String FUSION_EFFECT = "mega_showdown:mega_evolution";
    private MegaShowdownEffects() { }

    public static void playFusionStart(Pokemon pokemon, PokemonEntity entity) {
        if (!FabricLoader.getInstance().isModLoaded("mega_showdown")) return;
        Effect.getEffect(FUSION_EFFECT).applyEffects(pokemon, List.of(), Optional.empty(), entity);
    }
}
