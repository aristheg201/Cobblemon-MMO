package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import com.cobblemon.mod.common.net.messages.client.sound.UnvalidatedPlaySoundS2CPacket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;

public final class CobblemonMoveVfxService {
   private static final Logger LOG = LoggerFactory.getLogger("SVFrameMMO/Cobblemon-Move-VFX");
   private volatile Map<String, CobblemonActionEffectParser.MovePlan> specificPlans = Map.of();
   private volatile Map<String, CobblemonActionEffectParser.MovePlan> genericProfiles = Map.of();
   private volatile CobblemonActionEffectParser.MovePlan genericMove;
   private volatile Map<String, CobblemonActionEffectParser.MegaAccent> megaEffects = Map.of();
   private long packetBudgetTick = Long.MIN_VALUE;
   private int packetsThisTick;

   public synchronized void reload() {
      CobblemonMoveVfxService.LoadedPlans loaded = loadPlans();
      this.specificPlans = loaded.specific();
      this.genericProfiles = loaded.genericProfiles();
      this.genericMove = loaded.genericMove();
      this.megaEffects = loadMegaEffects();
      LOG.info(
         "Loaded Cobblemon move presentation provider; specific={}, genericProfiles={}, genericMove={}, MegaShowdown={}",
         new Object[]{this.specificPlans.size(), this.genericProfiles.size(), this.genericMove != null, this.megaEffects.size()}
      );
   }

   public int planCount() {
      return this.specificPlans.size();
   }

   public int genericPlanCount() {
      return this.genericProfiles.size() + (this.genericMove == null ? 0 : 1);
   }

   public boolean hasSpecificPlan(String moveId) {
      return moveId == null ? false : this.specificPlans.containsKey(CobblemonMoveSkillAdapter.id(moveId));
   }

   public boolean hasGenericPlan() {
      return this.genericMove != null || !this.genericProfiles.isEmpty();
   }

   public String presentationSource(MoveTemplate move) {
      if (move == null) {
         return "none";
      } else {
         String id = CobblemonMoveSkillAdapter.id(move.getName());
         if (this.specificPlans.containsKey(id)) {
            return "cobblemon:action_effects/moves/" + id;
         } else {
            String profile = genericProfileKey(move);
            if (this.genericProfiles.containsKey(profile)) {
               return "cobblemon:action_effects/moves/generic/" + profile;
            } else {
               return this.genericMove == null ? "none" : "cobblemon:action_effects/moves/generic_move";
            }
         }
      }
   }

   public void renderActor(ServerPlayerEntity caster, MoveTemplate move) {
      if (caster != null && move != null) {
         CobblemonActionEffectParser.MovePlan plan = this.planFor(move);
         Vec3d actorPosition = caster.getPos().add(0.0, 1.0, 0.0);
         if (plan != null) {
            this.scheduleParticles(caster, actorPosition, plan.actorParticles());
            this.scheduleSounds(caster, actorPosition, plan.actorSounds());
            this.scheduleAnimations(caster, plan.actorAnimations());
         } else {
            caster.swingHand(Hand.MAIN_HAND, true);
         }

         CobblemonActionEffectParser.MegaAccent accent = this.megaEffects.get(effectKey(CobblemonMoveSkillAdapter.id(move.getName())));
         if (accent != null) {
            this.scheduleMega(caster, accent);
         }
      }
   }

   /** Place billboard effects on the visible target surface, rather than inside its opaque model. */
   public void renderImpact(ServerPlayerEntity caster, MoveTemplate move, LivingEntity target) {
      if (caster == null || target == null) return;
      Vec3d center = target.getBoundingBox().getCenter();
      Vec3d direction = caster.getEyePos().subtract(center).normalize();
      double radius = Math.max(target.getBoundingBox().getLengthX(), target.getBoundingBox().getLengthZ()) * 0.5;
      renderImpact(caster, move, center.add(direction.multiply(radius + 0.25)));
   }

