package vn.svframe.svframelib.damage.onhit;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.api.event.OnHitEffectEvent;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
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

/** Source-derived Fabric implementation of the 1.7.1 on-hit effect module. */
@ModuleInfo(key = "on_hit_effects")
public final class OnHitModule extends Module {
    private final Map<String, OnHitEffect> registry = new LinkedHashMap<>();
    private boolean listenerRegistered;

    public OnHitModule(MythicLib plugin) { super(plugin, "on_hit_effects"); }

    @Override protected void onStartup() {
        if (listenerRegistered) return;
        listenerRegistered = true;
        PlayerAttackEvent.EVENT.register(this::onHitAttackEffects);
    }

    @Override protected void onReset() { registry.clear(); }

    @Override protected void onReload() {
        Map<String,Object> root = new YamlFile("on_hit_effects").getContent();
        if (root == null) return;
        for (Map.Entry<String,Object> entry : root.entrySet()) {
            if (!(entry.getValue() instanceof Map<?,?> raw)) continue;
            try {
                OnHitEffect effect = new OnHitEffect(new ConfigSectionObject(entry.getKey(), stringMap(raw)));
                registry.put(effect.getId(), effect);
            } catch (RuntimeException exception) {
                MythicLib.plugin.getLogger().log(Level.WARNING, "Could not load on-hit effect '" + entry.getKey() + "': " + exception.getMessage());
            }
        }
    }

    public OnHitEffect getOnHitEffect(String id) { return Objects.requireNonNull(registry.get(id), "No on-hit effect with ID '" + id + "'"); }
    public Map<String, OnHitEffect> getOnHitEffects() { return Map.copyOf(registry); }

    private void onHitAttackEffects(PlayerAttackEvent event) {
        if (!isEnabled() || registry.isEmpty() || event.isCancelled()) return;
        MMOPlayerData playerData = event.getAttacker().getData();
        Lazy<SkillMetadata> lazySkillMeta = SkillMetadata.lazyOf(event);
        for (OnHitEffect effect : registry.values()) {
            if (effect.preAttack() != null && !effect.preAttack().cast(lazySkillMeta.get()).isSuccessful()) continue;
            if (effect.hasCooldown() && playerData.getCooldownMap().isOnCooldown(effect)) continue;
            if (effect.getRoll() != null && Math.random() > effect.getRoll().evaluate(lazySkillMeta)) continue;
            if (!effect.skipsEvent()) {
                OnHitEffectEvent nativeEvent = new OnHitEffectEvent(playerData, effect, event.getAttack()).call();
                if (nativeEvent.isCancelled()) continue;
            }
            if (effect.hasCooldown()) playerData.getCooldownMap().applyCooldown(effect, effect.getCooldown().evaluate(lazySkillMeta));
            effect.onAttack().cast(lazySkillMeta.get());
        }
    }

    private static Map<String,Object> stringMap(Map<?,?> raw) { Map<String,Object> out = new LinkedHashMap<>(); raw.forEach((key, value) -> out.put(String.valueOf(key), value)); return out; }
}
