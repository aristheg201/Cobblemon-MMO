package io.lumine.mythic.lib.player.potion;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.modifier.ModifierMap;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PermanentPotionEffectMap extends ModifierMap<PermanentPotionEffect> {
    private final List<ResolvedEffect> nativeEffectCache = new ArrayList<>();

    public PermanentPotionEffectMap(MMOPlayerData playerData) {
        super(playerData);
    }

    @Override
    public PermanentPotionEffect addModifier(PermanentPotionEffect modifier) {
        PermanentPotionEffect previous = super.addModifier(modifier);
        resolvePermanentEffects();
        return previous;
    }

    @Override
    public PermanentPotionEffect removeModifier(UUID uniqueId) {
        PermanentPotionEffect previous = super.removeModifier(uniqueId);
        resolvePermanentEffects();
        return previous;
    }

    public void applyPermanentPotionEffects() {
        if (!sessionOpen) {
            throw new IllegalStateException("Session not open");
        }
        for (ResolvedEffect resolved : nativeEffectCache) {
            getPlayerData().getPlayer().addStatusEffect(resolved.create());
        }
    }

    private void resolvePermanentEffects() {
        Map<RegistryEntry<StatusEffect>, Integer> resolved = new HashMap<>();
        for (PermanentPotionEffect effect : modifiers.values()) {
            resolved.merge(effect.getEffect(), effect.getAmplifier(), Math::max);
        }

        nativeEffectCache.clear();
        resolved.forEach((effect, amplifier) -> {
            // Resolve duration while rebuilding the cache, as the original implementation
            // materializes its permanent potion objects at this exact point.
            int duration = PermanentPotionEffect.permanentDuration(effect);
            nativeEffectCache.add(new ResolvedEffect(effect, duration, amplifier));
        });
    }

    private record ResolvedEffect(RegistryEntry<StatusEffect> effect, int duration, int amplifier) {
        private StatusEffectInstance create() {
            // Minecraft mutates active StatusEffectInstance duration. Materialize a fresh native
            // instance from the immutable cached definition every time it is applied.
            return new StatusEffectInstance(effect, duration, amplifier);
        }
    }
}
