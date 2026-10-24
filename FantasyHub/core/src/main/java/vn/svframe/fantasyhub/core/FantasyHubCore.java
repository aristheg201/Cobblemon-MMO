package vn.svframe.fantasyhub.core;

import static net.minecraft.server.command.CommandManager.literal;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.fantasyhub.protocol.Protocol;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.runtime.PersistentHudRuntime;
import vn.svframe.svframemmo.skill.ClassSkill;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;

/** Presentation bridge consuming the existing authoritative MMO owner. */
public final class FantasyHubCore implements ModInitializer {
  private static final Gson GSON = new Gson();
  private static final Map<Object,Map<String,Object>> CLASS_METADATA = new IdentityHashMap<>();
  static void clearPresentationDefinitions(){CLASS_METADATA.clear();}
  private final Map<UUID,Long> nextAction=new HashMap<>(),nextObservation=new HashMap<>();
  private ExecutorService qaWriter;
  private long tick;
  private final NativeSyncRuntime sync=new NativeSyncRuntime(FantasyHubCore::stateMap);
  private void invalidate(UUID id){sync.invalidate(id);}
  private void invalidateAll(){sync.invalidateAll();}


  @Override
  public void onInitialize() {
    Protocol.install();
    sync.install();
    ClassProgression.install();
    CommerceRuntime.install();
    KabutoRuntime.install();
    RiderEquipment.install();
    RiderRuntime.install();
    vn.svframe.fantasyhub.core.hero.HeroRuntime.install();
    vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.registerMenu(
        "svframe_class_switch",
        player -> {
          invalidate(player.getUuid());
          ServerPlayNetworking.send(player, new Protocol.Open("classes"));
        });
    vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.registerMenu(
        "svframe_close", player -> ServerPlayNetworking.send(player, new Protocol.Open("close")));
    ServerLifecycleEvents.SERVER_STARTING.register(
        server -> {
          Path root =
              net.fabricmc.loader.api.FabricLoader.getInstance()
                  .getConfigDir()
                  .resolve("SVFrameLib");
          try {
            boolean installedSource = SourcePackInstaller.install();
            KabutoRuntime.reload();
            RiderRuntime.reload();
            for (String name : List.of("reference-models.json", "visual-anchors.json")) {
              Path target =
                  name.equals("reference-models.json")
                      ? root.resolve("visual-models").resolve(name)
                      : root.resolve(name);
              if (Files.exists(target)) continue;
              try (var input = getClass().getResourceAsStream("/default/presentation/" + name)) {
                if (input == null)
                  throw new IllegalStateException(
                      "Fantasy Hub server artifact is missing presentation configuration: " + name);
                Files.createDirectories(target.getParent());
                Files.copy(input, target);
              }
            }
            boolean installedClass = false;
            Path classes =
                net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("SVFrameMMO/classes");
            for (String name : List.of("death_knight.yml", "anti_mage_knight.yml")) {
              Path target = classes.resolve(name);
              if (Files.exists(target)) continue;
              try (var input =
                  getClass().getResourceAsStream("/default/presentation/classes/" + name)) {
                if (input == null)
                  throw new IllegalStateException("Missing reference class configuration: " + name);
                Files.createDirectories(classes);
                Files.copy(input, target);
                installedClass = true;
              }
            }
            // Definitions must exist before saved characters attach at SERVER_STARTED.
            if (!vn.svframe.svframelib.fabric.SVFrameLibFabricMod.reloadAll())
              throw new IllegalStateException("Cannot load authored source skill definitions");
            vn.svframe.svframelib.skill.NativeSkillProviders.reloadDefinitions();
            if (!SVFrameMMO.reload())
              throw new IllegalStateException("Cannot load authored class definitions");
            vn.svframe.svframelib.fabric.NativeVisualRuntime.reload();
            ClassSwitchService.reload();
            ClassProgression.reload();
            if (!vn.svframe.svframeitems.SVFrameItems.reload())
              throw new IllegalStateException(
                  "Cannot load source Dragonborn equipment definitions");
          } catch (java.io.IOException failure) {
            throw new IllegalStateException(
                "Cannot initialize Fantasy Hub presentation configuration", failure);
          }
        });
    PresentationHandshake.install();
    ServerPlayNetworking.registerGlobalReceiver(
        Protocol.Action.ID,
        (request, context) -> {
          var player = context.player();
          if(request.action().equals("hero_input")){vn.svframe.fantasyhub.core.hero.HeroRuntime.input(player,request.slot());return;}
          if(request.action().equals("ui_open")){sync.open(player.getUuid(),request.id());return;}
          if(request.action().equals("ui_close")){sync.close(player.getUuid(),request.id());return;}
          if(request.action().equals("ui_keepalive")){sync.touch(player);return;}
          long now = tick;
          if (now < nextAction.getOrDefault(player.getUuid(), -1L)) return;
          nextAction.put(player.getUuid(), now + 2);
          var data = SVFrameMMO.playerData().get(player);
          if ((ClassSwitchService.busy(data)||ClassProgression.busy(data))
              && !request.action().equals("refresh")) return;
          try {
            switch (request.action()) {
              case "switch_class" -> {
                ClassSwitchService.request(
                    player, request.id(), () -> invalidate(player.getUuid()));
                return;
              }
              case "shop_buy" -> {CommerceRuntime.purchase(player,request.id(),request.slot());invalidate(player.getUuid());return;}
              case "gear_upgrade" -> {ItemUpgradeRuntime.request(player,request.id());invalidate(player.getUuid());return;}
              case "shop_claim" -> {CommerceRuntime.claim(player);invalidate(player.getUuid());return;}
              case "switch_bypass" -> {ClassSwitchService.request(player,request.id(),true,()->invalidate(player.getUuid()));return;}
              case "unlock_class" -> {player.sendMessage(Text.literal("Choose BeastCoin or HunterCoin in /fantasyshop"),false);return;}
              case "learn" -> {ClassProgression.learn(player,request.id());invalidate(player.getUuid());return;}
              case "allocate" -> {
                if (request.slot() != 1) return;
                data.spendAttributePoints(request.id(), 1);
              }
              case "respec" -> data.reallocateAttributes();
              case "bind" -> {
                if (request.slot() < 1 || request.slot() > 9) return;
                PlayerSkillCatalog.bind(data, request.slot(), request.id());
              }
              case "unbind" -> {
                if (request.slot() < 1 || request.slot() > 9) return;
                PlayerSkillCatalog.unbind(data, request.slot());
              }
              case "upgrade" -> {
                var entry = PlayerSkillCatalog.owned(data, request.id());
                if (entry != null) PlayerSkillCatalog.upgrade(data, entry, 1);
              }
              case "cast" -> {
                if (request.slot() < 1 || request.slot() > 9) return;
                SVFrameMMO.skillRuntime().castBound(data, request.slot());
              }
              case "cast_id" -> SVFrameMMO.skillRuntime().cast(data, request.id());
              case "purchase" -> {
                var shop = vn.svframe.svframelib.skill.PlayerAbilityShop.get();
                if (shop == null) return;
                shop.purchaseUsing(player, request.id(), request.slot());
              }
              case "refresh" -> invalidate(player.getUuid());
              default -> {
                return;
              }
            }
            if (!Set.of("cast", "cast_id", "purchase", "refresh").contains(request.action()))
              SVFrameMMO.playerData().savePlayer(data);
            if(!Set.of("cast","cast_id").contains(request.action()))invalidate(player.getUuid());
          } catch (IllegalArgumentException | IllegalStateException rejected) {
            player.sendMessage(Text.translatable("fantasyhub.action.rejected"), true);
          }
        });
    ServerPlayNetworking.registerGlobalReceiver(
        Protocol.Observation.ID,
        (observation, context) -> {
          if (!"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))) return;
          UUID id = context.player().getUuid();
          if (tick < nextObservation.getOrDefault(id, -1L)) return;
          nextObservation.put(id, tick + 2);
          com.google.gson.JsonObject json;
          try {
            json = JsonParser.parseString(observation.json()).getAsJsonObject();
          } catch (RuntimeException malformed) {
            return;
          }
          json.addProperty("serverMaxHealth", context.player().getMaxHealth());
          json.addProperty("serverHealth", context.player().getHealth());
          json.addProperty("serverVanillaLevel", context.player().experienceLevel);
          json.addProperty("serverVanillaProgress", context.player().experienceProgress);
          json.addProperty("serverVanillaTotal", context.player().totalExperience);
          json.addProperty("serverTick", tick);
          json.addProperty("scenario", RpgLifecycleQa.label(id));
          var writer = qaWriter;
          if (writer != null && !writer.isShutdown())
            writer.execute(
                () -> {
                  try {
                    Path dir = Path.of("qa", "fantasyhub");
                    Files.createDirectories(dir);
                    Files.writeString(
                        dir.resolve(id + ".jsonl"),
                        GSON.toJson(json) + "\n",
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                  } catch (java.io.IOException e) {
                    java.util.logging.Logger.getLogger("FantasyHub-QA")
                        .log(java.util.logging.Level.WARNING, "Could not write QA observation", e);
                  }
                });
        });
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          if ("1".equals(System.getenv("SVFRAME_RUNTIME_QA")))
            qaWriter =
                new ThreadPoolExecutor(
                    1,
                    1,
                    0,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(512),
                    r -> {
                      Thread t = new Thread(r, "FantasyHub-QA");
                      t.setDaemon(true);
                      return t;
                    },
                    new ThreadPoolExecutor.DiscardPolicy());
        });
    ServerLifecycleEvents.SERVER_STOPPING.register(
        server -> {
          invalidateAll();

          nextAction.clear();
          nextObservation.clear();
          if (qaWriter != null) qaWriter.shutdown();
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> {
          if (qaWriter != null)
            try {
              if (!qaWriter.awaitTermination(5, TimeUnit.SECONDS)) qaWriter.shutdownNow();
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
        });
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) ->
            PersistentHudRuntime.setPresentationOwner(handler.player.getUuid(), true));
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> {
          nextAction.remove(handler.player.getUuid());
          nextObservation.remove(handler.player.getUuid());
          PersistentHudRuntime.setPresentationOwner(handler.player.getUuid(), false);
        });
    ServerTickEvents.END_SERVER_TICK.register(server->{++tick;sync.tick(server);});
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) -> {
          dispatcher.register(literal("fantasyhubperf").requires(c->c.hasPermissionLevel(2)).executes(c->{c.getSource().sendFeedback(()->Text.literal(GSON.toJson(perfSnapshot())),false);return 1;}).then(literal("reset").executes(c->{HudSyncMetrics.reset();sync.reset();return 1;})));
          RpgLifecycleQa.register(dispatcher);
          EconomyPolicyQa.register(dispatcher);
          for (String alias : List.of("classmenu", "classes", "doiclass"))
            dispatcher.register(
                literal(alias)
                    .executes(
                        ctx -> {
                          var player = ctx.getSource().getPlayerOrThrow();
                          invalidate(player.getUuid());
                          ServerPlayNetworking.send(player, new Protocol.Open("classes"));
                          return 1;
                        }));
          dispatcher.register(literal("fantasyshop").executes(c->{var p=c.getSource().getPlayerOrThrow();invalidate(p.getUuid());ServerPlayNetworking.send(p,new Protocol.Open("fantasy_shop"));return 1;})
            .then(literal("claim").executes(c->CommerceRuntime.claim(c.getSource().getPlayerOrThrow())))
            .then(literal("reload").requires(c->c.hasPermissionLevel(2)).executes(c->{try{CommerceRuntime.reload();sync.commerceReload();return 1;}catch(Exception e){c.getSource().sendError(Text.literal(e.getMessage()));return 0;}})));
          dispatcher.register(
              literal("svclass")
                  .then(
                      literal("switch")
                          .then(
                              net.minecraft.server.command.CommandManager.argument(
                                      "class",
                                      com.mojang.brigadier.arguments.StringArgumentType.word())
                                  .executes(
                                      ctx -> {
                                        var player = ctx.getSource().getPlayerOrThrow();
                                        String id =
                                            com.mojang.brigadier.arguments.StringArgumentType
                                                .getString(ctx, "class")
                                                .toUpperCase(Locale.ROOT);
                                        return ClassSwitchService.request(
                                                player,
                                                id,
                                                () -> invalidate(player.getUuid()))
                                            ? 1
                                            : 0;
                                      }))));
          dispatcher.register(
              literal("fantasyhub")
                  .executes(
                      ctx -> {
                        var player = ctx.getSource().getPlayerOrThrow();
                        invalidate(player.getUuid());
                        ServerPlayNetworking.send(player, new Protocol.Open("profile"));
                        return 1;
                      })
                  .then(
                      literal("classes")
                          .executes(
                              ctx -> {
                                var player = ctx.getSource().getPlayerOrThrow();
                                ServerPlayNetworking.send(player, new Protocol.Open("classes"));
                                return 1;
                              }))
                  .then(
                      literal("classpurchase")
                          .requires(s -> s.hasPermissionLevel(2))
                          .then(
                              net.minecraft.server.command.CommandManager.argument(
                                      "player",
                                      net.minecraft.command.argument.EntityArgumentType.player())
                                  .then(
                                      net.minecraft.server.command.CommandManager.argument(
                                              "class",
                                              com.mojang.brigadier.arguments.StringArgumentType
                                                  .word())
                                          .executes(
                                              ctx -> {
                                                var target =
                                                    net.minecraft.command.argument
                                                        .EntityArgumentType.getPlayer(
                                                        ctx, "player");
                                                return ClassSwitchService.request(
                                                        target,
                                                        com.mojang.brigadier.arguments
                                                            .StringArgumentType.getString(
                                                            ctx, "class"),
                                                        () -> invalidate(target.getUuid()))
                                                    ? 1
                                                    : 0;
                                              }))))
                  .then(
                      literal("shop")
                          .executes(
                              ctx -> {
                                var player = ctx.getSource().getPlayerOrThrow();
                                ServerPlayNetworking.send(
                                    player, new Protocol.Open("pokemon_shop"));
                                return 1;
                              }))
                  .then(
                      literal("attributes")
                          .executes(
                              ctx -> {
                                var player = ctx.getSource().getPlayerOrThrow();
                                ServerPlayNetworking.send(player, new Protocol.Open("attributes"));
                                return 1;
                              }))
                  .then(
                      literal("skills")
                          .executes(
                              ctx -> {
                                var player = ctx.getSource().getPlayerOrThrow();
                                ServerPlayNetworking.send(player, new Protocol.Open("skills"));
                                return 1;
                              }))
                  .then(
                      literal("inspect")
                          .requires(s -> s.hasPermissionLevel(2))
                          .then(
                              net.minecraft.server.command.CommandManager.argument(
                                      "player",
                                      net.minecraft.command.argument.EntityArgumentType.player())
                                  .executes(
                                      ctx -> {
                                        var player =
                                            net.minecraft.command.argument.EntityArgumentType
                                                .getPlayer(ctx, "player");
                                        var snapshot =
                                            JsonParser.parseString(state(player)).getAsJsonObject();
                                        snapshot.remove("pokemonShop");
                                        for (var row : snapshot.getAsJsonArray("skills"))
                                          row.getAsJsonObject().remove("parameters");
                                        ctx.getSource()
                                            .sendFeedback(
                                                () -> Text.literal(snapshot.toString()), false);
                                        return 1;
                                      })))
                  .then(
                      literal("hudstatus")
                          .executes(
                              ctx -> {
                                var player = ctx.getSource().getPlayerOrThrow();
                                Map<String, Object> status = new LinkedHashMap<>();
                                status.put(
                                    "build",
                                    net.fabricmc.loader.api.FabricLoader.getInstance()
                                        .getModContainer("fantasyhubcore")
                                        .orElseThrow()
                                        .getMetadata()
                                        .getVersion()
                                        .getFriendlyString());
                                status.put(
                                    "castingChannel",
                                    ServerPlayNetworking.canSend(player, Protocol.Casting.ID));
                                status.put("casting", castingState(player));
                                ctx.getSource()
                                    .sendFeedback(() -> Text.literal(GSON.toJson(status)), false);
                                return 1;
                              }))
                  .then(
                      literal("resync")
                          .requires(s -> s.hasPermissionLevel(2))
                          .executes(
                              ctx -> {
                                var player = ctx.getSource().getPlayerOrThrow();
                                SVFrameMMO.playerData().get(player).refreshClassStats();
                                invalidate(player.getUuid());
                                return 1;
                              })));
        });
  }

  private Map<String,Object> perfSnapshot(){var out=new LinkedHashMap<String,Object>(HudSyncMetrics.snapshot());out.putAll(sync.metrics());return out;}
  private static String state(ServerPlayerEntity player){return state(player,"all");}
  private static String state(ServerPlayerEntity player,String scope){return GSON.toJson(stateMap(player,scope));}
  private static Map<String,Object> stateMap(ServerPlayerEntity player,String scope) {
    long started=System.nanoTime();HudSyncMetrics.states++;
    var data = SVFrameMMO.playerData().get(player);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("schema", Protocol.VERSION);
    out.put("classId", data.getClassId());
    out.put("playerTitle",data.getPreference("fantasyhub:title",""));
    out.put("className", data.getProfess().getName());
    out.put(
        "experienceDisplay",
        vn.svframe.svframemmo.experience.vanilla.VanillaProgressionRuntime.instance()
            .experienceDisplayMode());
    if ("1".equals(System.getenv("SVFRAME_RUNTIME_QA")))
      out.put("qaScenario", RpgLifecycleQa.label(player.getUuid()));
    out.put("level", data.getLevel());
    out.put("experience", data.getExperience());
    out.put("nextLevel", data.getProfess().getExpCurve().getExperience(data, data.getLevel()));
    out.put("attributePoints", data.getAttributePoints());
    out.put("skillPoints", data.getSkillPoints());
    out.put("respecPoints", data.getAttributeReallocationPoints());
    for (var resource : PlayerResource.values()) {
      String name =
          resource == PlayerResource.STELLIUM ? "magic" : resource.name().toLowerCase(Locale.ROOT);
      out.put(name, data.getResource(resource));
      out.put(name + "Max", data.getMaxResource(resource));
    }
    // These views do not consume class/attribute/skill trees. Capture only their own domain.
    if (scope.equals("upgrades")) {
      out.clear();
      out.put("itemUpgrades", ItemUpgradeRuntime.view(player));
      return out;
    }
    if (scope.equals("pokemon")) {
      out.clear();
      var abilities = vn.svframe.svframelib.skill.PlayerAbilityShop.get();
      HudSyncMetrics.pokemon++;
      out.put("pokemonShop", abilities == null ? List.of() : abilities.catalog(player));
      return out;
    }
    List<Map<String, Object>> attributes = new ArrayList<>();
    for (var attr : SVFrameMMO.attributes().getAll()) {
      var instance = data.getAttributes().getInstance(attr);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", attr.getId());
      row.put("name", attr.getName());
      row.put("base", instance.getBase());
      row.put("total", instance.getTotal());
      row.put("max", attr.hasMax() ? attr.getMax() : -1);
      attributes.add(row);
    }
    out.put("attributes", attributes);
    out.put("classSwitch",scope.equals("classes")||scope.equals("all")?ClassSwitchService.policy(data):Map.of("busy",ClassProgression.busy(data)||ClassSwitchService.busy(data)));
    if(scope.equals("classes")||scope.equals("all")){
    List<Map<String, Object>> roster = new ArrayList<>();
    Map<vn.svframe.svframelib.api.economy.CurrencyKey, java.math.BigDecimal> classBalances =
        new HashMap<>();
    for (var type : SVFrameMMO.classes().getAll()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", type.getId());
      row.put("name", type.getName());
      var metadata = CLASS_METADATA.get(type);
      if (metadata == null) {
        if (CLASS_METADATA.size() >= 512) CLASS_METADATA.clear();
        metadata = new LinkedHashMap<>();
        metadata.put("description", List.copyOf(type.getDescription()));
        metadata.put("attributeDescription", List.copyOf(type.getAttributeDescription()));
        metadata.put("maxLevel", type.getMaxLevel());
        List<Map<String,Object>> classSkills = new ArrayList<>();
        for (var skill : type.getConfiguredSkills()) {
          var sr = new LinkedHashMap<String,Object>();
          sr.put("id", skill.getSkill().getId());
          sr.put("name", skill.getSkill().getName());
          sr.put("passive", skill.getTrigger().isPassive());
          sr.put("requiredLevel", skill.getUnlockLevel());
          skillSupport(sr, skill.getSkill());
          classSkills.add(Collections.unmodifiableMap(sr));
        }
        metadata.put("skills", List.copyOf(classSkills));
        metadata = Collections.unmodifiableMap(metadata);
        CLASS_METADATA.put(type, metadata);
      }
      row.putAll(metadata);
      row.put("price", ClassSwitchService.offer(type.getId(), player, classBalances));
      row.put("selectable", ClassSwitchService.selectable(type.getId()));
      row.put("unlocked", ClassSwitchService.unlocked(player, type.getId()));
      row.put("owned",ClassProgression.owns(data,type.getId()));row.put("unlockCost",ClassProgression.price(type.getId()));
      row.put("current", data.getClassId().equals(type.getId()));
      row.put("state",data.getClassId().equals(type.getId())?"ACTIVE":ClassProgression.owns(data,type.getId())?data.getClassSwitchAvailableAt()>System.currentTimeMillis()?"OWNED — COOLDOWN":"OWNED — READY":"LOCKED — PURCHASE AVAILABLE");
      Map<String, Double> baseStats = new TreeMap<>();
      for (String stat : type.getStats())
        baseStats.put(stat, type.calculateBaseStat(stat, 1, data));
      row.put("baseStats", baseStats);
      roster.add(row);
    }
    out.put("classRoster", roster);
    out.put("classPreviews", ClassEquipmentPreviews.snapshots(player));
    }
    List<Map<String, Object>> skills = new ArrayList<>();
    for (var entry : PlayerSkillCatalog.entries(data)) {
      var skill = entry.skill();
      Map<String, Object> row = new LinkedHashMap<>();
      skillSupport(row, skill.getSkill());
      row.put("id", entry.id());
      row.put("name", skill.getSkill().getName());
      row.put("owned", entry.learned());
      boolean learned = entry.origin() == PlayerSkillCatalog.Origin.EXTERNAL || data.hasUnlocked(skill.getUnlockNamespacedKey());
      row.put("learned", learned);
      row.put("learnCost", ClassProgression.settings().learnCost());
      row.put("canLearn", entry.origin() == PlayerSkillCatalog.Origin.CLASS && ProgressionPolicy.learnable(data.getLevel(), skill.getUnlockLevel(), data.getSkillPoints(), learned, ClassProgression.settings().learnCost()));
      row.put("level", entry.level());
      row.put("bindable", entry.bindable());
      row.put("slots", PlayerSkillCatalog.allowedSlots(data, entry));
      row.put("passive", skill.getTrigger().isPassive());
      skills.add(row);
      row.put("requiredLevel", skill.getUnlockLevel());
      row.put("maxLevel", skill.hasMaxLevel() ? skill.getMaxLevel() : 0);
      row.put("upgradable", skill.isUpgradable());
      int level = Math.max(1, entry.level());
      Map<String, Double> parameters = new TreeMap<>();
      skill
          .getParameters()
          .keySet()
          .forEach(key -> parameters.put(key, skill.getParameter(key, level, data)));
      row.put("parameters", parameters);
      row.put(
          "cooldownRemaining",
          MMOPlayerData.setup(player).getCooldownMap().getCooldown(skill.getCooldownPath()));
      double menuCooldown=MMOPlayerData.setup(player).getCooldownMap().getCooldown(skill.getCooldownPath());
      row.put("cooldownEnd",menuCooldown<=0?0:System.currentTimeMillis()+Math.round(menuCooldown*1000));
      row.put("lore", skill.getSkill().getUiLore());
      row.put(
          "iconMaterial",
          net.minecraft.registry.Registries.ITEM
              .getId(skill.getSkill().getIcon().getMaterialElse(net.minecraft.item.Items.PAPER))
              .toString());
      row.put("iconCustomModelData", skill.getSkill().getIcon().getCustomModelDataInt());
    }
    out.put("skills", skills);
    out.put("loadoutSlots", PlayerSkillCatalog.slots(data));
    if(scope.equals("all"))out.put("casting", castingState(player));
    var shop = vn.svframe.svframelib.skill.PlayerAbilityShop.get();
    if(scope.equals("pokemon")||scope.equals("all")){HudSyncMetrics.pokemon++;out.put("pokemonShop",shop==null?List.of():shop.catalog(player));}
    if(scope.equals("shop")||scope.equals("all"))out.put("fantasyShop",CommerceRuntime.view(player));
    if(scope.equals("upgrades")||scope.equals("all"))out.put("itemUpgrades",ItemUpgradeRuntime.view(player));
    Map<String, String> loadout = new LinkedHashMap<>();
    PlayerSkillCatalog.bindings(data).forEach((slot, entry) -> loadout.put(Integer.toString(slot), entry.id()));
    out.put("loadout", loadout);
    Map<String, Double> stats = new TreeMap<>();
    for (String id :
        List.of(
            "MAX_HEALTH",
            "ARMOR",
            "ARMOR_TOUGHNESS",
            "ATTACK_DAMAGE",
            "SKILL_DAMAGE",
            "MOVEMENT_SPEED")) stats.put(id, MMOPlayerData.setup(player).getStatMap().getStat(id));
    out.put("stats", stats);
    long elapsed=System.nanoTime()-started;HudSyncMetrics.stateNanos+=elapsed;HudSyncMetrics.maxStateNanos=Math.max(HudSyncMetrics.maxStateNanos,elapsed);return out;
  }

  private static Map<String, Object> castingState(ServerPlayerEntity player) {
    var data = SVFrameMMO.playerData().get(player);
    Map<String, Object> casting = new LinkedHashMap<>();
    casting.put("active", SVFrameMMO.skillBar().isCasting(player.getUuid()));
    casting.put(
        "mode", Objects.requireNonNullElse(SVFrameMMO.skillBar().mode(player.getUuid()), ""));
    List<Map<String, Object>> castingSlots = new ArrayList<>();

    for (vn.svframe.svframemmo.skill.runtime.SkillBarRuntime.SkillBarBinding binding :
        SVFrameMMO.skillBar().skillBarBindings(data)) {
      ClassSkill skill = binding.skill();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("boundSlot", binding.boundSlot());
      row.put("inputSlot", binding.inputSlot());
      row.put("temporary", binding.temporary());
      row.put("recastAvailable", SVFrameMMO.skillRuntime().isRecastAvailable(data, skill));
      row.put("id", skill.getSkill().getId());
      row.put("name", skill.getSkill().getName());
      int level = PlayerSkillCatalog.level(data, skill);
      Map<String, Double> parameters = new TreeMap<>();
      skill
          .getParameters()
          .keySet()
          .forEach(key -> parameters.put(key, skill.getParameter(key, level, data)));
      row.put("parameters", parameters);
      row.put(
          "cooldownRemaining",
          data.getMMOPlayerData().getCooldownMap().getCooldown(skill.getCooldownPath()));
      row.put(
          "iconMaterial",
          Registries.ITEM
              .getId(skill.getSkill().getIcon().getMaterialElse(Items.PAPER))
              .toString());
      row.put("iconCustomModelData", skill.getSkill().getIcon().getCustomModelDataInt());
      castingSlots.add(row);
    }

    casting.put("slots", castingSlots);
    return casting;
  }

  private static Map<String, Object> hudState(ServerPlayerEntity player) {
    var data = SVFrameMMO.playerData().get(player);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("schema", Protocol.VERSION);
    out.put("casting", castingState(player));
    out.put("classId",data.getClassId());out.put("className",data.getProfess().getName());out.put("playerTitle",data.getPreference("fantasyhub:title",""));out.put("attributePoints",data.getAttributePoints());out.put("skillPoints",data.getSkillPoints());
    out.put("riderStatus", RiderRuntime.uiState(player));
    out.put("heroStatus",vn.svframe.fantasyhub.core.hero.HeroRuntime.inspect(player));
    out.put(
        "experienceDisplay",
        vn.svframe.svframemmo.experience.vanilla.VanillaProgressionRuntime.instance()
            .experienceDisplayMode());
    out.put("level", data.getLevel());
    out.put("experience", data.getExperience());
    out.put("nextLevel", data.getProfess().getExpCurve().getExperience(data, data.getLevel()));
    for (var resource : PlayerResource.values()) {
      String name =
          resource == PlayerResource.STELLIUM ? "magic" : resource.name().toLowerCase(Locale.ROOT);
      out.put(name, data.getResource(resource));
      out.put(name + "Max", data.getMaxResource(resource));
    }
    return out;
  }

  private static void skillSupport(
      Map<String, Object> row, vn.svframe.svframelib.skill.handler.SkillHandler<?> handler) {
    if (handler instanceof vn.svframe.svframelib.skill.NativeSkillProviders.BackedHandler backed) {
      boolean available =
          vn.svframe.svframelib.skill.NativeSkillProviders.available(
              backed.providerNamespace(), backed.sourceSkill());
      row.put("available", available);
      row.put(
          "issues",
          available
              ? List.of()
              : vn.svframe.svframelib.skill.NativeSkillProviders.issues(
                  backed.providerNamespace(), backed.sourceSkill()));
    } else {
      row.put("available", true);
      row.put("issues", List.of());
    }
  }
}
