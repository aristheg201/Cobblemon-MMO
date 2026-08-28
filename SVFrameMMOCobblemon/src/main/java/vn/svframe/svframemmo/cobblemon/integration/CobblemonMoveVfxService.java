package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
    private volatile Map<String, CobblemonActionEffectParser.MovePlan> plans = Map.of();
    private volatile Map<String, CobblemonActionEffectParser.MegaAccent> megaEffects = Map.of();
    private long packetBudgetTick = Long.MIN_VALUE;
    private int packetsThisTick;

    public synchronized void reload() {
        plans = loadPlans();
        megaEffects = loadMegaEffects();
        LOG.info("Loaded {} Cobblemon move VFX timeline(s) and {} Mega Showdown descriptor(s)", plans.size(), megaEffects.size());
    }

    public int planCount() { return plans.size(); }

    public void renderActor(ServerPlayerEntity caster, MoveTemplate move) {
        if (caster == null || move == null) return;
        String id = CobblemonMoveSkillAdapter.id(move.getName());
        CobblemonActionEffectParser.MovePlan plan = plans.get(id);
        if (plan != null) {
            scheduleParticles(caster, caster.getPos().add(0d, 1d, 0d), plan.actorParticles());
            scheduleAnimations(caster, plan.actorAnimations());
        } else caster.swingHand(Hand.MAIN_HAND, true);
        CobblemonActionEffectParser.MegaAccent accent = megaEffects.get(effectKey(id));
        if (accent != null) scheduleMega(caster, accent);
    }

    public void renderImpact(ServerPlayerEntity caster, MoveTemplate move, Vec3d position) {
        if (caster == null || move == null || position == null) return;
        CobblemonActionEffectParser.MovePlan plan = plans.get(CobblemonMoveSkillAdapter.id(move.getName()));
        if (plan != null) scheduleParticles(caster, position, plan.targetParticles());
    }

    private void scheduleParticles(ServerPlayerEntity caster, Vec3d position, List<CobblemonActionEffectParser.ParticleCue> cues) {
        for (int i = 0; i < cues.size(); i++) {
            CobblemonActionEffectParser.ParticleCue cue = cues.get(i);
            final int cueIndex = i;
            later(cue.delayTicks(), () -> emit(caster, position, cue.effectId(), cueIndex));
        }
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
        ServerWorld world = caster.getServerWorld();
        var cfg = SVFrameMMOCobblemon.config().vfx;
        double radius = Math.max(1d, Math.min(64d, cfg.moveBroadcastRadius));
        double radiusSq = radius * radius;
        List<ServerPlayerEntity> viewers = world.getPlayers().stream()
                .filter(viewer -> !viewer.isDisconnected() && viewer.squaredDistanceTo(position) <= radiusSq)
                .sorted(Comparator.comparingDouble(viewer -> viewer.squaredDistanceTo(position)))
                .limit(Math.max(1, Math.min(128, cfg.maxViewersPerEmission)))
                .toList();
        for (ServerPlayerEntity viewer : viewers) {
            if (viewer.squaredDistanceTo(position) > cfg.fullQualityDistance * cfg.fullQualityDistance && (cueIndex & 1) == 1) continue;
            if (!claimPacket()) break;
            new SpawnSnowstormParticlePacket(effectId, position).sendToPlayer(viewer);
        }
    }

    private synchronized boolean claimPacket() {
        long tick = SVFrameMMO.currentTick();
        if (tick != packetBudgetTick) { packetBudgetTick = tick; packetsThisTick = 0; }
        int limit = Math.max(1, SVFrameMMOCobblemon.config().vfx.maxSnowstormPacketsPerTick);
        if (packetsThisTick >= limit) return false;
        packetsThisTick++;
        return true;
    }

    private static Map<String, CobblemonActionEffectParser.MovePlan> loadPlans() {
        Optional<Path> directory = FabricLoader.getInstance().getModContainer("cobblemon")
                .flatMap(container -> container.findPath("data/cobblemon/action_effects/moves"));
        if (directory.isEmpty()) return Map.of();
        LinkedHashMap<String, CobblemonActionEffectParser.MovePlan> result = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(directory.get())) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().forEach(path -> {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    CobblemonActionEffectParser.MovePlan plan = CobblemonActionEffectParser.parseMove(JsonParser.parseReader(reader).getAsJsonObject());
                    if (!plan.empty()) {
                        String file = path.getFileName().toString();
                        result.put(CobblemonMoveSkillAdapter.id(file.substring(0, file.length() - 5)), plan);
                    }
                } catch (Exception error) { LOG.debug("Skipping invalid move VFX {}", path, error); }
            });
        } catch (Exception error) { LOG.error("Cannot read Cobblemon move action effects", error); }
        return Map.copyOf(result);
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
}
