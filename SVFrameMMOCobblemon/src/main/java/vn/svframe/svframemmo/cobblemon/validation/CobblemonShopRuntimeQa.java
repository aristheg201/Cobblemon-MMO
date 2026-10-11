package vn.svframe.svframemmo.cobblemon.validation;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframelib.player.cooldown.CooldownMap;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveProfile;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;
import vn.svframe.svframemmo.cobblemon.move.MoveSemantic;
import vn.svframe.svframemmo.cobblemon.move.MoveSemanticRegistry;
import vn.svframe.svframemmo.cobblemon.move.PlayerMoveCoverage;
import vn.svframe.svframemmo.cobblemon.move.PokemonSkillShopService;
import vn.svframe.svframemmo.skill.ClassSkill;

public final class CobblemonShopRuntimeQa {
   private final ServerPlayerEntity player;
   private final List<PokemonSkillShopService.Offer> offers;
   private final Map<String, Object> report = new LinkedHashMap<>();
   private final List<Map<String, Object>> results = new ArrayList<>();
   private int index;

   private CobblemonShopRuntimeQa(ServerPlayerEntity player) {
      this.player = player;
      this.offers = SVFrameMMOCobblemon.pokemonSkills().offers(player.getUuid());
      this.report.put("offers", this.results);
      this.report.put("expected_offer_count", PlayerMoveCoverage.purchaseAdapters().size());
   }

   public static int run(ServerPlayerEntity player) {
      if (!"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))) {
         return 0;
      } else if (!SVFrameMMOCobblemon.config().pokemonSkills.normalizedProvider().equals("cobbledollars")) {
         throw new IllegalArgumentException("This QA scenario requires the configured real CobbleDollars provider");
      } else {
         CobblemonShopRuntimeQa test = new CobblemonShopRuntimeQa(player);
         test.next();
         return 1;
      }
   }

   private BigInteger balance() {
      try {
         Class<?> api = Class.forName("fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt");
         return (BigInteger)api.getMethod("getCobbleDollars", PlayerEntity.class).invoke(null, this.player);
      } catch (ReflectiveOperationException var2) {
         throw new IllegalStateException(var2);
      }
   }

   private void next() {
      if (this.player.isDisconnected()) {
         this.report.put("failure", "player disconnected");
         this.save();
      } else if (this.index != this.offers.size()) {
         PokemonSkillShopService.Offer offer = this.offers.get(this.index++);
         LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
         this.results.add(entry);
         entry.put("id", offer.moveId());
         entry.put("price", offer.price().toPlainString());
         BigInteger before = this.balance();
         entry.put("balanceBefore", before.toString());
         entry.put("previouslyOwned", offer.owned());
         SVFrameMMOCobblemon.pokemonSkills().purchase(this.player, offer.moveId());
         this.waitForPurchase(offer, entry, before, 0);
      } else {
         BigInteger before = this.balance();
         SVFrameMMOCobblemon.pokemonSkills().purchase(this.player, "metronome");
         this.report
            .put(
               "unsupported_not_sold",
               this.balance().equals(before) && !SVFrameMMO.externalProgression().isLearned(this.player.getUuid(), "COBBLEMON_MOVE_METRONOME")
            );
         this.report.put("count", this.results.size());
         this.report.put("finished", true);
         SVFrameMMO.externalProgression().saveDurably();
         this.save();
      }
   }

   private void waitForPurchase(PokemonSkillShopService.Offer offer, Map<String, Object> entry, BigInteger before, int age) {
      SVFrameLibFabricMod.schedule(2, () -> {
         if (SVFrameMMOCobblemon.pokemonSkills().isProcessing(this.player.getUuid()) && age < 200) {
            this.waitForPurchase(offer, entry, before, age + 2);
         } else {
            boolean learned = SVFrameMMO.externalProgression().isLearned(this.player.getUuid(), offer.skillId());
            BigInteger after = this.balance();
            BigInteger expected = offer.owned() ? before : before.subtract(offer.price().toBigIntegerExact());
            entry.put("balanceAfter", after.toString());
            entry.put("purchase_correct", learned && after.equals(expected));
            SVFrameMMOCobblemon.pokemonSkills().purchase(this.player, offer.moveId());
            entry.put("duplicate_no_charge", this.balance().equals(after));
            if (!learned) {
               entry.put("failure", "purchase did not grant ownership");
               this.save();
               this.next();
            } else {
               SVFrameMMO.externalProgression().bind(this.player.getUuid(), 1, offer.skillId());
               entry.put("equipped", offer.skillId().equals(SVFrameMMO.externalProgression().bindings(this.player.getUuid()).get(1)));
               this.cast(offer, entry, 0);
            }
         }
      });
   }

