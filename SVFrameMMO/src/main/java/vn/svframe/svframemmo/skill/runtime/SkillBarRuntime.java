package vn.svframe.svframemmo.skill.runtime;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.message.actionbar.ActionBarPriority;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerEnterCastingModeEvent;
import vn.svframe.svframemmo.api.event.PlayerExitCastingModeEvent;
import vn.svframe.svframemmo.api.event.PlayerKeyPressEvent;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.config.SVFrameMMOConfig;
import vn.svframe.svframemmo.skill.ClassSkill;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;
import vn.svframe.svframemmo.skill.cast.ComboMap;
import vn.svframe.svframemmo.skill.cast.KeyCombo;
import vn.svframe.svframemmo.skill.cast.Keybind;
import vn.svframe.svframemmo.skill.cast.PlayerKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native implementation of SKILL_BAR, SKILL_SCROLLER, KEY_COMBOS and NONE. */
public final class SkillBarRuntime {
    private static final int DEFAULT_BAR_PRIORITY = ActionBarPriority.LOWEST;
    private static final int CASTING_BAR_PRIORITY = ActionBarPriority.LOW;
    private static final int PARTICLES_PER_TICK = 2;

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> persistentScrollIndex = new ConcurrentHashMap<>();
    private final Map<UUID, LastKey> lastKeys = new ConcurrentHashMap<>();
    private volatile boolean callbacksInstalled;

