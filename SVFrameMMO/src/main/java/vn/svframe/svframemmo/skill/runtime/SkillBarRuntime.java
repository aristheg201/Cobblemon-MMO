package vn.svframe.svframemmo.skill.runtime;

import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.message.actionbar.ActionBarPriority;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.config.SVFrameMMOConfig;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native server-side SKILL_BAR casting. F is the vanilla swap-hands packet; no client keybind is required. */
public final class SkillBarRuntime {
    private static final int DEFAULT_BAR_PRIORITY = ActionBarPriority.LOWEST;
    private static final int SKILL_BAR_PRIORITY = ActionBarPriority.LOW;
    private static final int CAST_MESSAGE_PRIORITY = ActionBarPriority.NORMAL + 1;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    /** Returns true when vanilla SWAP_ITEM_WITH_OFFHAND must be cancelled. */
    public boolean handleSwapHands(ServerPlayerEntity player) {
        SVFrameMMOConfig.SkillCasting config = SVFrameMMO.config().skillCasting();
        if (!config.opensWithSwapHands()) return false;
        if (config.ignoreSneak() && player.isSneaking()) return false;
        PlayerData data = SVFrameMMO.playerData().get(player);
        Session active = sessions.remove(player.getUuid());
        if (active != null) {
            close(data, config.quitMessage());
            return true;
        }
        if (!player.isSpectator() && !activeSkills(data, config).isEmpty()) {
            sessions.put(player.getUuid(), new Session(SVFrameMMO.currentTick()));
            data.getMMOPlayerData().getActionBar().show(CAST_MESSAGE_PRIORITY, 20L, SVFrameLib.inst().parseColors(config.enterMessage()));
            showSkillBar(data, config);
        }
        return true;
    }

