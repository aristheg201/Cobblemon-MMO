package vn.svframe.fantasyhub.core;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.fantasyhub.core.sync.*;
import vn.svframe.fantasyhub.protocol.Protocol;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.*;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;

/**
 * Capture/dispatch owner. All methods except the UUID-only signal mailbox run on the server thread.
 */
final class NativeSyncRuntime {
  static final class Settings {
    int workers = 2,
        queueCapacity = 64,
        maxCapturesPerTick = 16,
        maxGuiCapturesPerTick = 2,
        mainBudgetMicros = 2000,
        vitalsIntervalTicks = 5,
        combatIntervalTicks = 5,
        extensionIntervalTicks = 5,
        balanceRefreshTicks = 20;

    void validate() {
      if (workers < 1
          || workers > 4
          || queueCapacity < 16
          || queueCapacity > 256
          || maxCapturesPerTick < 4
          || maxCapturesPerTick > 128
          || maxGuiCapturesPerTick < 1
          || maxGuiCapturesPerTick > 8
          || mainBudgetMicros < 500
          || mainBudgetMicros > 10000
          || vitalsIntervalTicks < 1
          || vitalsIntervalTicks > 5
          || combatIntervalTicks < 1
          || combatIntervalTicks > 5
          || extensionIntervalTicks < 1
          || extensionIntervalTicks > 20
          || balanceRefreshTicks < 20
          || balanceRefreshTicks > 100)
        throw new IllegalArgumentException("Invalid FantasyHub sync budgets");
    }
  }

  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("FantasyHub/Sync");
  private final BiFunction<ServerPlayerEntity, String, Map<String, Object>> uiCapture;
  private final Map<UUID, Session> sessions = new LinkedHashMap<>();
  private final UiSyncGate views = new UiSyncGate();
  private SnapshotEngine engine;
  private Settings settings = new Settings();
  private static final Map<String, Object> WARM_PENDING = Map.of("_warmPending", true);
  private long tickStartedAt;
  private long tick, itemRevision = -1, catalogRevision = -1;
  private final SyncTimings captureTimes = new SyncTimings(),
      tickTimes = new SyncTimings(),
      serializeTimes = new SyncTimings(),
      latencyTimes = new SyncTimings(),
      hudCaptureTimes = new SyncTimings(),
      hudSerializeTimes = new SyncTimings();
  private long packets, packetChars, wireBytes, unchanged, failures, hudUpdates, definitionHits, definitionBuilds, catalogReuses;
  private final EnumMap<Domain, Long> domainCaptures = new EnumMap<>(Domain.class);
  private static final PlayerResource[] RESOURCES = PlayerResource.values();
  private static final Domain[] DOMAINS = Domain.values();

  private static final class Session {
    final double[] observedVitals = new double[9];
    boolean vitalsSeen, observedActive;
    String observedMode = "";
    long[] observedEnds = new long[0];
    boolean[] observedRecast = new boolean[0];
    Object observedExtensions;
    Map<String, Object> observedBalance;
    boolean balanceInvalidated = true;
    FrozenValues.Shared pokemonDefinitions, profileDetails;
    long dueVitals, dueCombat, dueExtensions, dueBalance, dueInventory;
    int inventory = -1;
    Object connection; // Main-thread identity only; never part of a snapshot.
    String world = "", mode = "", openScope = "";
    Object lastVitals,
        lastCombat,
        lastExtensions,
        lastBalance,
        capturedVitals,
        capturedCombat,
        capturedExtensions,
        capturedBalance;
    boolean definitionsDirty = true, definitionsSent, loadingNoticeSent, catalogDirty = true;
    List<vn.svframe.svframemmo.skill.runtime.SkillBarRuntime.SkillBarBinding> bindings = List.of();
    Map<String, Object> definitions = Map.of();
    final Map<String, Long> deadlines = new HashMap<>();
    long sentCatalog = -1;
  }

  NativeSyncRuntime(BiFunction<ServerPlayerEntity, String, Map<String, Object>> capture) {
    uiCapture = capture;
  }

