package io.lumine.mythic.lib.player.modifier;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.particle.ParticleEffect;
import io.lumine.mythic.lib.player.permission.PermissionModifier;
import io.lumine.mythic.lib.player.potion.PermanentPotionEffect;
import io.lumine.mythic.lib.player.skill.PassiveSkill;
import io.lumine.mythic.lib.player.skillmod.SkillModifier;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.util.configobject.ConfigObject;
import io.lumine.mythic.lib.util.lang3.NotImplementedException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public abstract class PlayerModifier {
    private final UUID uniqueId;
    private final ModifierSource source;
    private final EquipmentSlot slot;
    private final String key;

    private static final Map<String, Function<ConfigObject, PlayerModifier>> BY_KEY = new HashMap<>();

    public PlayerModifier(String key, EquipmentSlot slot, ModifierSource source) {
        this(UUID.randomUUID(), key, slot, source);
    }

    public PlayerModifier(UUID uniqueId, String key, EquipmentSlot slot, ModifierSource source) {
        this.uniqueId = uniqueId;
        this.key = key;
        this.slot = slot;
        this.source = source;
    }

    public UUID getUniqueId() { return uniqueId; }
    public String getKey() { return key; }
    public EquipmentSlot getSlot() { return slot; }
    public ModifierSource getSource() { return source; }

    public abstract void register(MMOPlayerData playerData);
    public abstract void unregister(MMOPlayerData playerData);

    /** Legacy helper retained for modifier implementations which expose a ModifierMap. */
    public ModifierMap<?> getMap(MMOPlayerData playerData) {
        throw new NotImplementedException();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        return uniqueId.equals(((PlayerModifier) object).uniqueId);
    }

    @Override
    public int hashCode() { return Objects.hash(uniqueId); }

    public static void registerPlayerModifierType(String key, Function<ConfigObject, PlayerModifier> resolver, String... aliases) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(resolver, "Resolver cannot be null");
        BY_KEY.put(key, resolver);
        for (String alias : aliases) {
            Objects.requireNonNull(alias, "Alias cannot be null");
            BY_KEY.put(alias, resolver);
        }
    }

    public static PlayerModifier from(ConfigObject config) {
        Objects.requireNonNull(config, "Config cannot be null");
        String key = config.getKey();
        if (key == null) key = config.getString("type");
        Function<ConfigObject, PlayerModifier> resolver = BY_KEY.get(key);
        if (resolver == null) throw new IllegalArgumentException(String.format("Could not match player modifier type to %s", key));
        return resolver.apply(config);
    }

    static {
        registerPlayerModifierType("particle_effect", ParticleEffect::fromConfig, "particle", "particles");
        registerPlayerModifierType("potion_effect", PermanentPotionEffect::fromConfig, "potion", "potioneffect", "pot");
        registerPlayerModifierType("stat", StatModifier::new, "stats", "mmostat");
        registerPlayerModifierType("skill", PassiveSkill::fromConfig, "ability", "passive_skill", "passive");
        registerPlayerModifierType("skill_modifier", SkillModifier::fromConfig, "skill_mod", "skillmod", "skillmodifier");
        registerPlayerModifierType("permission", PermissionModifier::fromConfig, "perm", "perm_node", "permnode");
    }
}
