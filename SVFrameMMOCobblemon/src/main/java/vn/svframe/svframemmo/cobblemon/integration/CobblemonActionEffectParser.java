package vn.svframe.svframemmo.cobblemon.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Pure parser for Cobblemon action-effect timelines and optional Mega Showdown descriptors. */
final class CobblemonActionEffectParser {
    private static final int MAX_PARTICLE_CUES_PER_PHASE = 16;
    private static final int MAX_ANIMATION_CUES = 8;
    // Preserve real move timing while still bounding malicious/resource-pack supplied descriptors.
    private static final int MAX_TIMELINE_TICKS = 600;

    private CobblemonActionEffectParser() {}

    static MovePlan parseMove(JsonObject root) {
        JsonArray timeline = root.has("timeline") && root.get("timeline").isJsonArray() ? root.getAsJsonArray("timeline") : new JsonArray();
        List<RawParticleCue> particles = new ArrayList<>();
        List<AnimationCue> animations = new ArrayList<>();
        parseTimeline(timeline, 0, particles, animations);
        List<ParticleCue> actor = normalizeParticles(particles.stream().filter(cue -> !cue.target()).toList(), false);
        List<ParticleCue> target = normalizeParticles(particles.stream().filter(RawParticleCue::target).toList(), true);
        List<AnimationCue> actorAnimations = normalizeAnimations(animations.stream().filter(cue -> !cue.target()).toList());
        return new MovePlan(actor, target, actorAnimations);
    }

    static Optional<MegaAccent> parseMegaAccent(JsonObject root) {
        // Legacy/alternate descriptor shape.
        if (root.has("snowstorm") && root.get("snowstorm").isJsonObject()) {
            JsonObject snowstorm = root.getAsJsonObject("snowstorm");
            Identifier particle = Identifier.tryParse(string(snowstorm, "particle_apply"));
            if (particle != null) {
                int particleDelay = boundedTicks(number(snowstorm, "apply_after", 0));
                int animationDelay = particleDelay;
                if (snowstorm.has("animations") && snowstorm.get("animations").isJsonObject()) {
                    animationDelay = boundedTicks(number(snowstorm.getAsJsonObject("animations"), "apply_delay", 0));
                }
                return Optional.of(new MegaAccent(particle, particleDelay, animationDelay));
            }
        }

        // Mega Showdown move descriptors (for example mega_showdown:z_move): outer apply_after is
        // followed by an action_effect containing the Snowstorm particle and optional animation delay.
        JsonObject effect = root.has("action_effect") && root.get("action_effect").isJsonObject()
                ? root.getAsJsonObject("action_effect") : null;
        if (effect == null) return Optional.empty();
        JsonObject particleObject = effect.has("particle") && effect.get("particle").isJsonObject()
                ? effect.getAsJsonObject("particle") : null;
        Identifier particle = particleObject == null ? null : Identifier.tryParse(string(particleObject, "particle"));
        if (particle == null) return Optional.empty();
        int outer = boundedTicks(number(root, "apply_after", 0));
        int animationDelay = outer;
        if (effect.has("animation") && effect.get("animation").isJsonObject()) {
            animationDelay = boundedSum(outer, boundedTicks(number(effect.getAsJsonObject("animation"), "apply_delay", 0)));
        }
        return Optional.of(new MegaAccent(particle, outer, animationDelay));
    }

    private static int parseTimeline(JsonArray timeline, int cursor, List<RawParticleCue> particles, List<AnimationCue> animations) {
        int current = cursor;
        for (JsonElement element : timeline) {
            if (!element.isJsonObject()) continue;
            JsonObject step = element.getAsJsonObject();
            String type = string(step, "type");
            if ("pause".equals(type)) {
                current = boundedSum(current, boundedTicks(number(step, "pause", 0)));
                continue;
            }
            if ("sequence".equals(type) && step.has("keyframes") && step.get("keyframes").isJsonArray()) {
                current = parseTimeline(step.getAsJsonArray("keyframes"), current, particles, animations);
                continue;
            }
            int at = boundedSum(current, boundedTicks(number(step, "delay", 0)));
            if ("entity_particles".equals(type)) {
                Identifier effect = Identifier.tryParse(string(step, "effect"));
                if (effect != null) particles.add(new RawParticleCue(effect, at, isTargetCue(step, effect)));
            } else if ("animation".equals(type)) {
                List<String> names = strings(step, "animation");
                if (!names.isEmpty()) animations.add(new AnimationCue(names, at, isTargetCue(step, null)));
            }
        }
        return current;
    }

    private static List<ParticleCue> normalizeParticles(List<RawParticleCue> raw, boolean normalizeToImpact) {
        if (raw.isEmpty()) return List.of();
        int first = normalizeToImpact ? raw.stream().mapToInt(RawParticleCue::delayTicks).min().orElse(0) : 0;
        List<ParticleCue> result = new ArrayList<>();
        for (RawParticleCue cue : raw) {
            if (result.size() >= MAX_PARTICLE_CUES_PER_PHASE) break;
            ParticleCue normalized = new ParticleCue(cue.effectId(), Math.max(0, Math.min(MAX_TIMELINE_TICKS, cue.delayTicks() - first)));
            if (!result.contains(normalized)) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static List<AnimationCue> normalizeAnimations(List<AnimationCue> raw) {
        if (raw.isEmpty()) return List.of();
        List<AnimationCue> result = new ArrayList<>();
        for (AnimationCue cue : raw) {
            if (result.size() >= MAX_ANIMATION_CUES) break;
            AnimationCue normalized = new AnimationCue(cue.names(), Math.min(MAX_TIMELINE_TICKS, cue.delayTicks()), false);
            if (!result.contains(normalized)) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static boolean isTargetCue(JsonObject step, Identifier effect) {
        String condition = string(step, "entityCondition").toLowerCase(Locale.ROOT);
        if (condition.contains("is_user == false")) return true;
        if (condition.contains("is_user == true")) return false;
        if (effect == null) return false;
        String path = effect.getPath().toLowerCase(Locale.ROOT);
        return path.contains("_target") || path.startsWith("target") || path.contains("target_");
    }

    private static List<String> strings(JsonObject object, String key) {
        if (!object.has(key)) return List.of();
        JsonElement value = object.get(key);
        List<String> result = new ArrayList<>();
        if (value.isJsonArray()) for (JsonElement entry : value.getAsJsonArray()) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) result.add(entry.getAsString());
        }
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) result.add(value.getAsString());
        return List.copyOf(result);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static double number(JsonObject object, String key, double fallback) {
        try { return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int boundedTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return 0;
        return Math.min(MAX_TIMELINE_TICKS, Math.max(0, (int) Math.round(seconds * 20.0)));
    }

    private static int boundedSum(int a, int b) {
        long value = (long) a + b;
        return (int) Math.min(MAX_TIMELINE_TICKS, Math.max(0L, value));
    }

    record ParticleCue(Identifier effectId, int delayTicks) {}
    record AnimationCue(List<String> names, int delayTicks, boolean target) {}
    record MovePlan(List<ParticleCue> actorParticles, List<ParticleCue> targetParticles, List<AnimationCue> actorAnimations) {
        boolean empty() { return actorParticles.isEmpty() && targetParticles.isEmpty() && actorAnimations.isEmpty(); }
    }
    record MegaAccent(Identifier effectId, int particleDelayTicks, int animationDelayTicks) {}
    private record RawParticleCue(Identifier effectId, int delayTicks, boolean target) {}
}