   public void renderImpact(ServerPlayerEntity caster, MoveTemplate move, Vec3d position) {
      if (caster != null && move != null && position != null) {
         String id = CobblemonMoveSkillAdapter.id(move.getName());
         if (id.equals("bite") || id.equals("lick")) {
            this.renderSignatureImpact(caster, id, position);
         }

         CobblemonActionEffectParser.MovePlan specific = this.specificPlans.get(id);
         if (specific != null) {
            this.scheduleParticles(caster, position, specific.targetParticles());
            this.scheduleSounds(caster, position, specific.targetSounds());
         } else {
            CobblemonActionEffectParser.MovePlan fallback = this.fallbackPlan(move);
            if (fallback != null) {
               this.scheduleParticles(caster, position, fallback.targetParticles());
               this.scheduleSounds(caster, position, fallback.targetSounds());
            }

            if (fallback == this.genericMove && move.getPower() > 0.0) {
               String type = move.getElementalType().getName().toLowerCase(Locale.ROOT);
               Identifier impact = Identifier.tryParse("cobblemon:impact_" + type);
               Identifier hit = Identifier.tryParse("cobblemon:hit");
               Identifier sound = Identifier.tryParse("cobblemon:impact." + type);
               if (impact != null) {
                  this.emit(caster, position, impact, 0);
               }

               if (hit != null) {
                  this.emit(caster, position, hit, 1);
               }

               if (sound != null) {
                  this.emitSound(caster, position, sound);
               }
            }
         }
      }
   }

   private void renderSignatureImpact(ServerPlayerEntity caster, String id, Vec3d target) {
      boolean lick = id.equals("lick");
      DustParticleEffect dust = new DustParticleEffect(new Vector3f(lick ? 1.0F : 0.95F, lick ? 0.32F : 0.87F, lick ? 0.65F : 0.66F), lick ? 0.7F : 0.9F);
      Vec3d start = caster.getEyePos().add(0.0, -0.15, 0.0);

      for (int i = 0; i < 16 && this.claimPacket(); i++) {
         double fraction = i / 15.0;
         Vec3d position = lick
            ? start.lerp(target, fraction).add(0.0, -0.18 * Math.sin(Math.PI * fraction), 0.0)
            : target.add(Math.cos(fraction * Math.PI * 2.0) * 0.35, Math.sin(fraction * Math.PI * 2.0) * 0.24, 0.0);
         caster.getServerWorld().spawnParticles(dust, position.x, position.y, position.z, 1, 0.0, 0.0, 0.0, 0.0);
      }

      caster.getServerWorld()
         .playSound(
            null,
            target.x,
            target.y,
            target.z,
            lick ? SoundEvents.ENTITY_SLIME_ATTACK : SoundEvents.ENTITY_FOX_BITE,
            SoundCategory.PLAYERS,
            0.6F,
            lick ? 1.2F : 0.9F
         );
   }

   private CobblemonActionEffectParser.MovePlan planFor(MoveTemplate move) {
      CobblemonActionEffectParser.MovePlan specific = this.specificPlans.get(CobblemonMoveSkillAdapter.id(move.getName()));
      return specific != null ? specific : this.fallbackPlan(move);
   }

   private CobblemonActionEffectParser.MovePlan fallbackPlan(MoveTemplate move) {
      CobblemonActionEffectParser.MovePlan profile = this.genericProfiles.get(genericProfileKey(move));
      return profile != null ? profile : this.genericMove;
   }

   private static String genericProfileKey(MoveTemplate move) {
      return move.getDamageCategory().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")
         + "_"
         + move.getElementalType().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }

   private void scheduleParticles(ServerPlayerEntity caster, Vec3d position, List<CobblemonActionEffectParser.ParticleCue> cues) {
      for (int i = 0; i < cues.size(); i++) {
         CobblemonActionEffectParser.ParticleCue cue = cues.get(i);
         int cueIndex = i;
         later(cue.delayTicks(), () -> this.emit(caster, position, cue.effectId(), cueIndex));
      }
   }

