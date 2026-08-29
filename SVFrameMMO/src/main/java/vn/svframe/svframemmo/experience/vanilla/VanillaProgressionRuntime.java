package vn.svframe.svframemmo.experience.vanilla;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.experience.EXPSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Implements MMOCore death EXP loss, vanilla EXP redirection and RPG EXP-bar override. */
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
        if (settings.overrideVanilla) {
            sync(player);
            return true;
        }
        return false;
    }

    public boolean suppressDeathVanillaXp() {
        reloadSettings();
        return settings.overrideVanilla;
    }

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
        if (!settings.overrideVanilla) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sync(player);
    }

    private void sync(ServerPlayerEntity player) {
        var data = SVFrameMMO.playerData().get(player);
        int level = data.getLevel();
        long required = Math.max(1L, data.getLevelUpExperience());
        float progress = (float) Math.max(0d, Math.min(1d, data.getExperience() / required));
        player.experienceLevel = level;
        player.experienceProgress = progress;
        player.totalExperience = Math.max(0, level);
        if (player.networkHandler != null)
            player.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(progress, level, player.totalExperience));
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
            Map<String, Object> death = map(root.get("death-exp-loss"));
            settings = new Settings(
                    bool(redirect.get("enabled"), false),
                    Math.max(0d, number(redirect.get("ratio"), .8d)),
                    bool(root.get("override-vanilla-exp"), true),
                    bool(death.get("enabled"), false),
                    Math.max(0d, Math.min(100d, number(death.get("percent"), 30d)))
            );
        } catch (Exception exception) {
            LOG.log(Level.WARNING, "Could not reload vanilla EXP settings", exception);
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

    private record Settings(boolean redirectEnabled, double redirectRatio, boolean overrideVanilla,
                            boolean deathLossEnabled, double deathLossPercent) {
        static Settings defaults() { return new Settings(false, .8d, true, false, 30d); }
    }
}
