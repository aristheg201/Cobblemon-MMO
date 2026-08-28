package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

/** Optional bridge to Mega Showdown's real Kyurem fusion effect definitions. */
public final class MegaShowdownEffects {
    private MegaShowdownEffects() { }

    /**
     * Potara only. Fusion Dance intentionally has no fusion VFX.
     * The default pool contains both Mega Showdown Kyurem + Zekrom (black) and Kyurem + Reshiram (white) sequences.
     */
    public static void playPotaraFusionStart(Pokemon pokemon, PokemonEntity entity) {
        if (pokemon == null || entity == null || !FabricLoader.getInstance().isModLoaded("mega_showdown")) return;
        java.util.List<String> effects = SVFrameMMOCobblemon.config().vfx.potaraFusionEffects;
        String effect = effects.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(effects.size()));
        Loaded.play(pokemon, entity, effect);
    }

    /** Keeps Mega Showdown classes out of the outer class verifier when the optional mod is absent. */
    private static final class Loaded {
        private static void play(Pokemon pokemon, PokemonEntity entity, String effectId) {
            com.github.yajatkaul.mega_showdown.api.codec.Effect.getEffect(effectId)
                    .applyEffects(pokemon, java.util.List.of(), java.util.Optional.empty(), entity);
        }
    }
}
