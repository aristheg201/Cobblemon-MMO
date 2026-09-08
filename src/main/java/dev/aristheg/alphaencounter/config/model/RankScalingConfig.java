package dev.aristheg.alphaencounter.config.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RankScalingConfig {
    public boolean enabled = false;
    public String fallbackProfile = "default";
    public Map<String, SpawnProfile> profiles = new LinkedHashMap<>();
    public List<ProfileResolver> resolvers = new ArrayList<>();

    public RankScalingConfig() {
        profiles.put("default", new SpawnProfile());
    }

    public void normalize() {
        if (fallbackProfile == null || fallbackProfile.isBlank()) fallbackProfile = "default";
        fallbackProfile = fallbackProfile.trim();

        if (profiles == null) profiles = new LinkedHashMap<>();
        Map<String, SpawnProfile> normalizedProfiles = new LinkedHashMap<>();
        for (Map.Entry<String, SpawnProfile> entry : profiles.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            String id = entry.getKey().trim();
            SpawnProfile profile = entry.getValue() == null ? new SpawnProfile() : entry.getValue();
            profile.normalize();
            normalizedProfiles.put(id, profile);
        }
        profiles = normalizedProfiles;
        profiles.computeIfAbsent(fallbackProfile, ignored -> new SpawnProfile());

        if (resolvers == null) resolvers = new ArrayList<>();
        List<ProfileResolver> normalizedResolvers = new ArrayList<>();
        for (ProfileResolver resolver : resolvers) {
            if (resolver == null) continue;
            resolver.normalize();
            if (resolver.permission.isBlank() || resolver.profile.isBlank()) continue;
            if (!profiles.containsKey(resolver.profile)) continue;
            normalizedResolvers.add(resolver);
        }
        resolvers = normalizedResolvers;
    }

    public SpawnProfile fallback() {
        SpawnProfile profile = profiles.get(fallbackProfile);
        return profile == null ? new SpawnProfile() : profile;
    }

    public SpawnProfile profile(String id) {
        if (id == null) return null;
        return profiles.get(id);
    }

    public static final class SpawnProfile {
        public double spawnChanceMultiplier = 1.0;

        public void normalize() {
            if (!Double.isFinite(spawnChanceMultiplier) || spawnChanceMultiplier < 0.0) spawnChanceMultiplier = 1.0;
        }
    }

    public static final class ProfileResolver {
        public String permission = "";
        public String profile = "";
        public int priority = 0;

        public void normalize() {
            if (permission == null) permission = "";
            if (profile == null) profile = "";
            permission = permission.trim();
            profile = profile.trim();
        }
    }
}
