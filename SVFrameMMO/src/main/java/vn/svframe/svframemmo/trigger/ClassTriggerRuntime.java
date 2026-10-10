package vn.svframe.svframemmo.trigger;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.skill.trigger.TriggerType;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.api.event.PlayerCombatEvent;
import vn.svframe.svframemmo.api.event.PlayerLevelChangeEvent;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Dispatches class-defined gameplay triggers from native server events. */
public final class ClassTriggerRuntime {
    private final Set<UUID> combat = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, CombatTiming> combatTiming = new ConcurrentHashMap<>();

    public void onJoin(PlayerData data) {
        if (data == null || !data.isOnline()) return;
        data.getMMOPlayerData().triggerSkills(TriggerType.LOGIN);
        if (data.isInCombat()) {
            combat.add(data.getUniqueId());
            combatTiming.putIfAbsent(data.getUniqueId(), new CombatTiming(SVFrameMMO.currentTick(), SVFrameMMO.currentTick()));
        }
    }

    public void onAttack(PlayerAttackEvent event) {
        if (event == null || event.isCancelled()) return;
        ServerPlayerEntity attackerEntity = event.getPlayer();
        if (attackerEntity == null) return;
        if (event.getAttack().getTarget() == attackerEntity) return;

        PlayerData attacker = SVFrameMMO.playerData().get(attackerEntity.getUuid());
        enterCombat(attacker);
        for (DamageType type : event.getAttack().getDamage().collectTypes())
            attacker.getProfess().fireEventTriggers(type.getPath() + "-damage", attacker);

        if (event.getAttack().getTarget() instanceof ServerPlayerEntity target) {
            PlayerData victim = SVFrameMMO.playerData().get(target.getUuid());
            enterCombat(victim);
        }
    }

    public void onClassChange(PlayerClassChangeEvent event) {
        if (event == null) return;
        SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + 1L, () -> {
            if (event.isCancelled()) return;
            PlayerData data = event.getData();
            PlayerClass selected = event.getNewClass();
            selected.fireEventTriggers("class-chosen", data);
            if (data.isOnline()) data.getMMOPlayerData().triggerSkills(trigger("CLASS_CHOSEN"));
        });
    }

    public void onLevelChange(PlayerLevelChangeEvent event) {
        if (event == null || event.getReason() != PlayerLevelChangeEvent.Reason.LEVEL_UP) return;
        PlayerData data = event.getData();
        PlayerClass profess = data.getProfess();
        for (int value = event.getOldLevel() + 1; value <= event.getNewLevel(); value++) {
            if (event.isMainLevel()) {
                fire(profess, data, "level-up");
                fire(profess, data, "level-up-" + value);
                if (profess.hasMaxLevel() && value == profess.getMaxLevel()) fire(profess, data, "level-up-max");
                fireMultiples(profess, data, null, value);
            } else {
                String profession = event.getProfession().getId().toLowerCase(Locale.ROOT);
                fire(profess, data, "level-up-" + profession);
                fire(profess, data, "level-up-" + profession + "-" + value);
                fireMultiples(profess, data, profession, value);
            }
        }
        if (event.isMainLevel() && data.isOnline())
            SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + 1L,
                    () -> { if (data.isOnline()) data.getMMOPlayerData().triggerSkills(trigger("LEVEL_UP")); });
    }

    public void tick(long tick) {
        if ((tick & 3L) != 0L || combat.isEmpty()) return;
        for (UUID id : new ArrayList<>(combat)) {
            PlayerData data = SVFrameMMO.playerData().find(id);
            if (data == null || !data.isOnline()) {
                combat.remove(id);
                continue;
            }
            if (!data.isInCombat() && combat.remove(id)) {
                combatTiming.remove(id);
                new PlayerCombatEvent(data, false).call();
                data.getProfess().fireEventTriggers("quit-combat", data);
                data.getMMOPlayerData().triggerSkills(trigger("QUIT_COMBAT"));
            }
        }
    }

    public void detach(UUID id) { combat.remove(id); combatTiming.remove(id); }
    public void clear() { combat.clear(); combatTiming.clear(); }

    public double secondsSinceEnter(UUID id) {
        CombatTiming timing = combatTiming.get(id);
        PlayerData data = SVFrameMMO.playerData().find(id);
        return timing == null || data == null || !data.isInCombat() ? -1d : Math.max(0d, (SVFrameMMO.currentTick() - timing.enteredTick()) / 20d);
    }

    public double secondsSinceLastHit(UUID id) {
        CombatTiming timing = combatTiming.get(id);
        PlayerData data = SVFrameMMO.playerData().find(id);
        return timing == null || data == null || !data.isInCombat() ? -1d : Math.max(0d, (SVFrameMMO.currentTick() - timing.lastHitTick()) / 20d);
    }

    private void enterCombat(PlayerData data) {
        UUID id = data.getUniqueId();
        boolean first = combat.add(id) || !data.isInCombat();
        long now = SVFrameMMO.currentTick();
        CombatTiming previous = combatTiming.get(id);
        combatTiming.put(id, first || previous == null ? new CombatTiming(now, now) : new CombatTiming(previous.enteredTick(), now));
        data.markCombat();
        if (!first) return;
        new PlayerCombatEvent(data, true).call();
        data.getProfess().fireEventTriggers("enter-combat", data);
        if (data.isOnline()) data.getMMOPlayerData().triggerSkills(trigger("ENTER_COMBAT"));
    }

    private record CombatTiming(long enteredTick, long lastHitTick) { }

    private static TriggerType trigger(String id) { return TriggerType.valueOf(id); }

    private static void fire(PlayerClass profess, PlayerData data, String id) {
        if (profess.hasEventTriggers(id)) profess.fireEventTriggers(id, data);
    }

    private static void fireMultiples(PlayerClass profess, PlayerData data, String profession, int level) {
        for (String id : profess.getEventTriggers()) {
            if (!id.startsWith("level-up-multiple-")) continue;
            String[] split = id.split("-");
            if (split.length < 4) continue;
            final double multiple;
            try { multiple = Double.parseDouble(split[split.length - 1]); }
            catch (NumberFormatException ignored) { continue; }
            if (multiple <= 0d || level / multiple % 1d != 0d) continue;
            String suffix = new DecimalFormat("#").format(multiple);
            if (profession == null) fire(profess, data, "level-up-multiple-" + suffix);
            else fire(profess, data, "level-up-multiple-" + profession + "-" + suffix);
        }
    }
}
