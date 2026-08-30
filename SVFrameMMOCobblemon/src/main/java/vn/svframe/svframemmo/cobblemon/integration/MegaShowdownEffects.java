package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.api.codec.Effect;
import com.github.yajatkaul.mega_showdown.api.codec.particles.SnowStormParticle;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Potara presentation sourced directly from Mega Showdown's real Kyurem fusion effect definitions. */
public final class MegaShowdownEffects {
    private static final double COMPLETION_MARGIN_SECONDS = 0.15d;

    private MegaShowdownEffects() { }

    /**
     * Starts the original Mega Showdown Kyurem fusion effect without rewriting its delays.
     *
     * <p>The deployed Pokemon entity must remain alive until {@link PotaraPresentation#delayTicks()} has elapsed,
     * because Mega Showdown's Snowstorm emitters are anchored to that entity. Recalling the Pokemon immediately
     * destroys the emitter and leaves only the sound.</p>
     */
    public static PotaraPresentation playPotaraFusionStart(Pokemon pokemon, PokemonEntity entity) {
        if (pokemon == null || entity == null)
            throw new IllegalArgumentException("Pokemon and entity are required for Potara fusion VFX");

        List<String> effects = SVFrameMMOCobblemon.config().vfx.potaraFusionEffects;
        if (effects == null || effects.isEmpty())
            throw new IllegalStateException("No Potara Mega Showdown effects are configured");

        String effectId = effects.get(ThreadLocalRandom.current().nextInt(effects.size()));
        Effect effect = requireRenderableEffect(effectId);
        SnowStormParticle snowstorm = effect.snowStorm().orElseThrow();

        // Run Mega Showdown's exact codec. It owns the Kyurem buildup/godrays/cyclone/burst particle timeline,
        // sound and animation timing. PotaraUseHandler delays the recall/morph until this timeline completes.
        effect.applyEffects(pokemon, List.of(), Optional.empty(), entity);

        double seconds = snowstorm.apply_after().map(Float::doubleValue).orElse(0d);
        seconds = Math.max(seconds, snowstorm.animations().map(animation -> (double) animation.applyDelay()).orElse(0d));
        long delayTicks = Math.max(1L, (long) Math.ceil((seconds + COMPLETION_MARGIN_SECONDS) * 20d));
        return new PotaraPresentation(effectId, delayTicks);
    }

    /** Production-boot validation: configured Potara effects must resolve to a real Snowstorm apply particle. */
    public static void validateConfiguredEffects() {
        List<String> effects = SVFrameMMOCobblemon.config().vfx.potaraFusionEffects;
        if (effects == null || effects.isEmpty())
            throw new IllegalStateException("No Potara Mega Showdown effects are configured");
        for (String effectId : effects) requireRenderableEffect(effectId);
    }

    private static Effect requireRenderableEffect(String effectId) {
        if (effectId == null || effectId.isBlank())
            throw new IllegalArgumentException("Potara Mega Showdown effect ID must not be blank");
        Effect effect = Effect.getEffect(effectId);
        SnowStormParticle snowstorm = effect.snowStorm()
                .orElseThrow(() -> new IllegalStateException("Mega Showdown effect has no Snowstorm section: " + effectId));
        if (snowstorm.particle_apply().isEmpty() || snowstorm.particle_apply().orElse("").isBlank())
            throw new IllegalStateException("Mega Showdown effect has no apply particle: " + effectId);
        return effect;
    }

    public record PotaraPresentation(String effectId, long delayTicks) { }
}