  void install() {
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          try {
            var path =
                net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("FantasyHub/sync.json");
            var gson = new GsonBuilder().setPrettyPrinting().create();
            if (!Files.exists(path)) Files.writeString(path, gson.toJson(settings) + "\n");
            else settings = gson.fromJson(Files.readString(path), Settings.class);
            settings.validate();
          } catch (Exception e) {
            LOG.error("Invalid sync.json; using safe default budgets", e);
            settings = new Settings();
          }
          engine = new SnapshotEngine(settings.workers, settings.queueCapacity);
        });
    ServerLifecycleEvents.SERVER_STOPPING.register(
        server -> {
          if (engine != null) engine.close();
          sessions.clear();
          views.clear();
          SyncSignals.clear();
          FantasyHubCore.clearPresentationDefinitions();
          ClassEquipmentPreviews.clear();
        });
    ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
      UUID id=handler.player.getUuid();
      server.execute(() -> {
        var current=server.getPlayerManager().getPlayer(id);
        if(current==null || !current.networkHandler.isConnectionOpen())leave(id);
      });
    });
    net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
        (oldPlayer, newPlayer, alive) -> {
          leave(newPlayer.getUuid());
          join(newPlayer);
        });
    ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
        (player, oldWorld, newWorld) -> {
          String scope = views.scope(player.getUuid(), tick);
          leave(player.getUuid());
          join(player);
          if (scope != null) open(player.getUuid(), scope);
        });
    PlayerEnterCastingModeEvent.EVENT.register(
        e -> SyncSignals.changed(e.getData().getUniqueId(), Domain.COMBAT));
    PlayerExitCastingModeEvent.EVENT.register(
        e -> SyncSignals.changed(e.getData().getUniqueId(), Domain.COMBAT));
    PlayerLevelChangeEvent.EVENT.register(
        e ->
            SyncSignals.changed(
                e.getData().getUniqueId(),
                Domain.CLASS,
                Domain.COMBAT,
                Domain.GUI,
                Domain.BUNDLES));
    vn.svframe.svframelib.api.event.skill.PlayerCastSkillEvent.EVENT.register(
        e -> {
          if (e.getPlayer() != null)
            SyncSignals.changed(e.getPlayer().getUuid(), Domain.COMBAT, Domain.VITALS);
        });
  }

  private void join(ServerPlayerEntity p) {
    var id = p.getUuid();
    var s = new Session();
    s.connection=p.networkHandler;
    s.world = p.getWorld().getRegistryKey().getValue().toString();
    long phase = Math.floorMod(id.hashCode(), 5);
    s.dueVitals = tick + phase;
    s.dueCombat = tick + phase;
    s.dueExtensions = tick + phase;
    s.dueBalance = tick + phase;
    sessions.put(id, s);
    engine.join(id);
    SyncSignals.join(id);
    changed(id, Domain.CLASS, Domain.EQUIPMENT, Domain.COMBAT, Domain.VITALS, Domain.EXTENSIONS);
  }

  private void leave(UUID id) {
    sessions.remove(id);
    views.remove(id);
    SyncSignals.leave(id);
    if (engine != null) engine.leave(id);
  }

  void changed(UUID id, Domain... domains) {
    for (var d : domains) {
      var s = sessions.get(id);
      if (s == null || engine == null) continue;
      if (d == Domain.COMMERCE) s.balanceInvalidated = true;
      if (d == Domain.BUNDLES) s.catalogDirty = true;
      if (d == Domain.CLASS || d == Domain.EQUIPMENT || d == Domain.GUI) {
        s.pokemonDefinitions = null;
        s.profileDetails = null;
      }
      if (d == Domain.CLASS || d == Domain.EQUIPMENT) {
        s.definitionsDirty = true;
        s.definitionsSent = false;
      }
      if ((d == Domain.GUI && views.scope(id, tick) == null)
          || (d == Domain.BUNDLES || d == Domain.COMMERCE) && !"shop".equals(views.scope(id, tick)))
        continue;
      engine.dirty(id, d);
    }
  }

  void invalidate(UUID id) {
    changed(
        id,
        Domain.CLASS,
        Domain.EQUIPMENT,
        Domain.COMBAT,
        Domain.VITALS,
        Domain.COMMERCE,
        Domain.GUI,
        Domain.BUNDLES);
  }

  void invalidateAll() {
    FantasyHubCore.clearPresentationDefinitions();
    for (var id : sessions.keySet()) invalidate(id);
  }

  void commerceReload() {
    catalogRevision=CommerceRuntime.revision();
    for(UUID id:sessions.keySet()) {
      changed(id,Domain.COMMERCE,Domain.BUNDLES);
      if("classes".equals(views.scope(id,tick)))changed(id,Domain.GUI);
    }
  }

  void open(UUID id, String scope) {
    if (!views.open(id, scope, tick)) return;
    cancelView(id);
    var session = sessions.get(id);
    if (session != null) session.openScope = scope;
    if ("shop".equals(scope)) {
      if (session != null && (session.catalogDirty || session.sentCatalog != CommerceRuntime.previewRevision()))
        engine.dirty(id, Domain.BUNDLES);
      changed(id, Domain.COMMERCE);
    } else if (engine != null) engine.dirty(id, Domain.GUI);
  }

  void close(UUID id, String scope) {
    if (views.close(id, scope)) {
      cancelView(id);
    }
  }

  private void cancelView(UUID id) {
    if (engine == null) return;
    engine.cancel(id, Domain.GUI);
    engine.cancel(id, Domain.BUNDLES);
    engine.cancel(id, Domain.COMMERCE);
    var s = sessions.get(id);
    if (s != null) s.openScope = "";
  }

  void touch(ServerPlayerEntity p) {
    views.touch(p.getUuid(), tick);
    String s = views.scope(p.getUuid(), tick);
    if ("shop".equals(s)) CommerceRuntime.keepAlive(p);
    if ("upgrades".equals(s)) ItemUpgradeRuntime.keepAlive(p);
  }

  private boolean probeVitals(ServerPlayerEntity p, Session s) {
    var d = SVFrameMMO.playerData().get(p);
    boolean changed = !s.vitalsSeen;
    int i = 0;
    for (var r : RESOURCES) {
      double v = d.getResource(r), max = d.getMaxResource(r);
      if (Double.compare(s.observedVitals[i], v) != 0) changed = true;
      s.observedVitals[i++] = v;
      if (Double.compare(s.observedVitals[i], max) != 0) changed = true;
      s.observedVitals[i++] = max;
    }
    double exp = d.getExperience();
    if (Double.compare(s.observedVitals[i], exp) != 0) changed = true;
    s.observedVitals[i] = exp;
    s.vitalsSeen = true;
    return changed;
  }

  private boolean probeCombat(ServerPlayerEntity p, Session s) {
    if (s.definitionsDirty) return !engine.pending(p.getUuid(), Domain.COMBAT);
    boolean active = SVFrameMMO.skillBar().isCasting(p.getUuid());
    String mode = Objects.requireNonNullElse(SVFrameMMO.skillBar().mode(p.getUuid()), "");
    boolean changed = active != s.observedActive || !mode.equals(s.observedMode);
    s.observedActive = active;
    s.observedMode = mode;
    if (!active && !changed) return false;
    if (s.observedEnds.length != s.bindings.size()) {
      s.observedEnds = new long[s.bindings.size()];
      s.observedRecast = new boolean[s.bindings.size()];
      changed = true;
    }
    var d = SVFrameMMO.playerData().get(p);
    long now = System.currentTimeMillis();
    int i = 0;
    for (var b : s.bindings) {
      var skill = b.skill();
      double left = d.getMMOPlayerData().getCooldownMap().getCooldown(skill.getCooldownPath());
      long end = left <= 0 ? 0 : Math.round((now + left * 1000) / 50) * 50;
      long old = s.observedEnds[i];
      if (end > 0 && old > now && Math.abs(end - old) < 150) end = old;
      boolean recast = SVFrameMMO.skillRuntime().isRecastAvailable(d, skill);
      if (end != old || recast != s.observedRecast[i]) changed = true;
      s.observedEnds[i] = end;
      s.observedRecast[i++] = recast;
    }
    return changed;
  }

  private Map<String, Object> vitals(ServerPlayerEntity p, Session s) {
    var d = SVFrameMMO.playerData().get(p);
    var values = new ArrayList<Double>(9);
    for (var r : RESOURCES) {
      values.add(d.getResource(r));
      values.add(d.getMaxResource(r));
    }
    values.add(d.getExperience());
    if (values.equals(s.lastVitals)) return null;
    s.capturedVitals = List.copyOf(values);
    var m = new LinkedHashMap<String, Object>();
    int i = 0;
    for (var r : RESOURCES) {
      String key = r == PlayerResource.STELLIUM ? "magic" : r.name().toLowerCase(Locale.ROOT);
      m.put(key, values.get(i++));
      m.put(key + "Max", values.get(i++));
    }
    m.put("experience", d.getExperience());
    return m;
  }

  private Map<String, Object> classInfo(ServerPlayerEntity p) {
    var d = SVFrameMMO.playerData().get(p);
    return Map.of(
        "classId",
        d.getClassId(),
        "className",
        d.getProfess().getName(),
        "level",
        d.getLevel(),
        "attributePoints",
        d.getAttributePoints(),
        "skillPoints",
        d.getSkillPoints(),
        "playerTitle",
        d.getPreference("fantasyhub:title", ""),
        "nextLevel",
        d.getProfess().getExpCurve().getExperience(d, d.getLevel()),
        "experienceDisplay",
        vn.svframe.svframemmo.experience.vanilla.VanillaProgressionRuntime.instance()
            .experienceDisplayMode());
  }

  private Map<String, Object> combat(ServerPlayerEntity p, Session s) {
    var d = SVFrameMMO.playerData().get(p);
    String mode = Objects.requireNonNullElse(SVFrameMMO.skillBar().mode(p.getUuid()), "");
    if (!mode.equals(s.mode)) {
      s.mode = mode;
      s.definitionsDirty = true;
      s.definitionsSent = false;
    }
    if (s.definitionsDirty) {
      s.bindings = List.copyOf(SVFrameMMO.skillBar().skillBarBindings(d));
      var defs = new LinkedHashMap<String, Object>();
      for (var b : s.bindings) {
        var skill = b.skill();
        var params = new LinkedHashMap<String, Double>();
        int level = PlayerSkillCatalog.level(d, skill);
        for (String key : skill.getParameters().keySet())
          params.put(key, skill.getParameter(key, level, d));
        var def = new LinkedHashMap<String, Object>();
        def.put("id", skill.getSkill().getId());
        def.put("name", skill.getSkill().getName());
        def.put("parameters", params);
        def.put(
            "iconMaterial",
            Registries.ITEM
                .getId(skill.getSkill().getIcon().getMaterialElse(Items.PAPER))
                .toString());
        def.put("iconCustomModelData", skill.getSkill().getIcon().getCustomModelDataInt());
        defs.put(skill.getSkill().getId(), def);
      }
      s.definitions = defs;
      s.definitionsDirty = false;
    }
    long now = System.currentTimeMillis();
    var rows = new ArrayList<Map<String, Object>>();
    for (var b : s.bindings) {
      var skill = b.skill();
      String id = skill.getSkill().getId();
      double remaining = d.getMMOPlayerData().getCooldownMap().getCooldown(skill.getCooldownPath());
      long end = remaining <= 0 ? 0 : Math.round((now + remaining * 1000) / 50) * 50;
      long old = s.deadlines.getOrDefault(id, 0L);
      if (end > 0 && old > now && Math.abs(end - old) < 150) end = old;
      s.deadlines.put(id, end);
      rows.add(
          Map.of(
              "id",
              id,
              "boundSlot",
              b.boundSlot(),
              "inputSlot",
              b.inputSlot(),
              "temporary",
              b.temporary(),
              "cooldownEnd",
              end,
              "recastAvailable",
              SVFrameMMO.skillRuntime().isRecastAvailable(d, skill)));
    }
    var dynamic =
        Map.of("active", SVFrameMMO.skillBar().isCasting(p.getUuid()), "mode", mode, "slots", rows);
    if (s.definitionsSent && dynamic.equals(s.lastCombat)) return null;
    s.capturedCombat = dynamic;
    var out = new LinkedHashMap<String, Object>();
    out.put("combatState", dynamic);
    if (!s.definitionsSent) out.put("skillDefinitions", s.definitions);
    return out;
  }

  private Map<String, Object> capture(ServerPlayerEntity p, Session s, Domain domain) {
    return switch (domain) {
      case VITALS -> vitals(p, s);
      case COMBAT -> combat(p, s);
      case CLASS -> classInfo(p);
      case EQUIPMENT -> {
        var stats = new LinkedHashMap<String, Double>();
        for (String id :
            List.of(
                "MAX_HEALTH",
                "ARMOR",
                "ARMOR_TOUGHNESS",
                "ATTACK_DAMAGE",
                "SKILL_DAMAGE",
                "MOVEMENT_SPEED"))
          stats.put(
              id, vn.svframe.svframelib.api.player.MMOPlayerData.setup(p).getStatMap().getStat(id));
        yield Map.of("stats", stats);
      }
      case EXTENSIONS -> {
        Map<String, Object> data =
            Map.of(
                "riderStatus",
                RiderRuntime.uiState(p),
                "heroStatus",
                vn.svframe.fantasyhub.core.hero.HeroRuntime.hudState(p));
        if (data.equals(s.lastExtensions)) yield null;
        s.capturedExtensions = data;
        yield data;
      }
      case COMMERCE -> {
        var status =
            s.balanceInvalidated || s.observedBalance == null
                ? CommerceRuntime.status(p)
                : s.observedBalance;
        s.observedBalance = status;
        s.balanceInvalidated = false;
        if (status.equals(s.lastBalance)) yield null;
        s.capturedBalance = status;
        yield Map.of("shopStatus", status);
      }
      case BUNDLES -> {
        if (!"shop".equals(views.scope(p.getUuid(), tick))) yield null;
        if (!CommerceRuntime.preparePreviews(
            p,
            Math.max(0, settings.mainBudgetMicros * 1000L - (System.nanoTime() - tickStartedAt)),
            4)) yield s.loadingNoticeSent ? WARM_PENDING : Map.of("catalogLoading", true);
        var data = new LinkedHashMap<String, Object>();
        var shop = CommerceRuntime.capture(p);
        for (String key :
            List.of(
                "beastBalance",
                "hunterBalance",
                "beastAvailable",
                "hunterAvailable",
                "activeClass",
                "switchAvailableAt",
                "freeChoice",
                "bypassBeast",
                "mailCount",
                "mailAvailable",
                "switchRemainingSeconds")) shop.remove(key);
        data.put("fantasyShop", shop);
        data.put("catalogLoading", false);
        if (s.sentCatalog != CommerceRuntime.previewRevision())
          data.put("shopPreviews", CommerceRuntime.previewTable());
        yield data;
      }
      case GUI -> {
        String scope = views.scope(p.getUuid(), tick);
        if (scope == null || scope.equals("shop")) yield null;
        if (scope.equals("classes")
            && !ClassEquipmentPreviews.prepare(
                p,
                Math.max(
                    0, settings.mainBudgetMicros * 1000L - (System.nanoTime() - tickStartedAt))))
          yield WARM_PENDING;
        if (scope.equals("profile") && s.profileDetails != null) {
          definitionHits++;
          yield Map.of("_profile", s.profileDetails);
        }
        if (scope.equals("pokemon") && s.pokemonDefinitions != null) {
          definitionHits++;
          yield Map.of("pokemonShop", s.pokemonDefinitions);
        }
        definitionBuilds++;
        var detail = uiCapture.apply(p, scope);
        if (scope.equals("pokemon")) {
          s.pokemonDefinitions = FrozenValues.share(detail.get("pokemonShop"));
          yield Map.of("pokemonShop", s.pokemonDefinitions);
        }
        // An older GUI result must never overwrite newer realtime domain values.
        detail
            .keySet()
            .retainAll(
                Set.of(
                    "attributes",
                    "respecPoints",
                    "skills",
                    "loadoutSlots",
                    "loadout",
                    "classSwitch",
                    "classRoster",
                    "classPreviews",
                    "itemUpgrades",
                    "pokemonShop",
                    "qaScenario"));
        if (scope.equals("profile")) {
          s.profileDetails = FrozenValues.share(detail);
          yield Map.of("_profile", s.profileDetails);
        }
        yield detail;
      }
    };
  }

  void tick(MinecraftServer server) {
    if (engine == null) return;
    long started = System.nanoTime();
    tickStartedAt = started;
    tick++;
    long ir = vn.svframe.svframeitems.SVFrameItems.registry().revision(),
        cr = CommerceRuntime.revision();
    if (ir != itemRevision) {
      itemRevision=ir;
      invalidateAll();
    }
    if(cr!=catalogRevision)commerceReload();
    List<UUID> departed=null;
    for(UUID id:sessions.keySet())if(server.getPlayerManager().getPlayer(id)==null) {
      if(departed==null)departed=new ArrayList<>();
      departed.add(id);
    }
    if(departed!=null)for(UUID id:departed)leave(id);
    int balanceReads = 0;
    for (var p : server.getPlayerManager().getPlayerList()) {
      if (!ServerPlayNetworking.canSend(p, Protocol.Sync.ID)) continue;
      var id = p.getUuid();
      var existing=sessions.get(id);
      if(existing==null || existing.connection!=p.networkHandler) {
        leave(id);
        join(p);
      }
      var s = sessions.get(id);
      int bits = SyncSignals.take(id);
      for (var domain : DOMAINS) if ((bits & (1 << domain.ordinal())) != 0) changed(id, domain);
      if (tick >= s.dueVitals) {
        s.dueVitals = tick + settings.vitalsIntervalTicks;
        if (probeVitals(p, s)) changed(id, Domain.VITALS);
      }
      if (tick >= s.dueCombat) {
        s.dueCombat = tick + settings.combatIntervalTicks;
        if (probeCombat(p, s)) changed(id, Domain.COMBAT);
      }
      if (tick >= s.dueExtensions) {
        s.dueExtensions = tick + settings.extensionIntervalTicks;
        var ext =
            Map.of(
                "riderStatus",
                RiderRuntime.uiState(p),
                "heroStatus",
                vn.svframe.fantasyhub.core.hero.HeroRuntime.hudState(p));
        if (!ext.equals(s.observedExtensions)) {
          s.observedExtensions = ext;
          changed(id, Domain.EXTENSIONS);
        }
      }
      String scope = views.scope(id, tick);
      if (scope == null && !s.openScope.isEmpty()) cancelView(id);
      if (scope != null && tick >= s.dueInventory) {
        s.dueInventory = tick + 20;
        int revision = p.getInventory().getChangeCount();
        if (s.inventory != revision) {
          s.inventory = revision;
          if (scope.equals("upgrades") || scope.equals("profile")) changed(id, Domain.GUI);
        }
      }
      if ("shop".equals(scope)
          && tick >= s.dueBalance
          && balanceReads < 2
          && System.nanoTime() - started < settings.mainBudgetMicros * 1000L) {
        balanceReads++;
        s.dueBalance = tick + settings.balanceRefreshTicks;
        var balance = CommerceRuntime.status(p);
        if (!balance.equals(s.observedBalance)) {
          s.observedBalance = balance;
          s.balanceInvalidated = false;
          engine.dirty(id, Domain.COMMERCE);
        }
        if (s.sentCatalog != CommerceRuntime.previewRevision()) changed(id, Domain.BUNDLES);
      }
    }
    SnapshotEngine.Result result;
    for (int i = 0; i < 128 && (result = engine.completed()) != null; i++) {
      var tag = result.stamp();
      var p = server.getPlayerManager().getPlayer(tag.player());
      if (result.error() != null) {
        failures++;
        LOG.error(
            "Snapshot worker failed for {} domain {}: {}",
            tag.player(),
            tag.domain(),
            result.error());
        continue;
      }
      if (p == null || !ServerPlayNetworking.canSend(p, Protocol.Sync.ID)) continue;
      var s = sessions.get(tag.player());
      switch (tag.domain()) {
        case VITALS -> s.lastVitals = s.capturedVitals;
        case COMBAT -> {
          s.lastCombat = s.capturedCombat;
          s.definitionsSent = true;
        }
        case EXTENSIONS -> s.lastExtensions = s.capturedExtensions;
        case COMMERCE -> s.lastBalance = s.capturedBalance;
        default -> {}
      }
      if (tag.domain() == Domain.BUNDLES) {
        if (result.json().contains("\"catalogLoading\":true")) {
          s.loadingNoticeSent = true;
          changed(p.getUuid(), Domain.BUNDLES);
        } else {
          s.sentCatalog = CommerceRuntime.previewRevision();
          s.catalogDirty = false;
          s.loadingNoticeSent = false;
        }
      }
      serializeTimes.add(result.nanos());
      if(tag.domain().ordinal()<=Domain.EXTENSIONS.ordinal()) {
        hudUpdates++;
        hudSerializeTimes.add(result.nanos());
      }
      latencyTimes.add(System.nanoTime() - tag.capturedAtNanos());
      ServerPlayNetworking.send(
          p,
          new Protocol.Sync(
              tag.player().toString(),
              tag.generation(),
              tag.revision(),
              tag.sequence(),
              tag.domain().name(),
              System.currentTimeMillis(),
              result.encoded()));
      packets++;
      packetChars += result.json().length();
      wireBytes += result.encoded().length;
    }
    int gui = 0;
    for (int i = 0; i < settings.maxCapturesPerTick; i++) {
      if (i > 0 && System.nanoTime() - started > settings.mainBudgetMicros * 1000L) break;
      var task = engine.poll();
      if (task == null) break;
      if (task.domain().ordinal() >= Domain.GUI.ordinal()
          && ++gui > settings.maxGuiCapturesPerTick) {
        engine.abandon(task);
        break;
      }
      var p = server.getPlayerManager().getPlayer(task.player());
      if (p == null) {
        engine.skip(task);
        continue;
      }
      long at = System.nanoTime();
      try {
        var data = capture(p, sessions.get(task.player()), task.domain());
        if (data == null) {
          unchanged++;
          engine.skip(task);
        } else if (data == WARM_PENDING) {
          engine.abandon(task);
        } else {
          engine.submit(task, data);
          domainCaptures.merge(task.domain(), 1L, Long::sum);
        }
      } catch (RuntimeException e) {
        engine.skip(task);
        failures++;
        LOG.error("Snapshot capture failed for {} domain {}", task.player(), task.domain(), e);
      }
      long captureNanos=System.nanoTime()-at;
      captureTimes.add(captureNanos);
      if(task.domain().ordinal()<=Domain.EXTENSIONS.ordinal())hudCaptureTimes.add(captureNanos);
    }
    tickTimes.add(System.nanoTime() - started);
  }

  Map<String, Object> metrics() {
    var m = new LinkedHashMap<String, Object>();
    m.put("pipeline", engine == null ? Map.of() : engine.metrics());
    m.put("capture", captureTimes.view());
    m.put("hudCapture",hudCaptureTimes.view());
    m.put("hudSerialization",hudSerializeTimes.view());
    m.put("hudUpdates",hudUpdates);
    m.put("definitionCacheHits",definitionHits);
    m.put("definitionCacheBuilds",definitionBuilds);
    m.put("serialization", serializeTimes.view());
    m.put("tickWork", tickTimes.view());
    m.put("captureToDispatch", latencyTimes.view());
    m.put("commerce", CommerceRuntime.metrics());
    m.put("connectedPlayers", sessions.size());
    m.put("packets", packets);
    m.put("packetChars", packetChars);
    m.put("wirePayloadBytes", wireBytes);
    m.put("unchangedBeforeSerialization", unchanged);
    m.put("captureErrors", failures);
    var domains = new LinkedHashMap<String, Long>();
    domainCaptures.forEach((d, n) -> domains.put(d.name(), n));
    m.put("domains", domains);
    return m;
  }

  void reset() {
    if (engine != null) engine.reset();
    captureTimes.reset();
    hudCaptureTimes.reset();
    hudSerializeTimes.reset();
    hudUpdates=definitionHits=definitionBuilds=catalogReuses=0;
    tickTimes.reset();
    serializeTimes.reset();
    latencyTimes.reset();
    CommerceRuntime.resetMetrics();
    packets = packetChars = wireBytes = unchanged = failures = 0;
    domainCaptures.clear();
  }
}
