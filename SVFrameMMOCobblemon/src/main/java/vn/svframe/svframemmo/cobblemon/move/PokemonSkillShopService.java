package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import vn.svframe.svframelib.skill.PlayerAbilityShop;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig;
import vn.svframe.svframemmo.skill.ClassSkill;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog.Origin;

public final class PokemonSkillShopService implements PlayerAbilityShop {
   private final PokemonSkillEconomy economy = new PokemonSkillEconomy();
   private final Set<UUID> purchasing = ConcurrentHashMap.newKeySet();

   public boolean isProcessing(UUID player) {
      return this.purchasing.contains(player);
   }

   public void purchase(ServerPlayerEntity player, String id) {
      this.purchase(player, id, 1, -1);
   }

   @Override public void purchaseUsing(ServerPlayerEntity player,String id,int paymentOption) {
      this.purchase(player,id,paymentOption,-1);
   }

   private long indexSource=-1, catalogRevision;
   private Object indexConfig;
   private List<Offer> indexed=List.of();
   private final Map<String,Map<String,Object>> metadata=new LinkedHashMap<>();

   /** Main-thread definition fence. Native purchases never read this display cache. */
   public long catalogRevision(){
      Object cfg=SVFrameMMOCobblemon.config().pokemonSkills;long source=CobblemonMoveSkillAdapter.revision();
      if(source!=indexSource||cfg!=indexConfig){
         indexSource=source;indexConfig=cfg;metadata.clear();var next=new ArrayList<Offer>();
         if(SVFrameMMOCobblemon.config().pokemonSkills.enabled)for(String moveId:PlayerMoveCoverage.purchaseAdapters()){
            var definition=CobblemonMoveSkillAdapter.definition(moveId);
            if(definition==null||definition.getSkill()==null||!PlayerMoveCoverage.purchasable(moveId))continue;
            String skillId=definition.getSkill().getId(),name=definition.getSkill().getName();if(name==null||name.isBlank())name=moveId;
            next.add(new Offer(moveId,skillId,name,SVFrameMMOCobblemon.config().pokemonSkills.payment(1,moveId,skillId).price(),false));
         }
         next.sort(Comparator.<Offer>comparingInt(o->PlayerMoveCoverage.family(o.moveId())==PlayerMoveCoverage.Family.SIGNATURE?0:1).thenComparing(Offer::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Offer::moveId));
         if(next.size()>4096)throw new IllegalStateException("Pokémon shop exceeds 4096 definitions");
         indexed=List.copyOf(next);catalogRevision++;
      }
      return catalogRevision;
   }

