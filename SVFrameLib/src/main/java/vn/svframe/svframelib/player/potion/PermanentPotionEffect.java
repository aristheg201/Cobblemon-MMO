package vn.svframe.svframelib.player.potion;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.modifier.ModifierMap;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.PlayerModifier;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Objects;

public class PermanentPotionEffect extends PlayerModifier {
    private final RegistryEntry<StatusEffect> effect;
    private final int amplifier;

    public PermanentPotionEffect(String key, RegistryEntry<StatusEffect> effect, int amplifier) {
        super(key, EquipmentSlot.OTHER, ModifierSource.OTHER);
        if (amplifier < 0) {
            throw new IllegalArgumentException("Amplifier must be positive");
        }
        this.effect = effect;
        this.amplifier = amplifier;
    }

    public PermanentPotionEffect(ConfigObject config) {
        super(config.getString("key"), EquipmentSlot.OTHER, ModifierSource.OTHER);
        this.effect = resolveEffectOrNull(config.getString("effect"));
        this.amplifier = config.getInt("level") - 1;
    }

    public StatusEffectInstance toNative() {
        return new StatusEffectInstance(effect, permanentDuration(effect), amplifier);
    }

    public RegistryEntry<StatusEffect> getEffect() {
        return effect;
    }

    public int getAmplifier() {
        return amplifier;
    }

    @Override
    public void register(MMOPlayerData playerData) {
        playerData.getPermanentEffectMap().addModifier(this);
    }

    @Override
    public void unregister(MMOPlayerData playerData) {
        playerData.getPermanentEffectMap().removeModifier(getUniqueId());
    }

    @Override
    public ModifierMap<?> getMap(MMOPlayerData playerData) {
        return playerData.getPermanentEffectMap();
    }

    public static PermanentPotionEffect fromConfig(ConfigObject config) {
        return new PermanentPotionEffect(config);
    }

    static int permanentDuration(RegistryEntry<StatusEffect> effect) {
        if (effect.equals(StatusEffects.NIGHT_VISION) || effect.equals(StatusEffects.NAUSEA)) {
            return 260;
        }
        if (effect.equals(StatusEffects.BLINDNESS)) {
            return 140;
        }
        return 80;
    }

    private static RegistryEntry<StatusEffect> resolveEffectOrNull(String raw) {
        if (raw == null) return null;

        String input = raw.trim();
        if (input.isEmpty()) return null;

        // MythicLib 1.7.1 delegates this to Bukkit PotionEffectType#getByName.
        // That API accepts the historical Bukkit names used by existing MMOItems/
        // MMOCore configs, many of which do not match Minecraft registry paths.
        String path = switch (input.toUpperCase(Locale.ROOT)) {
            case "SPEED" -> "speed";
            case "SLOW", "SLOWNESS" -> "slowness";
            case "FAST_DIGGING", "HASTE" -> "haste";
            case "SLOW_DIGGING", "MINING_FATIGUE" -> "mining_fatigue";
            case "INCREASE_DAMAGE", "STRENGTH" -> "strength";
            case "HEAL", "INSTANT_HEALTH" -> "instant_health";
            case "HARM", "INSTANT_DAMAGE" -> "instant_damage";
            case "JUMP", "JUMP_BOOST" -> "jump_boost";
            case "CONFUSION", "NAUSEA" -> "nausea";
            case "REGENERATION" -> "regeneration";
            case "DAMAGE_RESISTANCE", "RESISTANCE" -> "resistance";
            case "FIRE_RESISTANCE" -> "fire_resistance";
            case "WATER_BREATHING" -> "water_breathing";
            case "INVISIBILITY" -> "invisibility";
            case "BLINDNESS" -> "blindness";
            case "NIGHT_VISION" -> "night_vision";
            case "HUNGER" -> "hunger";
            case "WEAKNESS" -> "weakness";
            case "POISON" -> "poison";
            case "WITHER" -> "wither";
            case "HEALTH_BOOST" -> "health_boost";
            case "ABSORPTION" -> "absorption";
            case "SATURATION" -> "saturation";
            case "GLOWING" -> "glowing";
            case "LEVITATION" -> "levitation";
            case "LUCK" -> "luck";
            case "UNLUCK" -> "unluck";
            case "SLOW_FALLING" -> "slow_falling";
            case "CONDUIT_POWER" -> "conduit_power";
            case "DOLPHINS_GRACE" -> "dolphins_grace";
            case "BAD_OMEN" -> "bad_omen";
            case "HERO_OF_THE_VILLAGE" -> "hero_of_the_village";
            case "DARKNESS" -> "darkness";
            default -> input.toLowerCase(Locale.ROOT);
        };

        Identifier id = path.indexOf(':') >= 0
                ? Identifier.tryParse(path)
                : Identifier.tryParse("minecraft:" + path);
        return id == null ? null : Registries.STATUS_EFFECT.getEntry(id).orElse(null);
    }
}