    /** Installs server-side vanilla input callbacks once. Packet-only keys are handled by the network mixin. */
    public synchronized void install() {
        if (callbacksInstalled) return;
        callbacksInstalled = true;
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity server)) return ActionResult.PASS;
            handleKey(server, PlayerKey.LEFT_CLICK);
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity server)) return ActionResult.PASS;
            handleKey(server, PlayerKey.LEFT_CLICK);
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity server)) return ActionResult.PASS;
            handleKey(server, PlayerKey.RIGHT_CLICK);
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity server)) return ActionResult.PASS;
            handleKey(server, PlayerKey.RIGHT_CLICK);
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity server) handleKey(server, PlayerKey.RIGHT_CLICK);
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
    }

    /** Compatibility entry point retained for the original SKILL_BAR packet hook. */
    public boolean handleSwapHands(ServerPlayerEntity player) { return handleKey(player, PlayerKey.SWAP_HANDS); }

    /** Fires the public key event first, then routes uncancelled input to the configured casting handler. */
    public boolean handleKey(ServerPlayerEntity player, PlayerKey key) {
        if (player == null || key == null) return false;
        if (deduplicate(player.getUuid(), key)) return false;
        PlayerData data = SVFrameMMO.playerData().get(player);
        PlayerKeyPressEvent event = new PlayerKeyPressEvent(data, key).call();
        if (event.isCancelled()) return true;

        SVFrameMMOConfig.SkillCasting casting = SVFrameMMO.config().skillCasting();
        if (casting.disabled()) return false;
        if (casting.ignoreSneak() && player.isSneaking()) return false;

        if (casting.skillBarMode()) return handleSkillBarKey(data, key, casting);
        if (casting.scrollerMode()) return handleScrollerKey(data, key, casting);
        if (casting.comboMode()) return handleComboKey(data, key, casting);
        return false;
    }

    public boolean handleSelectedSlot(ServerPlayerEntity player, int selectedSlot) {
        if (selectedSlot < 0 || selectedSlot >= 9) return false;
        Session session = sessions.get(player.getUuid());
        if (session == null) return false;
        SVFrameMMOConfig.SkillCasting casting = SVFrameMMO.config().skillCasting();
        if (casting.ignoreSneak() && player.isSneaking()) return false;
        if (casting.skillBarMode()) return handleSkillBarSlot(player, selectedSlot, session, casting);
        if (casting.scrollerMode()) return handleScrollerSlot(player, selectedSlot, session, casting);
        return false;
    }

    public boolean isCasting(UUID player) { return sessions.containsKey(player); }
    public String mode(UUID player) { Session session = sessions.get(player); return session == null ? null : session.mode; }

    public boolean leave(PlayerData data) { return leave(data, false); }
    public boolean leave(PlayerData data, boolean skipEvent) {
        if (data == null) return false;
        Session current = sessions.get(data.getUniqueId());
        if (current == null) return false;
        if (!skipEvent) {
            PlayerExitCastingModeEvent event = new PlayerExitCastingModeEvent(data).call();
            if (event.isCancelled()) return false;
        }
        sessions.remove(data.getUniqueId(), current);
        closeVisuals(data);
        return true;
    }

    public void detach(UUID player) {
        Session removed = sessions.remove(player);
        lastKeys.remove(player);
        if (removed != null) {
            PlayerData data = SVFrameMMO.playerData().find(player);
            if (data != null && data.isOnline()) closeVisuals(data);
        }
    }

    public void clear() {
        for (UUID id : List.copyOf(sessions.keySet())) detach(id);
        sessions.clear();
        lastKeys.clear();
    }

    public void tick(long tick) {
        SVFrameMMOConfig live = SVFrameMMO.config();
        for (PlayerData data : SVFrameMMO.playerData().all()) {
            if (!data.isOnline()) continue;
            var mmo = data.getMMOPlayerData();
            if (data.getPlayer().isDead() || !mmo.isPlaying()) {
                mmo.getActionBar().reset(DEFAULT_BAR_PRIORITY);
                continue;
            }
            Session session = sessions.get(data.getUniqueId());
            if (session != null) {
                List<CastBinding> active = activeSkills(data, live.skillCasting());
                if (active.isEmpty() || (live.skillCasting().timeoutTicks() > 0
                        && tick - session.lastActivityTick > live.skillCasting().timeoutTicks())) {
                    leave(data, true);
                    continue;
                }
                castingParticles(data, session);
                session.counter++;
                if ((session.counter & 7L) == 0L) {
                    if (live.skillCasting().skillBarMode() && (session.counter & 15L) == 0L) showSkillBar(data, live.skillCasting());
                    else if (live.skillCasting().scrollerMode()) showScroller(data, session, live.skillCasting());
                    else if (live.skillCasting().comboMode()) showCombo(data, session, live.skillCasting());
                }
            } else if (live.actionBar().enabled() && tick % live.actionBar().updateTicks() == 0L) {
                mmo.getActionBar().show(DEFAULT_BAR_PRIORITY,
                        Math.max(2L, live.actionBar().updateTicks() + 1L),
                        () -> SVFrameLib.inst().parseColors(formatDefaultBar(data, live.actionBar().format())));
            }
        }
    }

    private boolean handleSkillBarKey(PlayerData data, PlayerKey key, SVFrameMMOConfig.SkillCasting config) {
        Keybind open = config.openKey();
        if (open == null || !open.matches(key, data.getPlayer().isSneaking())) return false;
        boolean cancel = key.shouldCancelEvent();
        if (data.getPlayer().isSpectator() || (data.getPlayer().isCreative() && !SVFrameMMO.config().canCreativeCast())) return cancel;
        if (sessions.containsKey(data.getUniqueId())) {
            if (leave(data, false)) send(data, config.quitMessage());
            return cancel;
        }
        if (activeSkills(data, config).isEmpty()) return cancel;
        if (enter(data, config.mode())) {
            send(data, config.enterMessage());
            showSkillBar(data, config);
        }
        return cancel;
    }

    private boolean handleSkillBarSlot(ServerPlayerEntity player, int selectedSlot, Session session, SVFrameMMOConfig.SkillCasting config) {
        int currentSlot = player.getInventory().selectedSlot;
        if (selectedSlot == currentSlot) return true;
        session.lastActivityTick = SVFrameMMO.currentTick();
        PlayerData data = SVFrameMMO.playerData().get(player);
        CastBinding binding = skillForClickedSlot(data, config, currentSlot, selectedSlot);
        if (binding != null) cast(data, binding);
        showSkillBar(data, config);
        player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(currentSlot));
        return true;
    }

    private boolean handleScrollerKey(PlayerData data, PlayerKey key, SVFrameMMOConfig.SkillCasting config) {
        SVFrameMMOConfig.ScrollerCasting options = config.scroller();
        if (options == null) return false;
        if (data.getPlayer().isCreative() && !SVFrameMMO.config().canCreativeCast()) return false;
        boolean sneaking = data.getPlayer().isSneaking();
        Session active = sessions.get(data.getUniqueId());

        if (options.enterKey().matches(key, sneaking)) {
            boolean cancel = key.shouldCancelEvent();
            if (active != null) {
                if (leave(data, false)) playSound(data.getPlayer(), options.leaveSound());
                return cancel;
            }
            if (activeSkills(data, config).isEmpty()) return cancel;
            if (enter(data, config.mode())) {
                Session created = sessions.get(data.getUniqueId());
                created.scrollIndex = normalizedScrollIndex(data, config);
                playSound(data.getPlayer(), options.enterSound());
                showScroller(data, created, config);
            }
            return cancel;
        }

        active = sessions.get(data.getUniqueId());
        if (active == null) return false;
        if (options.castKey().matches(key, sneaking)) {
            boolean cancel = key.shouldCancelEvent();
            CastBinding selected = selectedScroller(data, active, config);
            if (selected != null) {
                var result = cast(data, selected);
                if (result != null && result.isSuccessful() && options.quitOnCast()) leave(data, false);
            }
            active.lastActivityTick = SVFrameMMO.currentTick();
            return cancel;
        }
        if (options.scrollKey() != null && options.scrollKey().matches(key, sneaking)) {
            scroll(data, active, config, 1);
            return true;
        }
        if (options.scrollBackKey() != null && options.scrollBackKey().matches(key, sneaking)) {
            scroll(data, active, config, -1);
            return true;
        }
        return false;
    }

    private boolean handleScrollerSlot(ServerPlayerEntity player, int selectedSlot, Session session, SVFrameMMOConfig.SkillCasting config) {
        SVFrameMMOConfig.ScrollerCasting options = config.scroller();
        if (options == null) return false;
        int previous = player.getInventory().selectedSlot;
        if (selectedSlot == previous) return false;
        if (options.quitOnSwitchEmptyHand() && player.getInventory().getStack(selectedSlot).isEmpty()) {
            leave(SVFrameMMO.playerData().get(player), true);
            return false;
        }
        if (options.scrollKey() != null) return false;
        int dist1 = 9 + selectedSlot - previous;
        int dist2 = selectedSlot - previous;
        int dist3 = selectedSlot - previous - 9;
        int change = Math.abs(dist1) < Math.abs(dist2)
                ? (Math.abs(dist1) < Math.abs(dist3) ? dist1 : dist3)
                : (Math.abs(dist3) < Math.abs(dist2) ? dist3 : dist2);
        scroll(SVFrameMMO.playerData().get(player), session, config, change);
        player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(previous));
        return true;
    }

    private boolean handleComboKey(PlayerData data, PlayerKey key, SVFrameMMOConfig.SkillCasting config) {
        SVFrameMMOConfig.ComboCasting options = config.combos();
        if (options == null || activeSkills(data, config).isEmpty()) return false;
        if (data.getPlayer().isCreative() && !SVFrameMMO.config().canCreativeCast()) return false;
        boolean sneaking = data.getPlayer().isSneaking();
        Session active = sessions.get(data.getUniqueId());

        if (active == null && options.initializerKey() != null) {
            if (!options.initializerKey().matches(key, sneaking)) return false;
            boolean cancel = key.shouldCancelEvent();
            if (enter(data, config.mode())) {
                playSound(data.getPlayer(), options.beginComboSound());
                showCombo(data, sessions.get(data.getUniqueId()), config);
            }
            return cancel;
        }

        if (active != null && options.quitKey() != null && options.quitKey().matches(key, sneaking)) {
            boolean cancel = key.shouldCancelEvent();
            leave(data, true);
            playSound(data.getPlayer(), options.failComboSound());
            return cancel;
        }

        ComboMap combos = comboMap(data, options);
        if (combos == null || !combos.isComboKey(key)) return false;
        if (active == null) {
            if (!combos.isComboStart(key) || !enter(data, config.mode())) return false;
            active = sessions.get(data.getUniqueId());
            playSound(data.getPlayer(), options.beginComboSound());
        }

        active.lastActivityTick = SVFrameMMO.currentTick();
        active.combo.registerKey(key);
        showCombo(data, active, config);
        playSound(data.getPlayer(), options.comboKeySound());
        boolean cancel = key.shouldCancelEvent();

        Integer spellSlot = combos.getCombos().get(active.combo);
        if (spellSlot != null) {
            if (options.stayIn()) active.combo = new KeyCombo();
            else leave(data, true);
            CastBinding binding = skillForBoundSlot(data, config, spellSlot);
            if (binding != null && !binding.skill().getTrigger().isPassive()) {
                var result = cast(data, binding);
                if (result != null && !result.isSuccessful()) playSound(data.getPlayer(), options.failSkillSound());
            } else if (options.stayIn()) playSound(data.getPlayer(), options.failComboSound());
            return cancel;
        }

        if (active.combo.countKeys() >= combos.getLongest()) {
            if (options.stayIn()) active.combo = new KeyCombo();
            else leave(data, true);
            playSound(data.getPlayer(), options.failComboSound());
        }
        return cancel;
    }

    private boolean enter(PlayerData data, String mode) {
        if (sessions.containsKey(data.getUniqueId())) return false;
        PlayerEnterCastingModeEvent event = new PlayerEnterCastingModeEvent(data).call();
        if (event.isCancelled()) return false;
        sessions.put(data.getUniqueId(), new Session(mode, SVFrameMMO.currentTick()));
        return true;
    }

    private void scroll(PlayerData data, Session session, SVFrameMMOConfig.SkillCasting config, int delta) {
        if (delta == 0) return;
        List<CastBinding> active = activeSkills(data, config);
        if (active.isEmpty()) { leave(data, true); return; }
        session.scrollIndex = remainder(session.scrollIndex + delta, active.size());
        persistentScrollIndex.put(data.getUniqueId(), session.scrollIndex);
        session.lastActivityTick = SVFrameMMO.currentTick();
        showScroller(data, session, config);
        var options = config.scroller();
        playSound(data.getPlayer(), delta > 0 || options.changeBackSound() == null ? options.changeSound() : options.changeBackSound());
    }

    private int normalizedScrollIndex(PlayerData data, SVFrameMMOConfig.SkillCasting config) {
        List<CastBinding> active = activeSkills(data, config);
        if (active.isEmpty()) return 0;
        return remainder(persistentScrollIndex.getOrDefault(data.getUniqueId(), 0), active.size());
    }

    private CastBinding selectedScroller(PlayerData data, Session session, SVFrameMMOConfig.SkillCasting config) {
        List<CastBinding> active = activeSkills(data, config);
        if (active.isEmpty()) return null;
        session.scrollIndex = remainder(session.scrollIndex, active.size());
        return active.get(session.scrollIndex);
    }

    private static ComboMap comboMap(PlayerData data, SVFrameMMOConfig.ComboCasting options) {
        ComboMap classMap = data.getProfess().getComboMap();
        return classMap != null ? classMap : options.globalCombos();
    }

    private static vn.svframe.svframelib.skill.result.SkillResult cast(PlayerData data, CastBinding binding) {
        return binding.temporary() ? SVFrameMMO.skillRuntime().castTemporary(data, binding.skill()) : SVFrameMMO.skillRuntime().cast(data, binding.skill());
    }

    private static CastBinding skillForClickedSlot(PlayerData data, SVFrameMMOConfig.SkillCasting config, int currentSlot, int clickedSlot) {
        for (CastBinding binding : activeSkills(data, config)) {
            int castSlot = binding.castSlot();
            int displayedIndex = castSlot + (currentSlot < castSlot ? 1 : 0);
            if (displayedIndex == clickedSlot + 1) return binding;
        }
        return null;
    }

    private static CastBinding skillForBoundSlot(PlayerData data, SVFrameMMOConfig.SkillCasting config, int slot) {
        for (CastBinding binding : activeSkills(data, config)) if (binding.boundSlot() == slot) return binding;
        return null;
    }

    private static List<CastBinding> activeSkills(PlayerData data, SVFrameMMOConfig.SkillCasting config) {
        List<TemporarySkillOverlayRuntime.Slot> temporary = SVFrameMMO.temporarySkills().slots(data.getUniqueId());
        if (!temporary.isEmpty()) {
            ArrayList<CastBinding> result = new ArrayList<>(temporary.size());
            int compact = 1;
            for (TemporarySkillOverlayRuntime.Slot slot : temporary) {
                ClassSkill skill = slot.skill();
                if (skill == null || skill.getTrigger().isPassive()) continue;
                int castSlot = config.useLowestKeybinds() ? compact++ : slot.slot();
                result.add(new CastBinding(slot.slot(), castSlot, skill, true));
            }
            result.sort(Comparator.comparingInt(CastBinding::boundSlot));
            return List.copyOf(result);
        }

        ArrayList<Map.Entry<Integer, PlayerSkillCatalog.Entry>> bound = new ArrayList<>(PlayerSkillCatalog.bindings(data).entrySet());
        bound.sort(Map.Entry.comparingByKey());
        ArrayList<CastBinding> result = new ArrayList<>();
        int compact = 1;
        for (Map.Entry<Integer, PlayerSkillCatalog.Entry> entry : bound) {
            PlayerSkillCatalog.Entry owned = entry.getValue();
            ClassSkill skill = owned == null ? null : owned.skill();
            if (skill == null || skill.getTrigger().isPassive() || !owned.learned()) continue;
            int castSlot = config.useLowestKeybinds() ? compact++ : entry.getKey();
            result.add(new CastBinding(entry.getKey(), castSlot, skill, false));
        }
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
            if (cooldowns.isOnCooldown(skill.getCooldownPath()))
                format = options.onCooldown().replace("{cooldown}", Long.toString(Math.max(0L, (long) cooldowns.getCooldown(skill.getCooldownPath()))));
            else if (parameter(skill, "mana", data) > data.getMana()) format = options.noMana();
            else if (parameter(skill, "stamina", data) > data.getStamina()) format = options.noStamina();
            else format = options.ready();
            int displayedIndex = binding.castSlot() + (currentSlot < binding.castSlot() ? 1 : 0);
            out.append(format.replace("{index}", Integer.toString(displayedIndex)).replace("{skill}", skill.getSkill().getName()));
        }
        data.getMMOPlayerData().getActionBar().show(CASTING_BAR_PRIORITY, 30L, SVFrameLib.inst().parseColors(out.toString()));
    }

    private static void showScroller(PlayerData data, Session session, SVFrameMMOConfig.SkillCasting config) {
        List<CastBinding> active = activeSkills(data, config);
        if (active.isEmpty()) return;
        session.scrollIndex = remainder(session.scrollIndex, active.size());
        CastBinding selected = active.get(session.scrollIndex);
        String raw = config.scroller().actionBarFormat().replace("{selected}", selected.skill().getSkill().getName());
        data.getMMOPlayerData().getActionBar().show(CASTING_BAR_PRIORITY, 20L, SVFrameLib.inst().parseColors(raw));
    }

    private static void showCombo(PlayerData data, Session session, SVFrameMMOConfig.SkillCasting config) {
        var options = config.combos();
        if (options == null || options.actionBar() == null) return;
        ComboMap combos = comboMap(data, options);
        if (combos == null) return;
        var bar = options.actionBar();
        StringBuilder builder = new StringBuilder(bar.prefix());
        int count = session.combo.countKeys();
        builder.append(count == 0 ? bar.noKey() : bar.keyNames().getOrDefault(session.combo.getAt(0), session.combo.getAt(0).name()));
        int index = 1;
        for (; index < count; index++)
            builder.append(bar.separator()).append(bar.keyNames().getOrDefault(session.combo.getAt(index), session.combo.getAt(index).name()));
        for (; index < combos.getLongest(); index++) builder.append(bar.separator()).append(bar.noKey());
        builder.append(bar.suffix());
        String text = SVFrameLib.inst().parseColors(builder.toString());
        if (bar.subtitle()) {
            data.getPlayer().networkHandler.sendPacket(new TitleFadeS2CPacket(0, 20, 0));
            data.getPlayer().networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(text)));
        } else data.getMMOPlayerData().getActionBar().show(CASTING_BAR_PRIORITY, 20L, text);
    }

    private static void castingParticles(PlayerData data, Session session) {
        var particle = data.getProfess().getCastParticle();
        if (particle == null) return;
        for (int k = 0; k < PARTICLES_PER_TICK; k++) {
            double angle = (double) (PARTICLES_PER_TICK * session.counter + k) / 4d;
            particle.display(data.getPlayer().getServerWorld(), data.getPlayer().getPos().add(
                    Math.cos(angle), 1d + Math.sin(angle / 3d) / 1.3d, Math.sin(angle)));
        }
    }

    private static void closeVisuals(PlayerData data) { data.getMMOPlayerData().getActionBar().reset(CASTING_BAR_PRIORITY); }

    private static void send(PlayerData data, SVFrameMMOConfig.PlayerMessage options) {
        if (options == null || !data.isOnline()) return;
        String parsed = SVFrameLib.inst().parseColors(options.message());
        if (parsed != null && !parsed.isBlank()) {
            if (options.actionBar()) data.getMMOPlayerData().getActionBar().show(options.priority(), options.duration(), parsed);
            else data.getPlayer().sendMessage(Text.literal(parsed), false);
        }
        playSound(data.getPlayer(), options.sound());
    }

    private static void playSound(ServerPlayerEntity player, SVFrameMMOConfig.SoundSpec sound) {
        if (sound == null) return;
        playSound(player, sound.sound(), sound.volume(), sound.pitch());
    }

    private static void playSound(ServerPlayerEntity player, String configured) {
        if (configured == null || configured.isBlank()) return;
        String[] split = configured.split(",", -1);
        playSound(player, split[0].trim(), floatValue(split, 1, 1f), floatValue(split, 2, 1f));
    }

    private static void playSound(ServerPlayerEntity player, String rawId, float volume, float pitch) {
        Identifier id = resolveSoundId(rawId);
        if (id == null) return;
        Registries.SOUND_EVENT.getEntry(id).ifPresent(sound -> player.getServerWorld().playSound(
                null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch));
    }

    private static Identifier resolveSoundId(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        if (rawId.indexOf(':') >= 0) {
            Identifier direct = Identifier.tryParse(rawId.toLowerCase(Locale.ROOT));
            return direct != null && Registries.SOUND_EVENT.containsId(direct) ? direct : null;
        }
        String legacy = rawId.trim().toUpperCase(Locale.ROOT);
        for (Identifier candidate : Registries.SOUND_EVENT.getIds()) {
            String normalized = candidate.getPath().replace('.', '_').replace('/', '_').toUpperCase(Locale.ROOT);
            if (normalized.equals(legacy)) return candidate;
        }
        Identifier direct = Identifier.tryParse("minecraft:" + rawId.toLowerCase(Locale.ROOT));
        return direct != null && Registries.SOUND_EVENT.containsId(direct) ? direct : null;
    }

    private boolean deduplicate(UUID id, PlayerKey key) {
        long tick = SVFrameMMO.currentTick();
        LastKey previous = lastKeys.put(id, new LastKey(tick, key));
        return previous != null && previous.tick == tick && previous.key == key;
    }

    private static double parameter(ClassSkill skill, String id, PlayerData data) {
        if (!skill.getParameters().containsKey(id)) return 0d;
        int level = PlayerSkillCatalog.level(data, skill);
        return Math.max(0d, skill.getParameter(id, level, data));
    }

    private static String formatDefaultBar(PlayerData data, String raw) {
        String format = data.getProfess().hasActionBar() ? data.getProfess().getActionBar() : raw;
        if (format == null) return "";
        return format.replace("{health}", trim(data.getHealth())).replace("{max_health}", trim(data.getMaxResource(PlayerResource.HEALTH)))
                .replace("{mana_icon}", manaIcon(data)).replace("{mana}", trim(data.getMana()))
                .replace("{max_mana}", trim(data.getMaxResource(PlayerResource.MANA))).replace("{stamina}", trim(data.getStamina()))
                .replace("{max_stamina}", trim(data.getMaxResource(PlayerResource.STAMINA))).replace("{stellium}", trim(data.getStellium()))
                .replace("{max_stellium}", trim(data.getMaxResource(PlayerResource.STELLIUM))).replace("{class}", data.getProfess().getName())
                .replace("{xp}", trim(data.getExperience())).replace("{armor}", trim(data.getMMOPlayerData().getStatMap().getStat("ARMOR")))
                .replace("{level}", Integer.toString(data.getLevel())).replace("{name}", data.getPlayer().getDisplayName().getString());
    }

    private static String manaIcon(PlayerData data) {
        Object rawMana = data.getProfess().getRawConfig().get("mana");
        if (rawMana instanceof Map<?, ?> mana) {
            Object icon = mana.get("icon");
            if (icon != null) return SVFrameLib.inst().parseColors(String.valueOf(icon));
        }
        return "§9✦";
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static float floatValue(String[] values, int index, float fallback) {
        if (index >= values.length) return fallback;
        try { return Float.parseFloat(values[index].trim()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int remainder(int value, int size) {
        if (size <= 0) return 0;
        int result = value % size;
        return result < 0 ? result + size : result;
    }

    private record CastBinding(int boundSlot, int castSlot, ClassSkill skill, boolean temporary) { }
    private record LastKey(long tick, PlayerKey key) { }
    private static final class Session {
        private final String mode;
        private volatile long lastActivityTick;
        private long counter = -1L;
        private int scrollIndex;
        private KeyCombo combo = new KeyCombo();
        private Session(String mode, long tick) { this.mode = mode; this.lastActivityTick = tick; }
    }
}
