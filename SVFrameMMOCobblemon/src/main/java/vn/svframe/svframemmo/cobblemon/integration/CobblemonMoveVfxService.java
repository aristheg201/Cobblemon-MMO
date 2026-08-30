package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import com.cobblemon.mod.common.net.messages.client.sound.UnvalidatedPlaySoundS2CPacket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Replays Cobblemon's real move action-effect timelines and optional Mega Showdown Snowstorm accents. */
public final class CobblemonMoveVfxService {
    private static final Logger LOG = LoggerFactory.getLogger("SVFrameMMO/Cobblemon-Move-VFX");
    private volatile Map<String, CobblemonActionEffectParser.MovePlan> specificPlans = Map.of();
    private volatile Map<String, CobblemonActionEffectParser.MovePlan> genericProfiles = Map.of();
    private volatile CobblemonActionEffectParser.MovePlan genericMove;
    private volatile Map<String, CobblemonActionEffectParser.MegaAccent> megaEffects = Map.of();
    private long packetBudgetTick = Long.MIN_VALUE;
    private int packetsThisTick;

    public synchronized void reload() {
        LoadedPlans loaded = loadPlans();
        specificPlans = loaded.specific();
        genericProfiles = loaded.genericProfiles();
        genericMove = loaded.genericMove();
        megaEffects = loadMegaEffects();
        LOG.info("Loaded Cobblemon move presentation provider; specific={}, genericProfiles={}, genericMove={}, MegaShowdown={}",
                specificPlans.size(), genericProfiles.size(), genericMove != null, megaEffects.size());
    }

    public int planCount() { return specificPlans.size(); }
    public int genericPlanCount() { return genericProfiles.size() + (genericMove == null ? 0 : 1); }
    public boolean hasSpecificPlan(String moveId) {
        if (moveId == null) return false;
        return specificPlans.containsKey(CobblemonMoveSkillAdapter.id(moveId));
    }
    public boolean hasGenericPlan() { return genericMove != null || !genericProfiles.isEmpty(); }

    public String presentationSource(MoveTemplate move) {
        if (move == null) return "none";
        String id = CobblemonMoveSkillAdapter.id(move.getName());
        if (specificPlans.containsKey(id)) return "cobblemon:action_effects/moves/" + id;
        String profile = genericProfileKey(move);
        if (genericProfiles.containsKey(profile)) return "cobblemon:action_effects/moves/generic/" + profile;
        return genericMove == null ? "none" : "cobblemon:action_effects/moves/generic_move";
    }

    public void renderActor(ServerPlayerEntity caster, MoveTemplate move) {
        if (caster == null || move == null) return;
        CobblemonActionEffectParser.MovePlan plan = planFor(move);
        Vec3d actorPosition = caster.getPos().add(0d, 1d, 0d);
        if (plan != null) {
            scheduleParticles(caster, actorPosition, plan.actorParticles());
            scheduleSounds(caster, actorPosition, plan.actorSounds());
            scheduleAnimations(caster, plan.actorAnimations());
        } else caster.swingHand(Hand.MAIN_HAND, true);
        CobblemonActionEffectParser.MegaAccent accent = megaEffects.get(effectKey(CobblemonMoveSkillAdapter.id(move.getName())));
        if (accent != null) scheduleMega(caster, accent);
    }

    public void renderImpact(ServerPlayerEntity caster, MoveTemplate move, Vec3d position) {
        if (caster == null || move == null || position == null) return;
        String id = CobblemonMoveSkillAdapter.id(move.getName());
        CobblemonActionEffectParser.MovePlan specific = specificPlans.get(id);
        if (specific != null) {
            scheduleParticles(caster, position, specific.targetParticles());
            scheduleSounds(caster, position, specific.targetSounds());
            return;
        }

        CobblemonActionEffectParser.MovePlan fallback = fallbackPlan(move);
        if (fallback != null) {
            scheduleParticles(caster, position, fallback.targetParticles());
            scheduleSounds(caster, position, fallback.targetSounds());
        }

        // generic_move.json builds these IDs dynamically from q.move.type. Dynamic Molang strings cannot be
        // represented by Identifier until the move is known, so resolve them here from the live Cobblemon template.
        if (fallback == genericMove && move.getPower() > 0d) {
            String type = move.getElementalType().getName().toLowerCase(Locale.ROOT);
            Identifier impact = Identifier.tryParse("cobblemon:impact_" + type);
            Identifier hit = Identifier.tryParse("cobblemon:hit");
            Identifier sound = Identifier.tryParse("cobblemon:impact." + type);
            if (impact != null) emit(caster, position, impact, 0);
            if (hit != null) emit(caster, position, hit, 1);
            if (sound != null) emitSound(caster, position, sound);
        }
    }

