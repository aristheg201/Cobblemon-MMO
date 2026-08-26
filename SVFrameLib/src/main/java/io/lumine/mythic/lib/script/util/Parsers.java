package io.lumine.mythic.lib.script.util;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.comp.interaction.InteractionType;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.player.resource.ResourceUpdateReason;
import io.lumine.mythic.lib.script.mechanic.shaped.RayTraceMechanic;
import io.lumine.mythic.lib.script.variable.VariableScope;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import io.lumine.mythic.lib.util.EntityLocationType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class Parsers {
    public static final Function<String, RegistryEntry<StatusEffect>> POTION_EFFECT_TYPE = input -> Registries.STATUS_EFFECT.getEntry(id(input)).orElseThrow(() -> new IllegalArgumentException("No potion effect with ID '" + input + "'"));
    public static final Function<String, DamageType> DAMAGE_TYPE = input -> DamageType.valueOf(enumName(input));
    public static final Function<String, TriggerType> SKILL_TRIGGER = input -> TriggerType.valueOf(enumName(input));
    public static final Function<String, List<DamageType>> DAMAGE_TYPES = input -> Arrays.stream(input.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).map(DAMAGE_TYPE).toList();
    public static final Function<String, RayTraceMechanic.RayTraceType> RAY_TRACE_TYPE = ofEnum(RayTraceMechanic.RayTraceType.class, value -> RayTraceMechanic.RayTraceType.valueOf(enumName(value)));
    public static final Function<String, Item> MATERIAL = input -> { Item value = Registries.ITEM.get(id(input)); if (value == null) throw new IllegalArgumentException("No material with ID '" + input + "'"); return value; };
    public static final Function<String, ParticleType<?>> PARTICLE = input -> { ParticleType<?> value = Registries.PARTICLE_TYPE.get(id(input)); if (value == null) throw new IllegalArgumentException("No particle with ID '" + input + "'"); return value; };
    public static final Function<String, VariableScope> VARIABLE_SCOPE = ofEnum(VariableScope.class, value -> VariableScope.valueOf(enumName(value)));
    public static final Function<String, SkillHandler<?>> SKILL_HANDLER = input -> MythicLib.plugin.getSkills().getHandlerOrThrow(enumName(input));
    public static final Function<String, InteractionType> INTERACTION_TYPE = ofEnum(InteractionType.class, value -> InteractionType.valueOf(enumName(value)));
    public static final Function<String, EntityLocationType> ENTITY_LOCATION_TYPE = ofEnum(EntityLocationType.class, value -> EntityLocationType.valueOf(enumName(value)));
    public static final Function<String, ResourceUpdateReason> RESOURCE_UPDATE_REASON = ofEnum(ResourceUpdateReason.class, value -> ResourceUpdateReason.valueOf(enumName(value)));

    public static <T> Function<String, T> ofEnum(Class<T> type, Function<String, T> parser) {
        return input -> {
            try { return parser.apply(input); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("No " + type.getSimpleName() + " with ID '" + input + "'", exception); }
        };
    }

    private static Identifier id(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!value.contains(":")) value = "minecraft:" + value;
        Identifier id = Identifier.tryParse(value);
        if (id == null) throw new IllegalArgumentException("Invalid identifier '" + raw + "'");
        return id;
    }
    private static String enumName(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
}
