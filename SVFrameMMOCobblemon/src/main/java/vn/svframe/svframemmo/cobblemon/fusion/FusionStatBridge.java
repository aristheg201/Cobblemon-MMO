package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Applies one immutable set of Pokemon-derived RPG stat modifiers per active fusion. */
public final class FusionStatBridge {
    private static final String KEY = "svframemmo_cobblemon_fusion";
    private final Map<UUID, Map<String, UUID>> active = new ConcurrentHashMap<>();

    public void apply(ServerPlayerEntity player, Pokemon pokemon, FusionTier tier) {
        remove(player);
        var cfg = SVFrameMMOCobblemon.config().fusion.statConversion;
        double rank = tier.multiplier();
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("MAX_HEALTH", pokemon.getStat(Stats.HP) * cfg.hpToMaxHealth * rank);
        values.put("MAX_MANA", pokemon.getStat(Stats.SPECIAL_ATTACK) * cfg.specialAttackToMaxMana * rank);
        values.put("MAX_STAMINA", pokemon.getStat(Stats.SPEED) * cfg.speedToMaxStamina * rank);
        values.put("ATTACK_DAMAGE", Math.max(pokemon.getStat(Stats.ATTACK), pokemon.getStat(Stats.SPECIAL_ATTACK)) * cfg.offenseToAttackDamage * rank);

        MMOPlayerData data = MMOPlayerData.setup(player);
        LinkedHashMap<String, UUID> ids = new LinkedHashMap<>();
        data.getStatMap().bufferUpdates(() -> {
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                if (entry.getValue() <= 0d) continue;
                UUID id = UUID.nameUUIDFromBytes((player.getUuid() + ":fusion:" + entry.getKey()).getBytes(StandardCharsets.UTF_8));
                data.getStatMap().getInstance(entry.getKey()).registerModifier(new StatModifier(id, KEY, entry.getKey(), entry.getValue(),
                        ModifierType.FLAT, EquipmentSlot.OTHER, ModifierSource.OTHER));
                ids.put(entry.getKey(), id);
            }
        });
        active.put(player.getUuid(), Map.copyOf(ids));
    }

    public void remove(ServerPlayerEntity player) {
        if (player == null) return;
        Map<String, UUID> ids = active.remove(player.getUuid());
        if (ids == null || ids.isEmpty()) return;
        MMOPlayerData data = MMOPlayerData.setup(player);
        data.getStatMap().bufferUpdates(() -> ids.forEach((stat, id) -> data.getStatMap().getInstance(stat).removeModifier(id)));
    }
}
