package vn.svframe.svframelib.fabric.runtime;

import net.minecraft.entity.LivingEntity;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.damage.DamageMetadata;

import java.util.List;

/**
 * Converts an already-registered public SVFrameLib attack into the native Fabric
 * combat representation without discarding packet types or elemental identity.
 *
 * Minecraft may already have changed the raw amount before the
 * {@code modifyAppliedDamage} hook (armor, resistance, etc.). The registered
 * packet proportions are therefore scaled to the vanilla-modified amount rather
 * than replaying the pre-vanilla total a second time.
 */
public final class RegisteredDamageMetadataBridge {
    private RegisteredDamageMetadataBridge() { }

    public static NativeDamageMetadata resolve(LivingEntity target, double vanillaDamage, List<DamageType> fallbackTypes) {
        var registered = target == null ? null : SVFrameLib.inst().getDamage().getRegisteredAttackMetadata(target);
        return convert(registered == null ? null : registered.getDamage(), vanillaDamage, fallbackTypes);
    }

    public static NativeDamageMetadata convert(DamageMetadata registered, double vanillaDamage, List<DamageType> fallbackTypes) {
        double safeDamage = Double.isFinite(vanillaDamage) && vanillaDamage > 0d
                ? vanillaDamage : NativeDamageMetadata.MINIMAL_DAMAGE;
        if (registered == null) return new NativeDamageMetadata(safeDamage, fallbackTypes);

        double sourceTotal = 0d;
        for (vn.svframe.svframelib.damage.DamagePacket packet : registered.getPackets()) {
            sourceTotal += packet.getFinalValue();
        }
        if (!Double.isFinite(sourceTotal) || sourceTotal <= 0d) {
            return new NativeDamageMetadata(safeDamage, fallbackTypes);
        }

        double scale = safeDamage / sourceTotal;
        NativeDamageMetadata converted = null;
        for (vn.svframe.svframelib.damage.DamagePacket packet : registered.getPackets()) {
            double value = packet.getFinalValue() * scale;
            if (!Double.isFinite(value) || value <= 0d) continue;
            String element = packet.getElement() == null ? null : packet.getElement().getId();
            List<DamageType> types = packet.getTypes().stream()
                    .map(type -> DamageType.valueOf(type.name()))
                    .toList();
            if (converted == null) converted = new NativeDamageMetadata(value, element, types);
            else converted.add(value, element, types);
        }
        if (converted == null) return new NativeDamageMetadata(safeDamage, fallbackTypes);

        if (registered.isWeaponCriticalStrike()) converted.registerWeaponCriticalStrike();
        if (registered.isSkillCriticalStrike()) converted.registerSkillCriticalStrike();
        for (vn.svframe.svframelib.element.Element element : registered.collectElements()) {
            if (registered.isElementalCriticalStrike(element)) converted.registerElementalCriticalStrike(element.getId());
        }
        return converted;
    }
}
