package vn.svframe.svframelib.fabric;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import java.util.Map;

/** Shared item ability cost owner. Raw administrative/script casts intentionally bypass costs. */
public final class ItemSkillRuntime {
    private ItemSkillRuntime() { }

    public static boolean cast(String id, ServerPlayerEntity player, Entity target, Map<String, ?> parameters) {
        if (player == null || !player.isAlive() || player.isSpectator() || !SVFrameLibFabricMod.hasSkill(id)) return false;
        MMOPlayerData data = MMOPlayerData.setup(player);
        var cooldowns = data.getCooldownMap();
        String path = id.trim().toLowerCase(java.util.Locale.ROOT);
        if (cooldowns.isOnCooldown(path)) return false;
        var definition = SVFrameLibFabricMod.skillDefinitions().get(id.toLowerCase(java.util.Locale.ROOT));
        Map<String, ?> resolved = definition == null ? parameters : definition.resolveParameters(parameters, player.getUuid());
        for (String key : java.util.List.of("mana","stamina","cooldown")) if (resolved.get(key) instanceof Number n && (!Double.isFinite(n.doubleValue()) || n.doubleValue()<0)) return false;
        double mana = cost(resolved, "mana"), stamina = cost(resolved, "stamina"), cooldown = cost(resolved, "cooldown");
        var resources = SVFrameLib.inst().getManaModule();
        if (resources.getMana(data) < mana || resources.getStamina(data) < stamina) return false;
        if (!SVFrameLibFabricMod.castSkill(id, player.getUuid(), target == null ? null : target.getUuid(), resolved)) return false;
        if (cooldown > 0) {
            double reduction = Math.max(0, Math.min(1, data.getStatMap().getStat("COOLDOWN_REDUCTION") / 100d));
            cooldowns.applyCooldown(path, cooldown).reduceInitialCooldown(reduction);
        }
        if (mana > 0) resources.setMana(data, resources.getMana(data) - mana, ResourceUpdateReason.SKILL);
        if (stamina > 0) resources.setStamina(data, resources.getStamina(data) - stamina, ResourceUpdateReason.SKILL);
        return true;
    }

    private static double cost(Map<String, ?> parameters, String key) {
        Object raw = parameters.get(key);
        double value = raw instanceof Number number ? number.doubleValue() : 0;
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
