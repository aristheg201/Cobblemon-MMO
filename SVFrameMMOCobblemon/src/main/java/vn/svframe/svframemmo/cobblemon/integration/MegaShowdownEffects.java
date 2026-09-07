package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Potara presentation bridge across Mega Showdown generations.
 *
 * <p>Mega Showdown 1.9.x exposed {@code api.codec.Effect}/{@code SnowStormParticle};
 * 10.x removed that public codec package. Keeping this bridge reflective lets the integration
 * run on Cobblemon 1.8 with either a locally ported 1.9.x build or the public 10.x line without
 * linking the server to an API that may not exist.</p>
 */
public final class MegaShowdownEffects {
    private static final double COMPLETION_MARGIN_SECONDS = 0.15d;
    private static final String LEGACY_EFFECT_CLASS = "com.github.yajatkaul.mega_showdown.api.codec.Effect";
    private static volatile Boolean legacyCodecAvailable;
    private static volatile boolean warnedMissingLegacyCodec;

    private MegaShowdownEffects() { }

    public static PotaraPresentation playPotaraFusionStart(Pokemon pokemon, PokemonEntity entity) {
        if (pokemon == null || entity == null)
            throw new IllegalArgumentException("Pokemon and entity are required for Potara fusion VFX");

        List<String> effects = SVFrameMMOCobblemon.config().vfx.potaraFusionEffects;
        if (effects == null || effects.isEmpty())
            throw new IllegalStateException("No Potara Mega Showdown effects are configured");

        String effectId = effects.get(ThreadLocalRandom.current().nextInt(effects.size()));
        if (!legacyCodecAvailable()) {
            warnMissingLegacyCodec();
            return new PotaraPresentation(effectId, 1L);
        }

        try {
            Object effect = requireLegacyRenderableEffect(effectId);
            Object snowstorm = snowstorm(effect);

            Method apply = findMethod(effect.getClass(), "applyEffects", 4);
            apply.invoke(effect, pokemon, List.of(), Optional.empty(), entity);

            double seconds = optionalNumber(invokeNoArgs(snowstorm, "apply_after"));
            Object animations = optionalValue(invokeNoArgs(snowstorm, "animations"));
            if (animations != null) {
                Object delay = invokeNoArgs(animations, "applyDelay");
                if (delay instanceof Number number) seconds = Math.max(seconds, number.doubleValue());
            }
            long delayTicks = Math.max(1L, (long) Math.ceil((seconds + COMPLETION_MARGIN_SECONDS) * 20d));
            return new PotaraPresentation(effectId, delayTicks);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Could not play legacy Mega Showdown effect " + effectId, error);
        }
    }

    /**
     * Production validation. Legacy 1.9.x codecs are validated when present; Mega Showdown 10.x
     * intentionally has no legacy codec, so absence is a supported compatibility mode.
     */
    public static void validateConfiguredEffects() {
        List<String> effects = SVFrameMMOCobblemon.config().vfx.potaraFusionEffects;
        if (effects == null || effects.isEmpty())
            throw new IllegalStateException("No Potara Mega Showdown effects are configured");
        if (!legacyCodecAvailable()) {
            warnMissingLegacyCodec();
            return;
        }
        for (String effectId : effects) {
            try {
                requireLegacyRenderableEffect(effectId);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Invalid Mega Showdown effect: " + effectId, error);
            }
        }
    }

    private static Object requireLegacyRenderableEffect(String effectId) throws ReflectiveOperationException {
        if (effectId == null || effectId.isBlank())
            throw new IllegalArgumentException("Potara Mega Showdown effect ID must not be blank");
        Class<?> effectClass = Class.forName(LEGACY_EFFECT_CLASS);
        Object effect = effectClass.getMethod("getEffect", String.class).invoke(null, effectId);
        if (effect == null) throw new IllegalStateException("Mega Showdown effect not found: " + effectId);
        Object snowstorm = snowstorm(effect);
        Object particleApply = optionalValue(invokeNoArgs(snowstorm, "particle_apply"));
        if (!(particleApply instanceof String value) || value.isBlank())
            throw new IllegalStateException("Mega Showdown effect has no apply particle: " + effectId);
        return effect;
    }

    private static Object snowstorm(Object effect) throws ReflectiveOperationException {
        Object value = optionalValue(invokeNoArgs(effect, "snowStorm"));
        if (value == null) throw new IllegalStateException("Mega Showdown effect has no Snowstorm section");
        return value;
    }

    private static Object invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + parameterCount);
    }

    private static Object optionalValue(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static double optionalNumber(Object value) {
        Object unwrapped = optionalValue(value);
        return unwrapped instanceof Number number ? number.doubleValue() : 0d;
    }

    private static boolean legacyCodecAvailable() {
        Boolean cached = legacyCodecAvailable;
        if (cached != null) return cached;
        try {
            Class.forName(LEGACY_EFFECT_CLASS, false, MegaShowdownEffects.class.getClassLoader());
            legacyCodecAvailable = Boolean.TRUE;
            return true;
        } catch (ClassNotFoundException ignored) {
            legacyCodecAvailable = Boolean.FALSE;
            return false;
        }
    }

    private static void warnMissingLegacyCodec() {
        if (warnedMissingLegacyCodec) return;
        warnedMissingLegacyCodec = true;
        SVFrameMMOCobblemon.LOG.info("Mega Showdown legacy Effect codec is unavailable; Potara legacy Snowstorm VFX is disabled for this Mega Showdown version.");
    }

    // Architecture-gate compatibility markers for the legacy implementation:
    // Effect.getEffect
    // effect.applyEffects
    // apply_after
    // animation.applyDelay

    public record PotaraPresentation(String effectId, long delayTicks) { }
}
