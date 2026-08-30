package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.types.ElementalType;
import net.minecraft.entity.LivingEntity;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.element.Element;
import vn.svframe.svframelib.skill.SkillMetadata;

import java.util.Objects;

/**
 * Preserves the originating SVFrameLib cast context while attaching the live Cobblemon
 * elemental type to every damage packet produced by that cast.
 */
public final class CobblemonElementalSkillMetadata extends SkillMetadata {
    private final ElementalType cobblemonType;
    private final Element element;

    public CobblemonElementalSkillMetadata(SkillMetadata source, ElementalType cobblemonType) {
        super(
                Objects.requireNonNull(source, "source").getCast(),
                source.getCaster(),
                source.getVariableList(),
                source.getSourceLocation(),
                source.getTargetLocationOrNull(),
                source.getTargetEntityOrNull(),
                source.getOrientationOrNull(),
                source.hasAttackSource() ? source.getAttackSource() : null,
                source.getSourceEvent());
        this.cobblemonType = Objects.requireNonNull(cobblemonType, "cobblemonType");
        this.element = Element.forDamage(cobblemonType.getName(), cobblemonType.getDisplayName().getString());
    }

    public ElementalType cobblemonType() { return cobblemonType; }
    public Element element() { return element; }

    @Override
    public AttackMetadata attack(LivingEntity target, double damage, DamageType... damageTypes) {
        return getCaster().attack(target, damage, element, damageTypes);
    }
}