    private CobblemonActionEffectParser.MovePlan planFor(MoveTemplate move) {
        CobblemonActionEffectParser.MovePlan specific = specificPlans.get(CobblemonMoveSkillAdapter.id(move.getName()));
        return specific != null ? specific : fallbackPlan(move);
    }

    private CobblemonActionEffectParser.MovePlan fallbackPlan(MoveTemplate move) {
        CobblemonActionEffectParser.MovePlan profile = genericProfiles.get(genericProfileKey(move));
        return profile != null ? profile : genericMove;
    }

    private static String genericProfileKey(MoveTemplate move) {
        return move.getDamageCategory().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") + "_"
                + move.getElementalType().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void scheduleParticles(ServerPlayerEntity caster, Vec3d position, List<CobblemonActionEffectParser.ParticleCue> cues) {
        for (int i = 0; i < cues.size(); i++) {
            CobblemonActionEffectParser.ParticleCue cue = cues.get(i);
            final int cueIndex = i;
            later(cue.delayTicks(), () -> emit(caster, position, cue.effectId(), cueIndex));
        }
    }

    private void scheduleSounds(ServerPlayerEntity caster, Vec3d position, List<CobblemonActionEffectParser.SoundCue> cues) {
        for (CobblemonActionEffectParser.SoundCue cue : cues)
            later(cue.delayTicks(), () -> emitSound(caster, position, cue.soundId()));
    }

    private void scheduleAnimations(ServerPlayerEntity caster, List<CobblemonActionEffectParser.AnimationCue> cues) {
        if (cues.isEmpty()) { caster.swingHand(Hand.MAIN_HAND, true); return; }
        for (CobblemonActionEffectParser.AnimationCue cue : cues)
            later(cue.delayTicks(), () -> { if (caster.isAlive() && !caster.isDisconnected()) caster.swingHand(Hand.MAIN_HAND, true); });
    }

    private void scheduleMega(ServerPlayerEntity caster, CobblemonActionEffectParser.MegaAccent accent) {
        Vec3d position = caster.getPos().add(0d, 1d, 0d);
        later(accent.particleDelayTicks(), () -> emit(caster, position, accent.effectId(), 0));
        later(accent.animationDelayTicks(), () -> { if (caster.isAlive() && !caster.isDisconnected()) caster.swingHand(Hand.MAIN_HAND, true); });
    }

    private static void later(int delay, Runnable action) {
        if (delay <= 0) action.run();
        else SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + delay, action);
    }

    private void emit(ServerPlayerEntity caster, Vec3d position, Identifier effectId, int cueIndex) {
        if (!caster.isAlive() || caster.isDisconnected()) return;
        List<ServerPlayerEntity> viewers = viewers(caster, position);
        var cfg = SVFrameMMOCobblemon.config().vfx;
        for (ServerPlayerEntity viewer : viewers) {
            if (viewer.squaredDistanceTo(position) > cfg.fullQualityDistance * cfg.fullQualityDistance && (cueIndex & 1) == 1) continue;
            if (!claimPacket()) break;
            new SpawnSnowstormParticlePacket(effectId, position).sendToPlayer(viewer);
        }
    }

    private void emitSound(ServerPlayerEntity caster, Vec3d position, Identifier soundId) {
        if (!caster.isAlive() || caster.isDisconnected()) return;
        for (ServerPlayerEntity viewer : viewers(caster, position)) {
            if (!claimPacket()) break;
            new UnvalidatedPlaySoundS2CPacket(soundId, SoundCategory.PLAYERS,
                    position.x, position.y, position.z, 1.0f, 1.0f).sendToPlayer(viewer);
        }
    }