   /** Optional sliced presentation API; each page captures only the requested live progression. */
   public Map<String,Object> catalogPage(ServerPlayerEntity player,int start,int limit){
      if(start<0||limit<1||limit>32)throw new IllegalArgumentException("Invalid Pokémon catalog slice");
      long revision=catalogRevision();var rows=new ArrayList<Map<String,Object>>();
      boolean beastAvailable=economy.available("beconomy","secondary"),dollarsAvailable=economy.available("cobbledollars","cobbledollars");
      PlayerData data=SVFrameMMO.playerData().get(player);
      for(int i=start;i<Math.min(indexed.size(),start+limit);i++){
         Offer indexedOffer=indexed.get(i);Offer offer=new Offer(indexedOffer.moveId(),indexedOffer.skillId(),indexedOffer.name(),indexedOffer.price(),SVFrameMMO.externalProgression().isLearned(player.getUuid(),indexedOffer.skillId()));
         ClassSkill definition=CobblemonMoveSkillAdapter.definition(offer.moveId());MoveTemplate move=Moves.getByName(offer.moveId());
         if(move==null||definition==null)continue;
         CobblemonMoveProfile profile=CobblemonMoveProfile.of(move);
         int level=Math.max(1,SVFrameMMO.externalProgression().level(player.getUuid(),offer.skillId()));
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.putAll(metadata.computeIfAbsent(offer.moveId(),key->{var base=new LinkedHashMap<String,Object>();
            base.put("id", offer.moveId());
            base.put("skillId", offer.skillId());
            base.put("name", offer.name());
            base.put("nameKey", translation(move.getDisplayName()));
            base.put("description", move.getDescription().getString());
            base.put("descriptionKey", translation(move.getDescription()));
            base.put("type", move.getElementalType().getName());
            base.put("category", profile.isHeal() ? "healing" : profile.damageCategory());
            base.put("range", profile.range());
            base.put("radius", profile.radius());
            base.put("requiredLevel", definition.getUnlockLevel());
            base.put("effectKey", PlayerMoveCoverage.effectKey(offer.moveId()));
            base.put("family", PlayerMoveCoverage.family(offer.moveId()).name().toLowerCase(Locale.ROOT));
              return Map.copyOf(base);
            }));
            row.put("owned", offer.owned());
            row.put("price", offer.price().toPlainString());
            row.put("currency","beastcoin");
            List<Map<String,Object>> payments=new ArrayList<>();
            for(int option:new int[]{1,2}) {
               SkillPayment payment=SVFrameMMOCobblemon.config().pokemonSkills.payment(option,offer.moveId(),offer.skillId());
               payments.add(Map.of("id",payment.id(),"price",payment.price().toPlainString(),"currency",economy.displayCurrency(payment.provider(),payment.currency()),"available",option==1?beastAvailable:dollarsAvailable));
            }
            row.put("paymentOptions",List.copyOf(payments));
            row.put("mana", definition.getParameter("mana", level, data));
            row.put("stamina", definition.getParameter("stamina", level, data));
            row.put("cooldown", definition.getParameter("cooldown", level, data));
            row.put("level", level);
            row.put("damage", definition.getParameter("damage", level, data));
            row.put(
               "slots",
               PlayerSkillCatalog.allowedSlots(data, new vn.svframe.svframemmo.skill.PlayerSkillCatalog.Entry(definition, Origin.EXTERNAL, true, level))
            );
         rows.add(Map.copyOf(row));
      }
      return Map.of("revision",revision,"total",indexed.size(),"next",Math.min(indexed.size(),start+limit),"rows",List.copyOf(rows));
   }

   public List<Map<String,Object>> catalog(ServerPlayerEntity player){
      var rows=new ArrayList<Map<String,Object>>();int cursor=0;
      do{var page=catalogPage(player,cursor,32);for(var row:(List<?>)page.get("rows"))rows.add((Map<String,Object>)row);cursor=(Integer)page.get("next");}while(cursor<indexed.size());
      return List.copyOf(rows);
   }

   private static String translation(Text text) {
      return text.getContent() instanceof TranslatableTextContent translated ? translated.getKey() : "";
   }

   public void open(ServerPlayerEntity player) {
      this.open(player, 0);
   }

   public void open(ServerPlayerEntity player, int requestedPage) { this.open(player,requestedPage,1); }

   public void open(ServerPlayerEntity player,int requestedPage,int paymentOption) {
      if (player != null) {
         IntegrationConfig.PokemonSkillShopConfig config = SVFrameMMOCobblemon.config().pokemonSkills;
         if (!config.enabled) {
            player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.disabled"), true);
         } else {
            List<PokemonSkillShopService.Offer> offers = this.offers(player.getUuid());
            if (offers.isEmpty()) {
               player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.not_ready"), true);
            } else {
               int last = Math.max(0, (offers.size() - 1) / 36);
               new PokemonSkillShopGui(player, this, Math.max(0, Math.min(last, requestedPage)), paymentOption).open();
            }
         }
      }
   }

   public List<PokemonSkillShopService.Offer> offers(UUID player) { return offers(player,1); }

