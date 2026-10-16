package vn.svframe.svframemmo.experience.vanilla;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.experience.EXPSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** RPG EXP mirroring and death loss. Presentation never owns Minecraft's spendable EXP. */
public final class VanillaProgressionRuntime {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-VanillaEXP");
    private static final VanillaProgressionRuntime INSTANCE = new VanillaProgressionRuntime();

    private Settings settings = Settings.defaults();
    private long configStamp = Long.MIN_VALUE;

    private VanillaProgressionRuntime() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) onDeath(player);
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    public static VanillaProgressionRuntime instance() { return INSTANCE; }

    public boolean onVanillaExperience(ServerPlayerEntity player, int amount) {
        if (player == null || amount <= 0) return false;
        reloadSettings();
        if (settings.redirectEnabled)
            SVFrameMMO.playerData().get(player).giveExperience(amount * settings.redirectRatio, EXPSource.VANILLA);
        // addExperience must always complete, including with the MMO HUD selected.
        // Enchanting, anvils, mending and death drops use the native fields.
        return false;
    }

    public boolean suppressDeathVanillaXp() {
        return false;
    }

    /** Semantic HUD preference; only the presentation client uses this value. */
    public String experienceDisplayMode() { return settings.displayMode.name(); }

    private void onDeath(ServerPlayerEntity player) {
        reloadSettings();
        if (!settings.deathLossEnabled) return;
        var data = SVFrameMMO.playerData().get(player);
        int loss = (int) (data.getExperience() * settings.deathLossPercent / 100d);
        if (loss > 0) data.setExperience(data.getExperience() - loss);
    }

    private void tick(MinecraftServer server) {
        if ((SVFrameMMO.currentTick() & 7L) != 0L) return;
        reloadSettings();
    }

    private synchronized void reloadSettings() {
        Path file = DefaultFiles.ROOT.resolve("config.yml");
        long stamp;
        try { stamp = Files.getLastModifiedTime(file).toMillis(); }
        catch (Exception ignored) { stamp = -1L; }
        if (stamp == configStamp) return;
        configStamp = stamp;
        try {
            Map<String, Object> root = map(YamlLite.parse(file));
            Map<String, Object> redirect = map(root.get("vanilla-exp-redirection"));
            Map<String, Object> display = map(root.get("vanilla-exp-display"));
            Map<String, Object> death = map(root.get("death-exp-loss"));
            if (display.isEmpty() && bool(root.get("override-vanilla-exp"), false)) {
                LOG.warning("Ignoring legacy override-vanilla-exp=true; vanilla-exp-display.mode only selects client HUD presentation.");
            }
            settings = new Settings(
                    bool(redirect.get("enabled"), false),
                    Math.max(0d, number(redirect.get("ratio"), .8d)),
                    DisplayMode.parse(display.get("mode")),
                    bool(death.get("enabled"), false),
                    Math.max(0d, Math.min(100d, number(death.get("percent"), 30d)))
            );
        } catch (Exception exception) {
            LOG.log(Level.WARNING, "Could not reload vanilla EXP settings", exception);
            settings = Settings.defaults();
        }
    }

    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
    private static boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }
    private static double number(Object value, double fallback) {
        try { return value instanceof Number n ? n.doubleValue() : value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private enum DisplayMode {
        VANILLA,
        MMO;

        static DisplayMode parse(Object raw) {
            if (raw == null) return VANILLA;
            try { return valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return VANILLA; }
        }
    }

    private record Settings(boolean redirectEnabled, double redirectRatio, DisplayMode displayMode,
                            boolean deathLossEnabled, double deathLossPercent) {
        static Settings defaults() { return new Settings(false, .8d, DisplayMode.VANILLA, false, 30d); }
    }
}
