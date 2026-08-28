package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

/** Bridge to Mega Showdown's real Kyurem fusion effect definitions used only by Potara. */
public final class MegaShowdownEffects {
    /** kyurem_black/white use apply_after=4.0s and cry apply_delay=4.4s; morph after the complete sequence. */
    public static final long POTARA_FUSION_FORM_DELAY_TICKS = 88L;

    private MegaShowdownEffects() { }

    /**
     * Potara only. Fusion Dance intentionally has no fusion VFX.
     * The configured pool is validated to contain only Mega Showdown Kyurem + Zekrom/Reshiram fusion effects.
     */
    public static String playPotaraFusionStart(Pokemon pokemon, PokemonEntity entity) {
        if (pokemon == null || entity == null) throw new IllegalArgumentException("Pokemon and entity are required for Potara fusion VFX");
        java.util.List<String> effects = SVFrameMMOCobblemon.config().vfx.potaraFusionEffects;
        String effectId = effects.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(effects.size()));
        com.github.yajatkaul.mega_showdown.api.codec.Effect.getEffect(effectId)
                .applyEffects(pokemon, java.util.List.of(), java.util.Optional.empty(), entity);
        return effectId;
    }
}