   public List<PokemonSkillShopService.Offer> offers(UUID player,int paymentOption) {
      if (!SVFrameMMOCobblemon.config().pokemonSkills.enabled) {
         return List.of();
      } else {
         ArrayList<PokemonSkillShopService.Offer> result = new ArrayList<>();

         for (String moveId : PlayerMoveCoverage.purchaseAdapters()) {
            ClassSkill definition = CobblemonMoveSkillAdapter.definition(moveId);
            if (definition != null && definition.getSkill() != null && PlayerMoveCoverage.purchasable(moveId)) {
               String skillId = definition.getSkill().getId();
               String name = definition.getSkill().getName();
               if (name == null || name.isBlank()) {
                  name = moveId;
               }

               result.add(
                  new PokemonSkillShopService.Offer(
                     moveId, skillId, name, SVFrameMMOCobblemon.config().pokemonSkills.payment(paymentOption,moveId,skillId).price(), SVFrameMMO.externalProgression().isLearned(player, skillId)
                  )
               );
            }
         }

         result.sort(
            Comparator.<PokemonSkillShopService.Offer>comparingInt(
                  offer -> PlayerMoveCoverage.family(offer.moveId()) == PlayerMoveCoverage.Family.SIGNATURE ? 0 : 1
               )
               .thenComparing(PokemonSkillShopService.Offer::name, String.CASE_INSENSITIVE_ORDER)
               .thenComparing(PokemonSkillShopService.Offer::moveId)
         );
         return List.copyOf(result);
      }
   }

   public List<String> moveIds() {
      return CobblemonMoveSkillAdapter.definitions().keySet().stream().filter(PlayerMoveCoverage::purchasable).sorted(String.CASE_INSENSITIVE_ORDER).toList();
   }

   public void purchase(ServerPlayerEntity player,String rawMoveId,int returnPage) { this.purchase(player,rawMoveId,1,returnPage); }

   public void purchase(ServerPlayerEntity player, String rawMoveId, int paymentOption,int returnPage) {
      if (player != null) {
         IntegrationConfig.PokemonSkillShopConfig config = SVFrameMMOCobblemon.config().pokemonSkills;
         if (!config.enabled) {
            player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.disabled"), true);
         } else if(paymentOption<0||paymentOption>2) {
            player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.payment_invalid"),true);
         } else {
            PokemonSkillShopService.ResolvedSkill resolved = resolve(rawMoveId);
            if (resolved == null) {
               player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.unknown", new Object[]{rawMoveId}), true);
            } else if (!PlayerMoveCoverage.purchasable(resolved.moveId)) {
               player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.unsupported"), true);
            } else {
               UUID playerId = player.getUuid();
               if (SVFrameMMO.externalProgression().isLearned(playerId, resolved.skillId)) {
                  player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.already_owned", new Object[]{resolved.name}), true);
                  if (returnPage >= 0) {
                     this.open(player, returnPage,paymentOption);
                  }
               } else if (!this.purchasing.add(playerId)) {
                  player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.processing"), true);
               } else {
                  SkillPayment payment=config.payment(paymentOption,resolved.moveId,resolved.skillId);
                  BigDecimal price=payment.price();
                  String provider=payment.provider();
                  String currency=payment.currency();
                  this.economy.withdraw(player, provider, currency, price).whenComplete((charge, failure) -> {
                     MinecraftServer server = player.getServer();
                     if (server == null) {
                        this.purchasing.remove(playerId);
                     } else {
                        server.execute(() -> this.completePurchase(player, resolved, price, provider, currency, returnPage, charge, failure));
                     }
                  });
               }
            }
         }
      }
   }