   private void cast(PokemonSkillShopService.Offer offer, Map<String, Object> entry, int attempt) {
      PlayerData data = SVFrameMMO.playerData().get(this.player);
      ClassSkill definition = CobblemonMoveSkillAdapter.definition(offer.moveId());
      if (this.player.getServerWorld().getBlockState(new BlockPos(40, 64, 0)).getCollisionShape(this.player.getServerWorld(), new BlockPos(40, 64, 0)).isEmpty()) {
         throw new IllegalStateException("QA requires a solid test floor at 40,64,0 before testing movement and targeting");
      } else {
         CooldownMap cooldown = data.getMMOPlayerData().getCooldownMap();
         cooldown.resetCooldown(definition.getCooldownPath());
         cooldown.resetCooldown("svframemmo_global_skill");
         MoveTemplate move = Moves.getByName(offer.moveId());
         CobblemonMoveProfile profile = CobblemonMoveProfile.of(move);
         MoveSemantic semantic = new MoveSemanticRegistry().resolve(move);
         FusionService fusions = SVFrameMMOCobblemon.fusions();
         fusions.clearMoveState(this.player.getUuid());
         this.player.networkHandler.requestTeleport(40.0, 65.0, 0.0, 0.0F, profile.executor() == CobblemonMoveProfile.Executor.TELEPORT ? 0.0F : 10.0F);
         this.player.setVelocity(Vec3d.ZERO);
         this.player.fallDistance = 0.0F;
         boolean healing = profile.isHeal();
         CowEntity cow = healing ? null : (CowEntity)EntityType.COW.create(this.player.getServerWorld());
         if (cow != null) {
            cow.setAiDisabled(true);
            cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200.0);
            cow.setHealth(200.0F);
            cow.refreshPositionAndAngles(40.0, 65.0, 2.0, 0.0F, 0.0F);
            cow.addCommandTag("svframe_qa");
            this.player.getServerWorld().spawnEntity(cow);
         }

         if (healing) {
            this.player.setHealth(Math.max(1.0F, this.player.getMaxHealth() - 16.0F));
         }

         SVFrameLibFabricMod.schedule(
            10,
            () -> {
               float healthBefore = this.player.getHealth();
               Vec3d positionBefore = this.player.getPos();
               SkillResult result = SVFrameMMO.skillRuntime().castBound(data, 1);

               boolean effect = switch (profile.executor()) {
                  case HEAL -> this.player.getHealth() > healthBefore;
                  case SHIELD -> fusions.blocksDamage(this.player);
                  case TELEPORT -> this.player.getPos().squaredDistanceTo(positionBefore) > 0.5;
                  case DASH -> this.player.getVelocity().lengthSquared() > 0.01;
                  case SELF_BUFF -> semantic.stages().stream().anyMatch(change -> fusions.moveStage(this.player, change.stat()) != 0);
                  case TARGET_DEBUFF -> cow != null
                     && (semantic.stages().stream().anyMatch(change -> fusions.moveStage(cow, change.stat()) != 0) || hasStatus(cow));
                  case AOE -> cow != null
                     && (
                        move.getPower() > 0.0
                           ? cow.getHealth() < 200.0F
                           : semantic.stages().stream().anyMatch(change -> fusions.moveStage(cow, change.stat()) != 0) || hasStatus(cow)
                     );
                  default -> cow != null && cow.getHealth() < 200.0F;
               };
               entry.put("executor", profile.executor().name());
               entry.put("family", PlayerMoveCoverage.family(offer.moveId()).name());
               entry.put("castSuccessful", result.isSuccessful());
               entry.put("attempts", attempt + 1);
               entry.put("actual_effect", effect);
               entry.put("beforeHealth", healing ? healthBefore : 200.0F);
               entry.put("afterHealth", healing ? this.player.getHealth() : (cow == null ? 0.0F : cow.getHealth()));
               if (result.isSuccessful()) {
                  entry.put("cooldown_rejects_repeat", !SVFrameMMO.skillRuntime().castBound(data, 1).isSuccessful());
               }

               if (cow != null) {
                  fusions.clearMoveState(cow.getUuid());
                  cow.discard();
               }

               this.save();
               if (!effect && attempt < 5) {
                  this.cast(offer, entry, attempt + 1);
               } else {
                  SVFrameLibFabricMod.schedule(20, this::next);
               }
            }
         );
      }
   }

   private static boolean hasStatus(LivingEntity entity) {
      return !entity.getStatusEffects().isEmpty() || entity.isOnFire() || entity.getFrozenTicks() > 0;
   }

   private void save() {
      try {
         Path p = Path.of("qa/cobblemon-shop-runtime.json");
         Files.createDirectories(p.getParent());
         Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(this.report));
      } catch (IOException var2) {
         throw new IllegalStateException(var2);
      }
   }
}
