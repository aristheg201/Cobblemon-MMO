package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.api.codec.Effect;
import com.github.yajatkaul.mega_showdown.api.codec.particles.AnimationData;
import com.github.yajatkaul.mega_showdown.api.codec.particles.SnowStormParticle;
import com.github.yajatkaul.mega_showdown.utils.PokemonBehaviourHelper;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Potara presentation sourced directly from Mega Showdown's real Kyurem fusion effect definitions. */
public final class MegaShowdownEffects {
    private MegaShowdownEffects() { }

    /**
     * Plays the configured Mega Showdown Potara effect immediately without using its delayed form-change timing.
     * Particle IDs, Minecraft particles and sounds remain owned by Mega Showdown. Animation cues are dispatched
     * synchronously before the party Pokemon is recalled so the original cry/animation is not lost with the entity.
     */
    public static String playPotaraFusionStart(Pokemon pokemon, PokemonEntity entity) {
        if (pokemon == null || entity == null) throw new IllegalArgumentException("Pokemon and entity are required for Potara fusion VFX");
        List<String> effects = SVFrameMMOCobblemon.config().vfx.potaraFusionEffects;
        if (effects == null || effects.isEmpty()) throw new IllegalStateException("No Potara Mega Showdown effects are configured");
        String effectId = effects.get(ThreadLocalRandom.current().nextInt(effects.size()));

        Effect original = Effect.getEffect(effectId);
        Effect immediate = new Effect(
                original.minecraft(),
                original.snowStorm().map(MegaShowdownEffects::immediateSnowstorm),
                Optional.empty(),
                original.battle_pause_revert()
        );

        // Keep Mega Showdown's own effect execution for particle and sound codecs, but do not wait 4/4.4 seconds.
        immediate.applyEffects(pokemon, List.of(), Optional.empty(), entity);

        // AnimationData.after(0) still schedules against the entity. Potara recalls the entity immediately afterwards,
        // so dispatch the exact Mega Showdown apply animation/expression set synchronously instead.
        original.snowStorm().flatMap(SnowStormParticle::animations).ifPresent(animation ->
                PokemonBehaviourHelper.Companion.playAnimation(
                        entity,
                        new HashSet<>(animation.animations_apply()),
                        animation.expressions_apply()
                ));
        return effectId;
    }

    private static SnowStormParticle immediateSnowstorm(SnowStormParticle source) {
        return new SnowStormParticle(
                source.source_apply(),
                source.target_apply(),
                source.source_revert(),
                source.target_revert(),
                source.particle_apply(),
                Optional.empty(),
                source.particle_revert(),
                source.revert_after(),
                source.sound_apply(),
                source.sound_revert(),
                Optional.<AnimationData>empty()
        );
    }
}