   private void scheduleSounds(ServerPlayerEntity caster, Vec3d position, List<CobblemonActionEffectParser.SoundCue> cues) {
      for (CobblemonActionEffectParser.SoundCue cue : cues) {
         later(cue.delayTicks(), () -> this.emitSound(caster, position, cue.soundId()));
      }
   }

   private void scheduleAnimations(ServerPlayerEntity caster, List<CobblemonActionEffectParser.AnimationCue> cues) {
      if (cues.isEmpty()) {
         caster.swingHand(Hand.MAIN_HAND, true);
      } else {
         for (CobblemonActionEffectParser.AnimationCue cue : cues) {
            later(cue.delayTicks(), () -> {
               if (caster.isAlive() && !caster.isDisconnected()) {
                  caster.swingHand(Hand.MAIN_HAND, true);
               }
            });
         }
      }
   }

   private void scheduleMega(ServerPlayerEntity caster, CobblemonActionEffectParser.MegaAccent accent) {
      Vec3d position = caster.getPos().add(0.0, 1.0, 0.0);
      later(accent.particleDelayTicks(), () -> this.emit(caster, position, accent.effectId(), 0));
      later(accent.animationDelayTicks(), () -> {
         if (caster.isAlive() && !caster.isDisconnected()) {
            caster.swingHand(Hand.MAIN_HAND, true);
         }
      });
   }

