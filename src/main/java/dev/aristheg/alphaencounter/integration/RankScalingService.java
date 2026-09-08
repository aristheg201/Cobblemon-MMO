package dev.aristheg.alphaencounter.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.config.model.RankScalingConfig;
import dev.aristheg.alphaencounter.config.model.RankScalingConfig.ProfileResolver;
import dev.aristheg.alphaencounter.config.model.RankScalingConfig.SpawnProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RankScalingService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final RankScalingService INSTANCE = new RankScalingService();

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("alpha-encounter/rank-scaling.json");
    private volatile RankScalingConfig config = new RankScalingConfig();
    private PermissionSource permissionSource = PermissionSource.unavailable("not-probed");
    private boolean providerAttempted;
    private boolean warnedUnavailable;

    private RankScalingService() {
        config.normalize();
    }

    public static RankScalingService instance() {
        return INSTANCE;
    }

    public synchronized void reload() {
        RankScalingConfig loaded = new RankScalingConfig();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                loaded.normalize();
                Files.writeString(file, GSON.toJson(loaded));
            } else {
                RankScalingConfig parsed = GSON.fromJson(Files.readString(file), RankScalingConfig.class);
                if (parsed != null) loaded = parsed;
            }
            loaded.normalize();
            config = loaded;
            providerAttempted = false;
            permissionSource = PermissionSource.unavailable("not-probed");
            warnedUnavailable = false;
            AlphaEncounterMod.LOGGER.info(
                "Loaded rank scaling config: enabled={} profiles={} resolvers={} fallback='{}'.",
                loaded.enabled, loaded.profiles.size(), loaded.resolvers.size(), loaded.fallbackProfile
            );
        } catch (Exception e) {
            loaded.normalize();
            loaded.enabled = false;
            config = loaded;
            providerAttempted = false;
            permissionSource = PermissionSource.unavailable("config-load-failed");
            warnedUnavailable = false;
            AlphaEncounterMod.LOGGER.error("Failed to load rank scaling config {}; rank scaling is disabled for safety.", file, e);
        }
    }

    public double spawnChanceMultiplier(ServerPlayerEntity player) {
        try {
            return resolve(player).spawnChanceMultiplier();
        } catch (Throwable t) {
            AlphaEncounterMod.LOGGER.error("Rank scaling resolution failed for {}; using neutral spawn multiplier.", player == null ? "<null>" : player.getName().getString(), t);
            return 1.0;
        }
    }

    public Resolution resolve(ServerPlayerEntity player) {
        RankScalingConfig snapshot = config;
        if (!snapshot.enabled) {
            return new Resolution(false, "disabled", false, snapshot.fallbackProfile, "", Integer.MIN_VALUE, 1.0);
        }

        SpawnProfile fallback = snapshot.fallback();
        if (snapshot.resolvers.isEmpty()) {
            return new Resolution(true, "not-needed", false, snapshot.fallbackProfile, "", Integer.MIN_VALUE, fallback.spawnChanceMultiplier);
        }

        PermissionSource source = permissionSource();
        if (!source.available()) {
            if (!warnedUnavailable) {
                warnedUnavailable = true;
                AlphaEncounterMod.LOGGER.warn(
                    "Rank scaling is enabled but LuckPerms is unavailable ({}); using fallback profile '{}'.",
                    source.status(), snapshot.fallbackProfile
                );
            }
            return new Resolution(true, source.status(), false, snapshot.fallbackProfile, "", Integer.MIN_VALUE, fallback.spawnChanceMultiplier);
        }

        ProfileResolver winner = null;
        for (ProfileResolver resolver : snapshot.resolvers) {
            if (!source.hasPermission(player, resolver.permission)) continue;
            if (winner == null || resolver.priority > winner.priority) winner = resolver;
        }

        if (winner == null) {
            return new Resolution(true, source.status(), true, snapshot.fallbackProfile, "", Integer.MIN_VALUE, fallback.spawnChanceMultiplier);
        }

        SpawnProfile profile = snapshot.profile(winner.profile);
        if (profile == null) profile = fallback;
        return new Resolution(true, source.status(), true, winner.profile, winner.permission, winner.priority, profile.spawnChanceMultiplier);
    }

    public String debugLine(ServerPlayerEntity player) {
        Resolution resolved = resolve(player);
        String matched = resolved.matchedPermission().isBlank()
            ? "fallback"
            : resolved.matchedPermission() + "@" + resolved.priority();
        return "RankScaling player=" + player.getName().getString()
            + " enabled=" + resolved.enabled()
            + " provider=" + resolved.providerStatus()
            + " providerAvailable=" + resolved.providerAvailable()
            + " profile=" + resolved.profileId()
            + " matched=" + matched
            + " spawnChance=x" + formatMultiplier(resolved.spawnChanceMultiplier());
    }

    private synchronized PermissionSource permissionSource() {
        if (providerAttempted) return permissionSource;
        providerAttempted = true;
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider", false, RankScalingService.class.getClassLoader());
            Class<?> implementation = Class.forName("dev.aristheg.alphaencounter.integration.LuckPermsPermissionSource");
            permissionSource = (PermissionSource) implementation.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            permissionSource = PermissionSource.unavailable("luckperms-not-installed");
        } catch (Throwable t) {
            permissionSource = PermissionSource.unavailable("luckperms-provider-unavailable");
            AlphaEncounterMod.LOGGER.warn("LuckPerms API was found but its provider could not be obtained; fallback spawn profile will be used when rank scaling is enabled.", t);
        }
        return permissionSource;
    }

    private String formatMultiplier(double value) {
        if (value == Math.rint(value)) return String.format(java.util.Locale.ROOT, "%.1f", value);
        return String.format(java.util.Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public record Resolution(
        boolean enabled,
        String providerStatus,
        boolean providerAvailable,
        String profileId,
        String matchedPermission,
        int priority,
        double spawnChanceMultiplier
    ) {}
}
