package vn.svframe.svframelib.version;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

public enum VPotionEffectType {
    NAUSEA(StatusEffects.NAUSEA),
    SLOWNESS(StatusEffects.SLOWNESS),
    JUMP_BOOST(StatusEffects.JUMP_BOOST),
    MINING_FATIGUE(StatusEffects.MINING_FATIGUE),
    HASTE(StatusEffects.HASTE);

    private final RegistryEntry<StatusEffect> effect;
    VPotionEffectType(RegistryEntry<StatusEffect> effect) { this.effect = effect; }
    public RegistryEntry<StatusEffect> get() { return effect; }
}