    /** Returns true when the hotbar slot change packet must be cancelled because it was used as a skill key. */
    public boolean handleSelectedSlot(ServerPlayerEntity player, int selectedSlot) {
        Session session = sessions.get(player.getUuid());
        if (session == null) return false;
        SVFrameMMOConfig.SkillCasting config = SVFrameMMO.config().skillCasting();
        if (config.ignoreSneak() && player.isSneaking()) return false;
        int currentSlot = player.getInventory().selectedSlot;
        session.lastActivityTick = SVFrameMMO.currentTick();
        if (selectedSlot >= 0 && selectedSlot < 9 && selectedSlot != currentSlot) {
            PlayerData data = SVFrameMMO.playerData().get(player);
            ClassSkill skill = skillForClickedSlot(data, config, currentSlot, selectedSlot);
            if (skill != null) SVFrameMMO.skillRuntime().cast(data, skill);
            showSkillBar(data, config);
        }
        player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(currentSlot));
        return true;
    }

    public boolean isCasting(UUID player) { return sessions.containsKey(player); }
    public void detach(UUID player) { sessions.remove(player); }
    public void clear() { sessions.clear(); }

    public void tick(long tick) {
        SVFrameMMOConfig live = SVFrameMMO.config();
        for (PlayerData data : SVFrameMMO.playerData().all()) {
            if (!data.isOnline()) continue;
            Session session = sessions.get(data.getUniqueId());
            if (session != null) {
                if (activeSkills(data, live.skillCasting()).isEmpty() || (live.skillCasting().timeoutTicks() > 0 && tick - session.lastActivityTick > live.skillCasting().timeoutTicks())) {
                    sessions.remove(data.getUniqueId());
                    close(data, null);
                } else if ((tick & 7L) == 0L) showSkillBar(data, live.skillCasting());
            } else if (live.actionBar().enabled() && tick % live.actionBar().updateTicks() == 0L) {
                data.getMMOPlayerData().getActionBar().show(DEFAULT_BAR_PRIORITY, Math.max(2L, live.actionBar().updateTicks() + 1L),
                        SVFrameLib.inst().parseColors(formatDefaultBar(data, live.actionBar().format())));
            }
        }
    }

    private void close(PlayerData data, String message) {
        data.getMMOPlayerData().getActionBar().reset(SKILL_BAR_PRIORITY);
        if (message != null && !message.isBlank())
            data.getMMOPlayerData().getActionBar().show(CAST_MESSAGE_PRIORITY, 20L, SVFrameLib.inst().parseColors(message));
    }

    private static ClassSkill skillForClickedSlot(PlayerData data, SVFrameMMOConfig.SkillCasting config, int currentSlot, int clickedSlot) {
        for (CastBinding binding : activeSkills(data, config)) {
            int castSlot = binding.castSlot();
            int displayedIndex = castSlot + (currentSlot < castSlot ? 1 : 0);
            if (displayedIndex == clickedSlot + 1) return binding.skill();
        }
        return null;
    }

    private static List<CastBinding> activeSkills(PlayerData data, SVFrameMMOConfig.SkillCasting config) {
        ArrayList<Map.Entry<Integer, String>> bound = new ArrayList<>(data.getSkillBindings().entrySet());
        bound.sort(Map.Entry.comparingByKey());
        ArrayList<CastBinding> result = new ArrayList<>();
        int compact = 1;
        for (Map.Entry<Integer, String> entry : bound) {
            ClassSkill skill = data.getProfess().getSkill(entry.getValue());
            if (skill == null || skill.getTrigger().isPassive() || !data.canUseSkill(skill)) continue;
            int castSlot = config.useLowestKeybinds() ? compact++ : entry.getKey();
            result.add(new CastBinding(castSlot, skill));
        }
        result.sort(Comparator.comparingInt(CastBinding::castSlot));
        return List.copyOf(result);
    }

    private static void showSkillBar(PlayerData data, SVFrameMMOConfig.SkillCasting config) {
        if (!data.isOnline()) return;
        List<CastBinding> active = activeSkills(data, config);
        if (active.isEmpty()) return;
        int currentSlot = data.getPlayer().getInventory().selectedSlot;
        StringBuilder out = new StringBuilder();
        var options = config.actionBar();
        for (CastBinding binding : active) {
            if (!out.isEmpty()) out.append(options.split());
            ClassSkill skill = binding.skill();
            var cooldowns = data.getMMOPlayerData().getCooldownMap();
            String format;
            if (cooldowns.isOnCooldown(skill.getCooldownPath())) format = options.onCooldown().replace("{cooldown}", trim(cooldowns.getCooldown(skill.getCooldownPath())));
            else if (parameter(skill, "mana", data) > data.getMana()) format = options.noMana();
            else if (parameter(skill, "stamina", data) > data.getStamina()) format = options.noStamina();
            else format = options.ready();
            int displayedIndex = binding.castSlot() + (currentSlot < binding.castSlot() ? 1 : 0);
            out.append(format.replace("{index}", Integer.toString(displayedIndex)).replace("{skill}", skill.getSkill().getName()));
        }
        data.getMMOPlayerData().getActionBar().show(SKILL_BAR_PRIORITY, 30L, SVFrameLib.inst().parseColors(out.toString()));
    }

    private static double parameter(ClassSkill skill, String id, PlayerData data) {
        return skill.getParameters().containsKey(id) ? Math.max(0d, skill.getParameter(id, data)) : 0d;
    }

    private static String formatDefaultBar(PlayerData data, String raw) {
        String format = data.getProfess().hasActionBar() ? data.getProfess().getActionBar() : raw;
        if (format == null) return "";
        return format
                .replace("{health}", trim(data.getHealth()))
                .replace("{max_health}", trim(data.getMaxResource(PlayerResource.HEALTH)))
                .replace("{mana_icon}", manaIcon(data))
                .replace("{mana}", trim(data.getMana()))
                .replace("{max_mana}", trim(data.getMaxResource(PlayerResource.MANA)))
                .replace("{stamina}", trim(data.getStamina()))
                .replace("{max_stamina}", trim(data.getMaxResource(PlayerResource.STAMINA)))
                .replace("{stellium}", trim(data.getStellium()))
                .replace("{max_stellium}", trim(data.getMaxResource(PlayerResource.STELLIUM)))
                .replace("{class}", data.getProfess().getName())
                .replace("{xp}", trim(data.getExperience()))
                .replace("{armor}", trim(data.getMMOPlayerData().getStatMap().getStat("ARMOR")))
                .replace("{level}", Integer.toString(data.getLevel()))
                .replace("{name}", data.getPlayer().getGameProfile().getName());
    }

    private static String manaIcon(PlayerData data) {
        Object rawMana = data.getProfess().getRawConfig().get("mana");
        if (rawMana instanceof Map<?, ?> mana) {
            Object icon = mana.get("icon");
            if (icon != null) return SVFrameLib.inst().parseColors(String.valueOf(icon));
        }
        return "♦";
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private record CastBinding(int castSlot, ClassSkill skill) { }
    private static final class Session { private volatile long lastActivityTick; private Session(long tick) { this.lastActivityTick = tick; } }
}
