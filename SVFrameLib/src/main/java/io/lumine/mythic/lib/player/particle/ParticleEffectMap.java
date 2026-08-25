package io.lumine.mythic.lib.player.particle;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.modifier.ModifierMap;

import java.util.UUID;

/** Owns particle-effect activation priority across profile session transitions. */
public class ParticleEffectMap extends ModifierMap<ParticleEffect> {
    public ParticleEffectMap(MMOPlayerData playerData) {
        super(playerData);
    }

    @Override
    protected void onSessionClose() {
        modifiers.values().forEach(ParticleEffect::stop);
    }

    @Override
    protected void onSessionOpen() {
        for (ParticleEffect effect : modifiers.values()) {
            if (effect.isStarted()) throw new IllegalStateException("Active particle effect");
        }
        startOneAgain();
    }

    @Override
    public ParticleEffect addModifier(ParticleEffect effect) {
        effect.bindPlayerData(playerData);
        if (sessionOpen) {
            if (effect.getType().hasPriority()) {
                modifiers.values().forEach(ParticleEffect::stop);
                effect.start();
            } else if (modifiers.values().stream().noneMatch(other -> other.getType().hasPriority())) {
                effect.start();
            }
        }
        return (ParticleEffect) super.addModifier(effect);
    }

    @Override
    public ParticleEffect removeModifier(UUID id) {
        ParticleEffect removed = (ParticleEffect) super.removeModifier(id);
        if (sessionOpen && removed != null && removed.getType().hasPriority()) startOneAgain();
        return removed;
    }

    private void startOneAgain() {
        for (ParticleEffect effect : modifiers.values()) {
            if (effect.getType().hasPriority()) {
                effect.start();
                return;
            }
        }
        modifiers.values().forEach(ParticleEffect::start);
    }
}
