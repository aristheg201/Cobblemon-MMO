package vn.svframe.svframemmo.pvp;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.comp.flags.CustomFlag;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.config.DefaultFiles;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Native Fabric PvP-mode runtime matching the RPG combat/toggle/region semantics. */
public final class PvpModeRuntime {
    private static final PvpModeRuntime INSTANCE = new PvpModeRuntime();

    private final Map<UUID, State> states = new HashMap<>();
    private volatile Settings settings = Settings.defaults();

    private PvpModeRuntime() {
        reload();
        PlayerAttackEvent.EVENT.register(this::onAttack);
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> states.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> states.clear());
    }

    public static PvpModeRuntime instance() {
        return INSTANCE;
    }

    public synchronized void reload() {
        try {
            Map<String, Object> root = map(YamlLite.parse(DefaultFiles.ROOT.resolve("config.yml")));
            settings = Settings.from(map(first(root, "pvp-mode", "pvp_mode")));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load PvP mode config", exception);
        }
    }

    public void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("pvpmode").executes(ctx -> toggle(ctx.getSource().getPlayer())));
    }

    public synchronized boolean isEnabled(ServerPlayerEntity player) {
        return state(player).pvpMode;
    }

    public synchronized boolean isInvulnerable(ServerPlayerEntity player) {
        return state(player).invulnerableUntilTick > SVFrameMMO.currentTick();
    }

    public synchronized double invulnerabilitySecondsLeft(ServerPlayerEntity player) {
        if (player == null) return 0d;
        return Math.max(0d, (state(player).invulnerableUntilTick - SVFrameMMO.currentTick()) / 20d);
    }

    private synchronized int toggle(ServerPlayerEntity player) {
        Settings cfg = settings;
        if (!cfg.enabled) {
            message(player, "§cPvP mode is disabled by the server.");
            return 0;
        }

        State state = state(player);
        long now = SVFrameMMO.currentTick();
        if (state.cooldownUntilTick > now) {
            message(player, "§cPvP mode cooldown: §f" + seconds(state.cooldownUntilTick - now) + "s");
            return 0;
        }

        state.pvpMode = !state.pvpMode;
        state.cooldownUntilTick = now + secondsToTicks(state.pvpMode ? cfg.toggleOnCooldown : cfg.toggleOffCooldown);
        if (state.pvpMode && inPvpModeRegion(player)) {
            state.invulnerableUntilTick = now + secondsToTicks(cfg.invulnerabilityCommand);
            message(player, "§aPvP mode enabled. §eInvulnerable for " + format(cfg.invulnerabilityCommand) + "s.");
        } else {
            message(player, state.pvpMode ? "§aPvP mode enabled." : "§cPvP mode disabled.");
        }
        return 1;
    }

    private synchronized void onAttack(PlayerAttackEvent event) {
        if (event.isCancelled() || !(event.getAttack().getTarget() instanceof ServerPlayerEntity target)) return;
        Settings cfg = settings;
        if (!cfg.enabled) return;

        ServerPlayerEntity source = event.getPlayer();
        PlayerData sourceData = SVFrameMMO.playerData().get(source);
        PlayerData targetData = SVFrameMMO.playerData().get(target);

        if (cfg.minLevel > 0) {
            if (targetData.getLevel() < cfg.minLevel) {
                deny(event, source, "§cThat player is below the minimum PvP level.");
                return;
            }
            if (sourceData.getLevel() < cfg.minLevel) {
                deny(event, source, "§cYou are below the minimum PvP level.");
                return;
            }
            if (cfg.maxLevelDifference > 0 && Math.abs(targetData.getLevel() - sourceData.getLevel()) > cfg.maxLevelDifference) {
                deny(event, source, "§cThe level difference is too high for PvP.");
                return;
            }
        }

        long now = SVFrameMMO.currentTick();
        State attacker = state(source), defender = state(target);
        if (defender.invulnerableUntilTick > now) {
            deny(event, source, "§cThat player is temporarily PvP-invulnerable for §f" + seconds(defender.invulnerableUntilTick - now) + "s§c.");
            return;
        }
        if (!cfg.invulnerabilityCanDamage && attacker.invulnerableUntilTick > now) {
            deny(event, source, "§cYou cannot attack while PvP-invulnerable for §f" + seconds(attacker.invulnerableUntilTick - now) + "s§c.");
            return;
        }

        if (inPvpModeRegion(target)) {
            if (!defender.pvpMode) {
                deny(event, source, "§cThat player has PvP mode disabled.");
                return;
            }
            if (!attacker.pvpMode) {
                deny(event, source, "§cEnable PvP mode before attacking here.");
                return;
            }
        }

        attacker.lastHitTick = defender.lastHitTick = now;
        attacker.invulnerableUntilTick = defender.invulnerableUntilTick = 0L;
        long combatCooldown = now + secondsToTicks(cfg.combatCooldown);
        attacker.cooldownUntilTick = Math.max(attacker.cooldownUntilTick, combatCooldown);
        defender.cooldownUntilTick = Math.max(defender.cooldownUntilTick, combatCooldown);
    }

    private synchronized void tick() {
        Settings cfg = settings;
        if (!cfg.enabled) return;
        for (PlayerData data : SVFrameMMO.playerData().all()) {
            ServerPlayerEntity player = data.getPlayer();
            if (player == null) continue;
            State state = state(player);
            boolean current = inPvpModeRegion(player);
            if (!state.regionInitialized) {
                state.regionInitialized = true;
                state.inPvpRegion = current;
                continue;
            }
            if (current == state.inPvpRegion) continue;
            boolean previous = state.inPvpRegion;
            state.inPvpRegion = current;
            long now = SVFrameMMO.currentTick();

            if (current && !previous) {
                state.cooldownUntilTick = Math.max(state.cooldownUntilTick, now + secondsToTicks(cfg.regionEnterCooldown));
                if (state.pvpMode && canQuitPvpMode(state, cfg, now)) {
                    state.invulnerableUntilTick = now + secondsToTicks(cfg.invulnerabilityRegionChange);
                    message(player, "§eEntered a PvP-mode region. §f" + format(cfg.invulnerabilityRegionChange) + "s §eof invulnerability.");
                } else {
                    message(player, "§eEntered a PvP-mode region. PvP mode is " + (state.pvpMode ? "§aON" : "§cOFF") + "§e.");
                }
            } else if (!current && previous) {
                state.cooldownUntilTick = Math.max(state.cooldownUntilTick, now + secondsToTicks(cfg.regionLeaveCooldown));
                if (state.pvpMode && !canQuitPvpMode(state, cfg, now) && SVFrameLib.inst().getFlags().isPvpAllowed(player.getServerWorld(), player.getBlockPos())) {
                    message(player, "§eLeft the PvP-mode region while still combat-locked for §f" + seconds(state.lastHitTick + secondsToTicks(cfg.combatTimeout) - now) + "s§e.");
                } else {
                    message(player, "§eLeft the PvP-mode region.");
                }
            }
        }
    }

    private static boolean canQuitPvpMode(State state, Settings cfg, long now) {
        return now > state.lastHitTick + secondsToTicks(cfg.combatTimeout);
    }

    private static void deny(PlayerAttackEvent event, ServerPlayerEntity player, String text) {
        event.setCancelled(true);
        if (event.getDamage().getDamage() > 0d) message(player, text);
    }

    private static boolean inPvpModeRegion(ServerPlayerEntity player) {
        return SVFrameLib.inst().getFlags().isFlagAllowed(player.getServerWorld(), player.getBlockPos(), CustomFlag.PVP_MODE);
    }

    private State state(ServerPlayerEntity player) {
        return states.computeIfAbsent(player.getUuid(), ignored -> new State());
    }

    private static void message(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message), false);
    }

    private static long secondsToTicks(double seconds) {
        return Math.max(0L, Math.round(seconds * 20d));
    }

    private static String seconds(long ticks) {
        return format(Math.max(0d, ticks / 20d));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new HashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Object first(Map<String, Object> map, String... keys) {
        for (String key : keys) if (map.containsKey(key)) return map.get(key);
        return null;
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static double decimal(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private static final class State {
        boolean pvpMode;
        boolean inPvpRegion;
        boolean regionInitialized;
        long lastHitTick;
        long cooldownUntilTick;
        long invulnerableUntilTick;
    }

    private record Settings(boolean enabled, int minLevel, int maxLevelDifference, double combatTimeout,
                            double invulnerabilityRegionChange, double invulnerabilityCommand,
                            boolean invulnerabilityCanDamage, boolean invulnerabilityApplyToPvpFlag,
                            double regionEnterCooldown, double regionLeaveCooldown, double combatCooldown,
                            double toggleOnCooldown, double toggleOffCooldown) {
        static Settings defaults() {
            return new Settings(false, 0, 10, 30d, 60d, 30d, false, true, 20d, 20d, 45d, 5d, 3d);
        }

        static Settings from(Map<String, Object> root) {
            Settings d = defaults();
            Map<String, Object> invulnerability = map(root.get("invulnerability"));
            Map<String, Object> invTime = map(invulnerability.get("time"));
            Map<String, Object> cooldown = map(root.get("cooldown"));
            return new Settings(
                    bool(root, "enabled", d.enabled),
                    integer(root, "min_level", integer(root, "min-level", d.minLevel)),
                    integer(root, "max_level_difference", integer(root, "max-level-difference", d.maxLevelDifference)),
                    decimal(root, "combat_timeout", decimal(root, "combat-timeout", d.combatTimeout)),
                    decimal(invTime, "region_change", decimal(invTime, "region-change", d.invulnerabilityRegionChange)),
                    decimal(invTime, "command", d.invulnerabilityCommand),
                    bool(invulnerability, "can_damage", bool(invulnerability, "can-damage", d.invulnerabilityCanDamage)),
                    bool(invulnerability, "apply_to_pvp_flag", bool(invulnerability, "apply-to-pvp-flag", d.invulnerabilityApplyToPvpFlag)),
                    decimal(cooldown, "region_enter", decimal(cooldown, "region-enter", d.regionEnterCooldown)),
                    decimal(cooldown, "region_leave", decimal(cooldown, "region-leave", d.regionLeaveCooldown)),
                    decimal(cooldown, "combat", d.combatCooldown),
                    decimal(cooldown, "toggle_on", decimal(cooldown, "toggle-on", d.toggleOnCooldown)),
                    decimal(cooldown, "toggle_off", decimal(cooldown, "toggle-off", d.toggleOffCooldown))
            );
        }
    }
}