    private static List<ServerPlayerEntity> viewers(ServerPlayerEntity caster, Vec3d position) {
        ServerWorld world = caster.getServerWorld();
        var cfg = SVFrameMMOCobblemon.config().vfx;
        double radius = Math.max(1d, Math.min(64d, cfg.moveBroadcastRadius));
        double radiusSq = radius * radius;
        return world.getPlayers().stream()
                .filter(viewer -> !viewer.isDisconnected() && viewer.squaredDistanceTo(position) <= radiusSq)
                .sorted(Comparator.comparingDouble(viewer -> viewer.squaredDistanceTo(position)))
                .limit(Math.max(1, Math.min(128, cfg.maxViewersPerEmission)))
                .toList();
    }

    private synchronized boolean claimPacket() {
        long tick = SVFrameMMO.currentTick();
        if (tick != packetBudgetTick) { packetBudgetTick = tick; packetsThisTick = 0; }
        int limit = Math.max(1, SVFrameMMOCobblemon.config().vfx.maxSnowstormPacketsPerTick);
        if (packetsThisTick >= limit) return false;
        packetsThisTick++;
        return true;
    }

    private static LoadedPlans loadPlans() {
        Optional<Path> directory = FabricLoader.getInstance().getModContainer("cobblemon")
                .flatMap(container -> container.findPath("data/cobblemon/action_effects/moves"));
        if (directory.isEmpty()) return new LoadedPlans(Map.of(), Map.of(), null);
        LinkedHashMap<String, CobblemonActionEffectParser.MovePlan> specific = new LinkedHashMap<>();
        LinkedHashMap<String, CobblemonActionEffectParser.MovePlan> generic = new LinkedHashMap<>();
        final CobblemonActionEffectParser.MovePlan[] genericMove = { null };
        try (Stream<Path> files = Files.walk(directory.get())) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().forEach(path -> {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    CobblemonActionEffectParser.MovePlan plan = CobblemonActionEffectParser.parseMove(JsonParser.parseReader(reader).getAsJsonObject());
                    if (plan.empty()) return;
                    String relative = directory.get().relativize(path).toString().replace('\\', '/');
                    String file = path.getFileName().toString();
                    String base = file.substring(0, file.length() - 5);
                    if ("generic_move.json".equals(relative)) {
                        genericMove[0] = plan;
                    } else if (relative.startsWith("generic/")) {
                        generic.put(base.toLowerCase(Locale.ROOT), plan);
                    } else {
                        specific.put(CobblemonMoveSkillAdapter.id(base), plan);
                    }
                } catch (Exception error) { LOG.debug("Skipping invalid move VFX {}", path, error); }
            });
        } catch (Exception error) { LOG.error("Cannot read Cobblemon move action effects", error); }
        return new LoadedPlans(Map.copyOf(specific), Map.copyOf(generic), genericMove[0]);
    }

    private static Map<String, CobblemonActionEffectParser.MegaAccent> loadMegaEffects() {
        Optional<Path> directory = FabricLoader.getInstance().getModContainer("mega_showdown")
                .flatMap(container -> container.findPath("data/mega_showdown/mega_showdown/effect"));
        if (directory.isEmpty()) return Map.of();
        LinkedHashMap<String, CobblemonActionEffectParser.MegaAccent> result = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(directory.get(), 1)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().forEach(path -> {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    CobblemonActionEffectParser.parseMegaAccent(root).ifPresent(accent -> {
                        String file = path.getFileName().toString();
                        result.put(effectKey(file.substring(0, file.length() - 5)), accent);
                    });
                } catch (Exception error) { LOG.debug("Skipping invalid Mega Showdown VFX {}", path, error); }
            });
        } catch (Exception error) { LOG.error("Cannot read Mega Showdown effect descriptors", error); }
        return Map.copyOf(result);
    }

    private static String effectKey(String value) {
        if (value == null) return "";
        String lower = value.trim().toLowerCase(Locale.ROOT);
        int namespace = lower.indexOf(':');
        if (namespace >= 0) lower = lower.substring(namespace + 1);
        return lower.replaceAll("[^a-z0-9_.-]", "");
    }

    private record LoadedPlans(Map<String, CobblemonActionEffectParser.MovePlan> specific,
                               Map<String, CobblemonActionEffectParser.MovePlan> genericProfiles,
                               CobblemonActionEffectParser.MovePlan genericMove) { }
}
