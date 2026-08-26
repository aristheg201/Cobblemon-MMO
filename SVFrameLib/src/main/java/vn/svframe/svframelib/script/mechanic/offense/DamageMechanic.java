package vn.svframe.svframelib.script.mechanic.offense;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.DamageMetadata;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.element.Element;
import vn.svframe.svframelib.script.mechanic.type.TargetMechanic;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native Fabric implementation of MythicLib 1.7.1 DamageMechanic. */
public class DamageMechanic extends TargetMechanic {
    private final NumericExpression amount;
    private final boolean knockback;
    private final boolean ignoreImmunity;
    private final List<DamageType> types;
    private final String elementName;

    public DamageMechanic(ConfigObject config) {
        super(config);
        config.validateKeys("amount");
        amount = NumericExpression.compile(config.getString("amount"));
        knockback = config.getBoolean("knockback", true);
        ignoreImmunity = config.getBoolean("ignore_immunity", false);
        if (config.contains("damage_type")) {
            List<DamageType> parsed = new ArrayList<>();
            for (String token : config.getString("damage_type").split(","))
                parsed.add(DamageType.valueOf(token.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')));
            types = List.copyOf(parsed);
        } else {
            types = List.of(DamageType.MAGIC, DamageType.SKILL);
        }
        elementName = config.contains("element")
                ? config.getString("element").trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')
                : null;
    }

    @Override
    public void cast(SkillMetadata meta, Entity target) {
        if (!(target instanceof LivingEntity living)) throw new IllegalArgumentException("Cannot damage a non living entity");
        double evaluated = amount.evaluate(meta);
        Element element = elementName == null ? null : MythicLib.plugin.getElements().get(elementName);

        AttackMetadata registered = MythicLib.plugin.getDamage().getRegisteredAttackMetadata(living);
        if (registered != null) {
            registered.getDamage().add(evaluated, element, types);
            return;
        }

        DamageMetadata damage = element == null
                ? new DamageMetadata(evaluated, types)
                : new DamageMetadata(evaluated, element, types);
        AttackMetadata attack = new AttackMetadata(damage, living, meta.getCaster());
        MythicLib.plugin.getDamage().registerAttack(attack, knockback, ignoreImmunity);
    }
}
