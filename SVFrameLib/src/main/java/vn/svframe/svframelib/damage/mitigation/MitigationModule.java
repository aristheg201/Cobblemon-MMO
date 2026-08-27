package vn.svframe.svframelib.damage.mitigation;

import net.minecraft.entity.Entity;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.event.AttackEvent;
import vn.svframe.svframelib.api.event.DamageMitigationEvent;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.module.Module;
import vn.svframe.svframelib.module.ModuleInfo;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.Lazy;
import vn.svframe.svframelib.util.config.YamlFile;
import vn.svframe.svframelib.util.configobject.ConfigSectionObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/** Source-derived Fabric implementation of the 1.7.1 damage mitigation module. */
@ModuleInfo(key = "damage_mitigation")
public final class MitigationModule extends Module {
    private final Map<String, MitigationType> registry = new LinkedHashMap<>();
    private boolean listenerRegistered;

    public MitigationModule(SVFrameLib plugin) { super(plugin, "damage_mitigation"); }

    @Override protected void onStartup() {
        if (listenerRegistered) return;
        listenerRegistered = true;
        AttackEvent.EVENT.register(this::applyMitigationTypes);
    }

    @Override protected void onReset() { registry.clear(); }

    @Override protected void onReload() {
        Map<String,Object> root = new YamlFile("mitigation_types").getContent();
        if (root == null) return;
        for (Map.Entry<String,Object> entry : root.entrySet()) {
            if (!(entry.getValue() instanceof Map<?,?> raw)) continue;
            try {
                MitigationType type = new MitigationType(new ConfigSectionObject(entry.getKey(), stringMap(raw)));
                registry.put(type.getId(), type);
            } catch (RuntimeException exception) {
                SVFrameLib.plugin.getLogger().log(Level.WARNING, "Could not load mitigation type '" + entry.getKey() + "': " + exception.getMessage());
            }
        }
    }

    public MitigationType getMitigationType(String id) { return Objects.requireNonNull(registry.get(id), "No mitigation type with ID '" + id + "'"); }
    public Map<String, MitigationType> getMitigationTypes() { return Map.copyOf(registry); }

    private void applyMitigationTypes(AttackEvent event) {
        if (!isEnabled() || registry.isEmpty() || event.isCancelled()) return;
        MMOPlayerData playerData = MMOPlayerData.getOrNull(event.getEntity());
        if (playerData == null) return;
        Lazy<SkillMetadata> lazySkillMeta = Lazy.of(() -> {
            Entity attacker = event.getAttack().getAttacker() == null ? null : event.getAttack().getAttacker().getEntity();
            return SkillMetadata.of(playerData, EquipmentSlot.MAIN_HAND, playerData.getPlayer().getPos(), attacker, null, event.getAttack(), null, event);
        });
        for (MitigationType type : registry.values()) {
            if (type.preDamage() != null && !type.preDamage().cast(lazySkillMeta.get()).isSuccessful()) continue;
            if (type.hasCooldown() && playerData.getCooldownMap().isOnCooldown(type)) continue;
            if (type.getRoll() != null && Math.random() > type.getRoll().evaluate(lazySkillMeta)) continue;
            if (!type.skipsEvent()) {
                DamageMitigationEvent nativeEvent = type.asLegacy() == null ? new DamageMitigationEvent(playerData, type, event.getAttack()) : type.asLegacy().generateLegacyEvent(playerData, event.getAttack(), type);
                nativeEvent.call();
                if (nativeEvent.isCancelled()) continue;
            }
            if (type.hasCooldown()) playerData.getCooldownMap().applyCooldown(type, type.getCooldown().evaluate(lazySkillMeta));
            type.onDamage().cast(lazySkillMeta.get());
        }
    }

    private static Map<String,Object> stringMap(Map<?,?> raw) { Map<String,Object> out = new LinkedHashMap<>(); raw.forEach((key, value) -> out.put(String.valueOf(key), value)); return out; }
}