   private void completePurchase(
      ServerPlayerEntity player,
      PokemonSkillShopService.ResolvedSkill resolved,
      BigDecimal price,
      String provider,
      String currency,
      int returnPage,
      PokemonSkillEconomy.ChargeResult charge,
      Throwable failure
   ) {
      UUID playerId = player.getUuid();
      if (failure == null && charge != null && charge.charged()) {
         if (SVFrameMMO.externalProgression().isLearned(playerId, resolved.skillId)) {
            this.refundAndFinish(
               player, price, provider, currency, resolved.name + " was already granted while payment was processing; the purchase was refunded.", returnPage
            );
         } else {
            boolean learned = false;

            try {
               learned = SVFrameMMO.externalProgression().learn(playerId, resolved.skillId, 1);
               if (!learned) {
                  this.refundAndFinish(player, price, provider, currency, "SVFrameMMO rejected the skill grant; the purchase was refunded.", returnPage);
               } else {
                  SVFrameMMO.externalProgression()
                     .saveDurably()
                     .whenComplete(
                        (ignored, persistFailure) -> {
                           MinecraftServer server = player.getServer();
                           if (server == null) {
                              this.purchasing.remove(playerId);
                           } else {
                              server.execute(
                                 () -> {
                                    if (persistFailure != null) {
                                       this.rollbackLearn(playerId, resolved.skillId);
                                       this.refundAndFinish(
                                          player, price, provider, currency, "The skill could not be saved; the purchase was refunded.", returnPage
                                       );
                                    } else {
                                       this.purchasing.remove(playerId);
                                       ServerPlayerEntity online = server.getPlayerManager().getPlayer(playerId);
                                       if (online != null) {
                                          online.sendMessage(
                                             Text.translatable(
                                                "svframemmo_cobblemon.shop.purchased",
                                                new Object[]{resolved.name, format(price), this.economy.displayCurrency(provider, currency)}
                                             ),
                                             true
                                          );
                                          if (returnPage >= 0) {
                                             this.open(online, returnPage,provider.equals("cobbledollars")?2:1);
                                          }
                                       }
                                    }
                                 }
                              );
                           }
                        }
                     );
               }
            } catch (Throwable var12) {
               SVFrameMMOCobblemon.LOG.error("Could not persist purchased Pokemon skill {} for {}", new Object[]{resolved.skillId, playerId, var12});
               if (learned) {
                  this.rollbackLearn(playerId, resolved.skillId);
               }

               this.refundAndFinish(player, price, provider, currency, "The skill could not be saved; the purchase was refunded.", returnPage);
            }
         }
      } else {
         this.purchasing.remove(playerId);
         if (failure != null) {
            SVFrameMMOCobblemon.LOG.error("Pokemon skill charge failed for {}", playerId, failure);
         }

         String message = charge == null ? "Economy transaction failed. No skill was granted." : charge.message();
         if (message != null && !message.isBlank()) {
            SVFrameMMOCobblemon.LOG.warn("Skill payment rejected for {}: {}", playerId, message);
         }

         player.sendMessage(
            Text.translatable(charge != null && charge.insufficient() ? "svframemmo_cobblemon.shop.insufficient" : "svframemmo_cobblemon.shop.payment_failed"),
            true
         );
      }
   }

   private void rollbackLearn(UUID playerId, String skillId) {
      try {
         SVFrameMMO.externalProgression().forget(playerId, skillId);
         SVFrameMMO.externalProgression().save();
      } catch (Throwable var4) {
         SVFrameMMOCobblemon.LOG.error("Could not rollback failed Pokemon skill grant {} for {}", new Object[]{skillId, playerId, var4});
      }
   }

   private void refundAndFinish(ServerPlayerEntity player, BigDecimal amount, String provider, String currency, String successMessage, int returnPage) {
      UUID playerId = player.getUuid();
      this.economy
         .refund(player, provider, currency, amount)
         .whenComplete(
            (refunded, failure) -> {
               MinecraftServer server = player.getServer();
               if (server == null) {
                  this.purchasing.remove(playerId);
               } else {
                  server.execute(
                     () -> {
                        this.purchasing.remove(playerId);
                        if (failure == null && Boolean.TRUE.equals(refunded)) {
                           player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.refunded"), true);
                        } else {
                           player.sendMessage(Text.translatable("svframemmo_cobblemon.shop.refund_failed"), false);
                           SVFrameMMOCobblemon.LOG
                              .error(
                                 "Pokemon skill refund failed for player {}, provider {}, currency {}, amount {}",
                                 new Object[]{playerId, provider, currency, amount, failure}
                              );
                        }

                        if (returnPage >= 0 && !player.isDisconnected()) {
                           this.open(player, returnPage,provider.equals("cobbledollars")?2:1);
                        }
                     }
                  );
               }
            }
         );
   }