   private static void later(int delay, Runnable action) {
      if (delay <= 0) {
         action.run();
      } else {
         SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + delay, action);
      }
   }

   private void emit(ServerPlayerEntity caster, Vec3d position, Identifier effectId, int cueIndex) {
      if (caster.isAlive() && !caster.isDisconnected()) {
         List<ServerPlayerEntity> viewers = viewers(caster, position);
         IntegrationConfig.VfxConfig cfg = SVFrameMMOCobblemon.config().vfx;

         for (ServerPlayerEntity viewer : viewers) {
            if (!(viewer.squaredDistanceTo(position) > cfg.fullQualityDistance * cfg.fullQualityDistance) || (cueIndex & 1) != 1) {
               if (!this.claimPacket()) {
                  break;
               }

               new SpawnSnowstormParticlePacket(effectId, position).sendToPlayer(viewer);
               if ("1".equals(System.getenv("SVFRAME_RUNTIME_QA")) && (effectId.getPath().startsWith("bite_") || effectId.getPath().startsWith("lick_")))
                  LOG.info("Runtime move VFX: effect={}, viewer={}, position={}", effectId, viewer.getName().getString(), position);
            }
         }
      }
   }

   private void emitSound(ServerPlayerEntity caster, Vec3d position, Identifier soundId) {
      if (caster.isAlive() && !caster.isDisconnected()) {
         for (ServerPlayerEntity viewer : viewers(caster, position)) {
            if (!this.claimPacket()) {
               break;
            }

            new UnvalidatedPlaySoundS2CPacket(soundId, SoundCategory.PLAYERS, position.x, position.y, position.z, 1.0F, 1.0F)
               .sendToPlayer(viewer);
         }
      }
   }

   private static List<ServerPlayerEntity> viewers(ServerPlayerEntity caster, Vec3d position) {
      IntegrationConfig.VfxConfig cfg = SVFrameMMOCobblemon.config().vfx;
      double radius = Math.max(1.0, Math.min(64.0, cfg.moveBroadcastRadius));
      int limit = Math.max(1, Math.min(128, cfg.maxViewersPerEmission));
      return PlayerLookup.around(caster.getServerWorld(), position, radius).stream().filter(viewer -> !viewer.isDisconnected()).limit(limit).toList();
   }

   private synchronized boolean claimPacket() {
      long tick = SVFrameMMO.currentTick();
      if (tick != this.packetBudgetTick) {
         this.packetBudgetTick = tick;
         this.packetsThisTick = 0;
      }

      int limit = Math.max(1, SVFrameMMOCobblemon.config().vfx.maxSnowstormPacketsPerTick);
      if (this.packetsThisTick >= limit) {
         return false;
      } else {
         this.packetsThisTick++;
         return true;
      }
   }

   private static CobblemonMoveVfxService.LoadedPlans loadPlans() {
      Optional<Path> directory = FabricLoader.getInstance()
         .getModContainer("cobblemon")
         .flatMap(container -> container.findPath("data/cobblemon/action_effects/moves"));
      if (directory.isEmpty()) {
         return new CobblemonMoveVfxService.LoadedPlans(Map.of(), Map.of(), null);
      } else {
         LinkedHashMap<String, CobblemonActionEffectParser.MovePlan> specific = new LinkedHashMap<>();
         LinkedHashMap<String, CobblemonActionEffectParser.MovePlan> generic = new LinkedHashMap<>();
         CobblemonActionEffectParser.MovePlan[] genericMove = new CobblemonActionEffectParser.MovePlan[]{null};

         try (Stream<Path> files = Files.walk(directory.get())) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().forEach(path -> {
               try {
                  try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                     CobblemonActionEffectParser.MovePlan plan = CobblemonActionEffectParser.parseMove(JsonParser.parseReader(reader).getAsJsonObject());
                     if (!plan.empty()) {
                        String relative = directory.get().relativize(path).toString().replace('\\', '/');
                        String file = path.getFileName().toString();
                        String base = file.substring(0, file.length() - 5);
                        if ("generic_move.json".equals(relative)) {
                           genericMove[0] = plan;
                           return;
                        } else {
                           if (relative.startsWith("generic/")) {
                              generic.put(base.toLowerCase(Locale.ROOT), plan);
                           } else {
                              specific.put(CobblemonMoveSkillAdapter.id(base), plan);
                           }

                           return;
                        }
                     }
                  }
               } catch (Exception var12) {
                  LOG.debug("Skipping invalid move VFX {}", path, var12);
               }
            });
         } catch (Exception var9) {
            LOG.error("Cannot read Cobblemon move action effects", var9);
         }

         return new CobblemonMoveVfxService.LoadedPlans(Map.copyOf(specific), Map.copyOf(generic), genericMove[0]);
      }
   }

   private static Map<String, CobblemonActionEffectParser.MegaAccent> loadMegaEffects() {
      Optional<Path> directory = FabricLoader.getInstance()
         .getModContainer("mega_showdown")
         .flatMap(container -> container.findPath("data/mega_showdown/mega_showdown/effect"));
      if (directory.isEmpty()) {
         return Map.of();
      } else {
         LinkedHashMap<String, CobblemonActionEffectParser.MegaAccent> result = new LinkedHashMap<>();

         try (Stream<Path> files = Files.walk(directory.get(), 1)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().forEach(path -> {
               try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                  JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                  CobblemonActionEffectParser.parseMegaAccent(root).ifPresent(accent -> {
                     String file = path.getFileName().toString();
                     result.put(effectKey(file.substring(0, file.length() - 5)), accent);
                  });
               } catch (Exception var7x) {
                  LOG.debug("Skipping invalid Mega Showdown VFX {}", path, var7x);
               }
            });
         } catch (Exception var7) {
            LOG.error("Cannot read Mega Showdown effect descriptors", var7);
         }

         return Map.copyOf(result);
      }
   }

   private static String effectKey(String value) {
      if (value == null) {
         return "";
      } else {
         String lower = value.trim().toLowerCase(Locale.ROOT);
         int namespace = lower.indexOf(58);
         if (namespace >= 0) {
            lower = lower.substring(namespace + 1);
         }

         return lower.replaceAll("[^a-z0-9_.-]", "");
      }
   }

   private record LoadedPlans(
      Map<String, CobblemonActionEffectParser.MovePlan> specific,
      Map<String, CobblemonActionEffectParser.MovePlan> genericProfiles,
      CobblemonActionEffectParser.MovePlan genericMove
   ) {
   }
}