   public PokemonSkillShopService.GrantResult adminGive(ServerPlayerEntity target, String rawMoveId) {
      if (target == null) {
         return PokemonSkillShopService.GrantResult.failed("Target player is unavailable.");
      } else {
         PokemonSkillShopService.ResolvedSkill resolved = resolve(rawMoveId);
         if (resolved == null) {
            return PokemonSkillShopService.GrantResult.failed("Unknown Pokemon skill: " + rawMoveId);
         } else {
            UUID playerId = target.getUuid();
            if (SVFrameMMO.externalProgression().isLearned(playerId, resolved.skillId)) {
               return PokemonSkillShopService.GrantResult.failed(target.getName().getString() + " already owns " + resolved.name + ".");
            } else {
               boolean learned = false;

               try {
                  learned = SVFrameMMO.externalProgression().learn(playerId, resolved.skillId, 1);
                  if (!learned) {
                     return PokemonSkillShopService.GrantResult.failed("SVFrameMMO rejected the skill grant for " + resolved.skillId + ".");
                  } else {
                     SVFrameMMO.externalProgression().save();
                     return PokemonSkillShopService.GrantResult.success(
                        "Gave " + resolved.name + " to " + target.getName().getString() + ".", resolved.name
                     );
                  }
               } catch (Throwable var7) {
                  SVFrameMMOCobblemon.LOG.error("Could not persist admin Pokemon skill grant {} for {}", new Object[]{resolved.skillId, playerId, var7});
                  if (learned) {
                     this.rollbackLearn(playerId, resolved.skillId);
                  }

                  return PokemonSkillShopService.GrantResult.failed("Could not save the Pokemon skill grant; no grant was kept.");
               }
            }
         }
      }
   }

   public BigDecimal price(String moveId, String skillId) {
      IntegrationConfig.PokemonSkillShopConfig config = SVFrameMMOCobblemon.config().pokemonSkills;
      return config.payment(1,moveId,skillId).price();
   }

   public String currencyLabel() {
      IntegrationConfig.PokemonSkillShopConfig config = SVFrameMMOCobblemon.config().pokemonSkills;
      return "beastcoin";
   }

   public String title() {
      return SVFrameMMOCobblemon.config().pokemonSkills.title;
   }

   private static PokemonSkillShopService.ResolvedSkill resolve(String raw) {
      if (raw != null && !raw.isBlank()) {
         String moveId;
         try {
            moveId = CobblemonMoveSkillAdapter.isCanonicalSkillId(raw) ? CobblemonMoveSkillAdapter.moveIdFromCanonical(raw) : CobblemonMoveSkillAdapter.id(raw);
         } catch (IllegalArgumentException var4) {
            return null;
         }

         ClassSkill definition = CobblemonMoveSkillAdapter.definitions().get(moveId);
         if (definition != null && definition.getSkill() != null) {
            String name = definition.getSkill().getName();
            if (name == null || name.isBlank()) {
               name = moveId;
            }

            return new PokemonSkillShopService.ResolvedSkill(moveId, definition.getSkill().getId(), name);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static String format(BigDecimal amount) {
      return amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
   }

   public record GrantResult(boolean success, String message, String skillName) {
      public static PokemonSkillShopService.GrantResult success(String message, String name) {
         return new PokemonSkillShopService.GrantResult(true, message, name);
      }

      public static PokemonSkillShopService.GrantResult failed(String message) {
         return new PokemonSkillShopService.GrantResult(false, message, "");
      }
   }

   public record Offer(String moveId, String skillId, String name, BigDecimal price, boolean owned) {
   }

   private record ResolvedSkill(String moveId, String skillId, String name) {
   }
}
