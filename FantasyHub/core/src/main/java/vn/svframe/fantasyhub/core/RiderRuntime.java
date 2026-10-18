package vn.svframe.fantasyhub.core;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.*;
import net.minecraft.item.*;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import vn.svframe.fantasyhub.core.rider.*;
import vn.svframe.fantasyhub.protocol.*;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.damage.*;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.fabric.ReferenceClassSkillRuntime;
import vn.svframe.svframelib.player.PlayerMetadata;
import vn.svframe.svframelib.player.modifier.*;
import vn.svframe.svframelib.skill.*;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.util.configobject.MapConfigObject;
import vn.svframe.svframemmo.SVFrameMMO;

/** Native Rider abilities share the MMO owner for progression, resources and cancellable casts. */
public final class RiderRuntime {
  static final Map<UUID, Session> SESSIONS = new HashMap<>();
  private static final Map<UUID, LivingEntity> SUNDERED = new HashMap<>();
  private static final Identifier FORM = Identifier.of("fantasyhub", "rider_form");
  private static final Identifier MARK = Identifier.of("fantasyhub", "zolda_targeted");
  private static final Identifier SLOW = Identifier.of("fantasyhub", "zolda_rocket_slow");
  private static final Map<UUID, Slow> SLOWS = new HashMap<>();
  static long tick;
  private static Map<String, Object> settings = Map.of();
  static final Map<String, List<String>> SKILLS =
      Map.of(
          "AGITO",
              List.of(
                  "HENSHIN",
                  "GROUND_SMASH",
                  "FLAME_SABER",
                  "STORM_HALBERD",
                  "TRINITY",
                  "RIDER_KICK",
                  "SHINING"),
          "ZOLDA",
              List.of(
                  "HENSHIN",
                  "MAGNA_SHOT",
                  "STRIKE_VENT",
                  "SHOOT_VENT",
                  "GIGA_CANNONS",
                  "GUARD_VENT",
                  "FINAL_VENT"),
          "RYUKI",
              List.of(
                  "HENSHIN",
                  "STRIKE_VENT",
                  "SWORD_VENT",
                  "GUARD_VENT",
                  "ADVENT",
                  "SURVIVE",
                  "SHOOT_VENT",
                  "FINAL_VENT"),
          "KNIGHT",
              List.of(
                  "HENSHIN",
                  "SWORD_VENT",
                  "NASTY_VENT",
                  "TRICK_VENT",
                  "GUARD_VENT",
                  "SURVIVE",
                  "BLAST_VENT",
                  "FINAL_VENT"),
          "DECADE",
              List.of(
                  "HENSHIN",
                  "SLASH_BLAST",
                  "ILLUSION",
                  "CLOCK_UP",
                  "HEISEI_SPIRIT",
                  "COMPLETE",
                  "ULTIMATE_SUMMON",
                  "DIMENSION_KICK"));

  private record Slow(ServerPlayerEntity actor, LivingEntity target, long until) {}

  private record Prepared(boolean isSuccessful, LivingEntity target, Vec3d point)
      implements SkillResult {}

  static final class Session {
    final ServerPlayerEntity player;
    final ServerWorld world;
    final String type;
    final AgitoState agito = new AgitoState();
    final ZoldaState zolda = new ZoldaState();
    final CardRiderState cards = new CardRiderState();
    final List<StatModifier> stats = new ArrayList<>();
    Protocol.Rider sent;
    String applied = "";
    int generation;
    boolean executing, shiningKickUsed;
    CombatAction action;
    net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity summon;
    long groundUntil, ultimateUntil;
    int activeProjectiles, projectileSequence;

    Session(ServerPlayerEntity p, String type) {
      player = p;
      world = p.getServerWorld();
      this.type = type;
      RiderPreferences.restore(this);
    }

    boolean active() {
      return switch (type) {
        case "AGITO" -> agito.transformed();
        case "ZOLDA" -> zolda.transformed;
        default -> cards.transformed;
      };
    }

    String form() {
      return !active()
          ? ""
          : type.equals("AGITO")
              ? "agito_" + agito.form(tick).name().toLowerCase(Locale.ROOT)
              : type.equals("ZOLDA") ? "zolda" : type.toLowerCase(Locale.ROOT);
    }
  }

  static final class CombatAction {
    boolean awarded, empowered;
    double power = 1;
    boolean artillery, rapid, trueDamage, suppressive, guaranteedCritical;
    double armorPierce;
  }

  private RiderRuntime() {}

  public static boolean transformed(LivingEntity e) {
    var s = SESSIONS.get(e.getUuid());
    return s != null && s.active();
  }

  public static boolean controlImmune(LivingEntity e) {
    var s = SESSIONS.get(e.getUuid());
    return s != null
        && valid(s)
        && s.active()
        && (s.zolda.guardUntil > tick
            || s.type.equals("AGITO") && s.agito.form(tick) == AgitoState.Form.SHINING
            || s.type.equals("DECADE") && s.cards.elevated && flag(s, "cc-immunity-stun"));
  }

  public static boolean knockbackImmune(LivingEntity e) {
    var s = SESSIONS.get(e.getUuid());
    if (s != null && s.type.equals("DECADE"))
      return valid(s) && s.active() && s.cards.elevated && flag(s, "cc-immunity-knockback");
    return controlImmune(e);
  }

  public static boolean statusImmune(
      LivingEntity e, net.minecraft.registry.entry.RegistryEntry<StatusEffect> effect) {
    var s = SESSIONS.get(e.getUuid());
    if (s != null && valid(s) && s.active() && s.type.equals("DECADE") && s.cards.elevated) {
      if (effect.equals(StatusEffects.SLOWNESS)) return flag(s, "cc-immunity-slow");
      if (effect.equals(StatusEffects.BLINDNESS)) return flag(s, "cc-immunity-blindness");
    }
    return effect.equals(StatusEffects.SLOWNESS) && controlImmune(e);
  }

  static void install() {
    reload();
    RiderSummons.install();
    BeltEquipment.allow(
        Protocol.agitoBelt, e -> !(e instanceof ServerPlayerEntity p) || type(p).equals("AGITO"));
    BeltEquipment.allow(
        Protocol.zoldaBelt, e -> !(e instanceof ServerPlayerEntity p) || type(p).equals("ZOLDA"));
    for (String cardType : List.of("RYUKI", "KNIGHT", "DECADE"))
      BeltEquipment.allow(
          belt(cardType), e -> !(e instanceof ServerPlayerEntity p) || type(p).equals(cardType));
    CardRiderSkills.install();
    vn.svframe.svframeitems.runtime.EquipmentProviderRegistry.register(
        p -> {
          var stack = BeltEquipment.equippedAny(p);
          return !stack.isEmpty() && !stack.isOf(Protocol.zecterBelt)
              ? List.of(
                  new vn.svframe.svframeitems.runtime.EquipmentProvider.EquippedItem(
                      "fantasyhub:rider_belt",
                      stack,
                      vn.svframe.svframelib.fabric.runtime.NativeStatEngine.EquipmentSlot
                          .ACCESSORY))
              : List.of();
        });
    SVFrameMMO.skillRuntime()
        .registerRecastWindow(
            "fantasyhub:riders",
            (data, skill) -> {
              var s = SESSIONS.get(data.getUniqueId());
              if (s == null || !valid(s) || !s.active()) return false;
              return skill.getSkill().getId().equals("DECADE_HEISEI_SPIRIT")
                      && tick < s.cards.spiritSelectUntil
                  || skill.getSkill().getId().equals("DECADE_SLASH_BLAST")
                      && tick < s.cards.recastUntil
                  || skill.getSkill().getId().equals("ZOLDA_STRIKE_VENT")
                      && s.zolda.strikeUntil > tick
                  || skill.getSkill().getId().equals("AGITO_SHINING")
                      && s.agito.form(tick) == AgitoState.Form.SHINING
                      && s.ultimateUntil <= tick
                      && !s.shiningKickUsed;
            });
    vn.svframe.svframemmo.api.event.PlayerEnterCastingModeEvent.EVENT.register(
        event ->
            schedule(
                1,
                () -> {
                  var p = event.getPlayer();
                  var s = SESSIONS.get(p.getUuid());
                  if (!event.isCancelled()
                      && s != null
                      && valid(s)
                      && !s.active()
                      && SVFrameMMO.skillBar().isCasting(p.getUuid())) henshin(s);
                }));
    vn.svframe.svframemmo.api.event.PlayerExitCastingModeEvent.EVENT.register(
        event -> {
          if (!"SKILL_BAR".equals(SVFrameMMO.skillBar().mode(event.getPlayer().getUuid()))) return;
          schedule(
              1,
              () -> {
                var p = event.getPlayer();
                var s = SESSIONS.get(p.getUuid());
                if (!event.isCancelled()
                    && !SVFrameMMO.skillBar().isCasting(p.getUuid())
                    && s != null) detransform(s);
              });
        });
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          tick++;
          for (var p : server.getPlayerManager().getPlayerList()) {
            String type = type(p);
            var s = SESSIONS.get(p.getUuid());
            boolean eligible =
                p.isAlive()
                    && SKILLS.containsKey(type)
                    && !BeltEquipment.equipped(p, belt(type)).isEmpty();
            if (!eligible) {
              if (s != null) clear(s);
              continue;
            }
            if (s == null
                || s.player != p
                || s.world != p.getServerWorld()
                || !s.type.equals(type)) {
              if (s != null) clear(s);
              s = new Session(p, type);
              SESSIONS.put(p.getUuid(), s);
            }
            s.zolda.expire(tick);
            if (CardRiderSkills.supports(s)) CardRiderSkills.tick(s);
            String form = formKey(s);
            if (!s.applied.equals(form)) applyForm(s);
            publish(s);
            if (s.active()
                && tick % 10 == 0
                && s.type.equals("AGITO")
                && s.agito.form(tick) == AgitoState.Form.TRINITY)
              ring(s, s.player.getPos().add(0, .8, 0), .85, ParticleTypes.FLAME);
          }
          if (tick % 5 == 0) reconcileMarks();
          for (var entry : List.copyOf(SLOWS.entrySet())) {
            var slow = entry.getValue();
            if (tick >= slow.until
                || !slow.target.isAlive()
                || slow.target.isRemoved()
                || slow.actor.isRemoved()
                || !RpgEntityAdapters.allows(
                    slow.actor, slow.target, RpgEntityAdapters.Effect.MOVEMENT)) {
              attribute(slow.target, EntityAttributes.GENERIC_MOVEMENT_SPEED, SLOW, 0);
              SLOWS.remove(entry.getKey());
            }
          }
        });
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> {
          var s = SESSIONS.get(handler.player.getUuid());
          if (s != null) clear(s);
        });
    ServerLifecycleEvents.SERVER_STOPPING.register(
        server -> {
          for (var s : List.copyOf(SESSIONS.values())) clear(s);
          reconcileMarks();
          for (var slow : SLOWS.values())
            attribute(slow.target, EntityAttributes.GENERIC_MOVEMENT_SPEED, SLOW, 0);
          SLOWS.clear();
        });
    EntityTrackingEvents.START_TRACKING.register(
        (e, p) -> {
          var s = SESSIONS.get(e.getUuid());
          if (s != null && ServerPlayNetworking.canSend(p, Protocol.Rider.ID))
            ServerPlayNetworking.send(p, packet(s));
        });
    RpgEntityAdapters.register(
        "fantasyhub:riders",
        new RpgEntityAdapters.Adapter() {
          public boolean supports(LivingEntity target) {
            return false;
          }

          public boolean allows(
              Entity actor, LivingEntity target, RpgEntityAdapters.Effect effect) {
            return true;
          }

          public boolean allowsFrom(
              Entity actor, LivingEntity target, RpgEntityAdapters.Effect effect) {
            return effect != RpgEntityAdapters.Effect.MOVEMENT || !controlImmune(target);
          }
        });
    vn.svframe.svframelib.api.event.PlayerAttackEvent.EVENT.register(
        event -> {
          var s = SESSIONS.get(event.getPlayer().getUuid());
          if (s == null
              || !valid(s)
              || !s.active()
              || event.isCancelled()
              || !ReferenceClassSkillRuntime.canTarget(s.player, event.getEntity())) return;
          CardRiderSkills.attack(s, event.getEntity(), event.getDamage());
          if (s.type.equals("ZOLDA")
              && (event.getDamage().hasType(DamageType.PROJECTILE)
                  || event.getDamage().hasType(DamageType.SKILL)))
            event
                .getDamage()
                .multiplicativeModifier(
                    1
                        + n(s, "mark-damage-per-stack")
                            * s.zolda.stacks(event.getEntity().getUuid(), tick));
          if (s.type.equals("AGITO")) {
            if (s.agito.form(tick) == AgitoState.Form.SHINING)
              event.getDamage().multiplicativeModifier(n(s, "shining-damage-multiplier"));
            if (s.executing && s.action != null ? s.action.empowered : s.agito.force() == 6)
              event.getDamage().multiplicativeModifier(n(s, "force-damage-multiplier"));
          }
        });
    ServerLivingEntityEvents.AFTER_DAMAGE.register(
        (victim, source, base, amount, blocked) -> {
          if (amount <= 0 || !(source.getAttacker() instanceof ServerPlayerEntity p)) return;
          var s = SESSIONS.get(p.getUuid());
          if (s == null
              || s.executing
              || !valid(s)
              || !s.active()
              || !CardRiderSkills.legal(s, victim)) return;
          if (CardRiderSkills.supports(s)) {
            CardRiderSkills.normalHit(s, victim, amount, source);
          } else if (s.type.equals("ZOLDA")) {
            if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_PROJECTILE))
              mark(s, victim);
          } else if (source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK))
            gain(s, null);
        });
    UseItemCallback.EVENT.register(
        (p, world, hand) -> {
          var stack = p.getStackInHand(hand);
          var custom = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
          if (custom == null
              || !custom
                  .copyNbt()
                  .getString("SVFRAME_ITEM_MODEL")
                  .equals("fantasyhub:item/riders/magna_visor"))
            return TypedActionResult.pass(stack);
          if (world.isClient) return TypedActionResult.success(stack);
          if (p instanceof ServerPlayerEntity actor)
            try {
              var result =
                  SVFrameMMO.skillRuntime()
                      .cast(SVFrameMMO.playerData().get(actor), "ZOLDA_MAGNA_SHOT");
              return result.isSuccessful()
                  ? TypedActionResult.success(stack)
                  : TypedActionResult.fail(stack);
            } catch (IllegalArgumentException | IllegalStateException unavailable) {
              return TypedActionResult.fail(stack);
            }
          return TypedActionResult.fail(stack);
        });
  }

  static void reload() {
    try {
      var path =
          net.fabricmc.loader.api.FabricLoader.getInstance()
              .getConfigDir()
              .resolve("FantasyHub/riders.yml");
      if (!Files.exists(path)) {
        try (var in =
            RiderRuntime.class.getResourceAsStream("/default/riders/FantasyHub/riders.yml")) {
          if (in == null) throw new IllegalStateException("Missing Rider settings");
          Files.createDirectories(path.getParent());
          Files.copy(in, path);
        }
      }
      var configured = new LinkedHashMap<String, Object>(YamlLite.map(YamlLite.parse(path)));
      // Add only entirely absent new classes; preserve every existing administrator value.
      var additions = new LinkedHashMap<String, Object>();
      try (var defaults =
          RiderRuntime.class.getResourceAsStream("/default/riders/FantasyHub/riders.yml")) {
        var bundled =
            YamlLite.map(
                YamlLite.parse(
                    new String(
                        Objects.requireNonNull(defaults).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)));
        for (String id : SKILLS.keySet()) {
          String key = id.toLowerCase(Locale.ROOT);
          if (!configured.containsKey(key)) {
            configured.put(key, bundled.get(key));
            additions.put(key, bundled.get(key));
          }
        }
      }
      // Newly introduced options inherit bundled defaults in memory; existing server values win.
      try (var defaults =
          RiderRuntime.class.getResourceAsStream("/default/riders/FantasyHub/riders.yml")) {
        var bundled =
            YamlLite.map(
                YamlLite.parse(
                    new String(
                        Objects.requireNonNull(defaults).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)));
        for (String id : SKILLS.keySet()) {
          String key = id.toLowerCase(Locale.ROOT);
          var merged =
              RiderSettings.withDefaults(
                  YamlLite.map(bundled.get(key)), YamlLite.map(configured.get(key)));
          configured.put(key, merged);
        }
      }
      var parsed = RiderSettings.validate(configured);
      if (!additions.isEmpty()) {
        Path temporary = Files.createTempFile(path.getParent(), "rider-defaults-", ".tmp");
        Files.writeString(
            temporary,
            Files.readString(path)
                + "\n# Added native Rider classes; existing settings retained.\n"
                + new org.yaml.snakeyaml.Yaml().dump(additions));
        try {
          Files.move(
              temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
          Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
      }
      for (var type : SKILLS.entrySet()) {
        var costs =
            YamlLite.map(
                YamlLite.map(parsed.get(type.getKey().toLowerCase(Locale.ROOT))).get("skills"));
        if (!costs.keySet().containsAll(type.getValue()))
          throw new IllegalArgumentException("Incomplete " + type.getKey() + " skill costs");
      }
      settings = parsed;
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Cannot load Rider abilities", failure);
    }
    for (var entry : SKILLS.entrySet())
      for (String ability : entry.getValue()) {
        String id = entry.getKey() + "_" + ability;
        var section = YamlLite.map(settings.get(entry.getKey().toLowerCase(Locale.ROOT)));
        var params =
            new LinkedHashMap<String, Object>(
                YamlLite.map(YamlLite.map(section.get("skills")).get(ability)));
        params.put("power", Map.of("player", Map.of("base", 1.0, "per-level", .04)));
        var handler =
            new Handler(
                entry.getKey(),
                ability,
                new MapConfigObject(
                    id,
                    Map.of(
                        "name",
                        ability.replace('_', ' '),
                        "trigger",
                        "CAST",
                        "parameters",
                        params,
                        "icon",
                        Map.of(
                            "material",
                            "fantasyhub:" + entry.getKey().toLowerCase(Locale.ROOT) + "_belt"),
                        "lore",
                        List.of("<cooldown>s • <mana> mana • <stamina> stamina"))));
        var manager = SVFrameLib.inst().getSkills();
        if (manager.getHandler(id) == null) manager.registerSkillHandler(handler);
        else
          for (var param : params.entrySet())
            manager
                .getHandler(id)
                .addParameter(
                    param.getKey(),
                    vn.svframe.svframelib.skill.parameter.SkillParameter.fromConfig(
                        param.getValue(), param.getKey()));
      }
  }

  private static final class Handler extends SkillHandler<Prepared> {
    final String type, ability;

    Handler(String type, String ability, MapConfigObject config) {
      super(config);
      this.type = type;
      this.ability = ability;
    }

    public Prepared getResult(SkillMetadata meta) {
      var p = meta.getCaster().getPlayer();
      var s = SESSIONS.get(p.getUuid());
      boolean ok =
          s != null && valid(s) && s.type.equals(type) && (ability.equals("HENSHIN") || s.active());
      LivingEntity target = null;
      Vec3d point = p.getPos();
      if (ok && s.ultimateUntil > tick && !ability.equals("HENSHIN")) ok = false;
      if (ok && ability.equals("TRINITY"))
        ok =
            SVFrameMMO.playerData().get(p).getLevel() >= 20
                && s.agito.form(tick) != AgitoState.Form.SHINING;
      if (ok
          && (ability.equals("SHINING")
              || ability.equals("RIDER_KICK")
              || ability.equals("FINAL_VENT")))
        ok = SVFrameMMO.playerData().get(p).getLevel() >= 50;
      if (ok && ability.equals("RIDER_KICK")) {
        target = aim(p, 12);
        ok = target != null;
      }
      if (ok && ability.equals("GIGA_CANNONS")) {
        var aimed = aim(p, 20);
        var ground = p.raycast(20, 1, false);
        ok = aimed != null || ground.getType() != HitResult.Type.MISS;
        point = aimed != null ? aimed.getPos() : ground.getPos();
      }
      if (ok
          && ability.equals("SHINING")
          && s.agito.form(tick) == AgitoState.Form.SHINING
          && s.shiningKickUsed) ok = false;
      if (ok && CardRiderSkills.supports(s)) {
        target = aim(p, ability.equals("FINAL_VENT") ? 16 : 24);
        var ground = p.raycast(24, 1, false);
        point = target != null ? target.getPos() : ground.getPos();
        ok = CardRiderSkills.ready(s, ability, target, ground.getType() != HitResult.Type.MISS);
      }
      if (ok && ability.equals("MAGNA_SHOT")) ok = s.activeProjectiles < 8;
      return new Prepared(ok, target, point);
    }

    public void whenCast(Prepared prepared, SkillMetadata metadata) {
      var s = SESSIONS.get(metadata.getCaster().getPlayer().getUuid());
      if (s == null || !valid(s)) return;
      var previous = s.action;
      s.action = new CombatAction();
      s.action.power = Math.clamp(metadata.getParameter("power"), .1, 5);
      try {
        execute(s, ability, prepared);
        applyForm(s);
        publish(s);
        RiderPreferences.accepted(s, ability);
      } finally {
        s.action = previous;
      }
    }
  }

  private static void execute(Session s, String ability, Prepared result) {
    var p = s.player;
    if (!ability.equals("HENSHIN") && CardRiderSkills.supports(s)) {
      CardRiderSkills.execute(s, ability, result.target, result.point);
      return;
    }
    switch (ability) {
      case "HENSHIN" -> {
        if (s.active()) detransform(s);
        else henshin(s);
      }
      case "GROUND_SMASH" -> {
        s.agito.stance(AgitoState.Form.GROUND);
        s.groundUntil = tick + (int) n(s, "ground-reduction-ticks");
        area(s, p.getPos(), n(s, "ground-radius"), n(s, "ground-damage"), .18, false);
        ring(s, p.getPos(), 3, ParticleTypes.CRIT);
        for (var t : near(s, p.getPos(), 3)) slow(s, t, 40, .25);
        sound(s, SoundEvents.BLOCK_ANVIL_LAND, .6f);
      }
      case "FLAME_SABER" -> {
        s.agito.stance(AgitoState.Form.FLAME);
        dash(s, 4);
        for (var t : near(s, p.getPos().add(forward(p)), 2.2))
          if (hit(s, t, n(s, "flame-damage"), true)
              && RpgEntityAdapters.allows(p, t, RpgEntityAdapters.Effect.STATUS))
            t.setOnFireFor((float) n(s, "burn-ticks") / 20);
        ring(s, p.getPos().add(0, 1, 0), 1.5, ParticleTypes.FLAME);
        sound(s, SoundEvents.ITEM_FIRECHARGE_USE, 1);
      }
      case "STORM_HALBERD" -> {
        s.action.rapid = true;
        s.agito.stance(AgitoState.Form.STORM);
        for (int i = 0; i < (int) n(s, "storm-duration-ticks"); i += 5) {
          int delay = i;
          later(
              s,
              delay,
              () -> {
                area(s, p.getPos(), n(s, "storm-radius"), n(s, "storm-damage"), .12, false);
                ring(s, p.getPos().add(0, .3 + (delay % 15) / 10d, 0), 2.5, ParticleTypes.CLOUD);
              });
        }
        sound(s, SoundEvents.ENTITY_BREEZE_WIND_BURST, 1);
      }
      case "TRINITY" -> {
        s.agito.elevate(AgitoState.Form.TRINITY, tick, (int) n(s, "trinity-duration-ticks"));
        for (String id : List.of("GROUND_SMASH", "FLAME_SABER", "STORM_HALBERD"))
          MMOPlayerData.setup(p)
              .getCooldownMap()
              .resetCooldown("agito_" + id.toLowerCase(Locale.ROOT));
        sound(s, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1);
      }
      case "RIDER_KICK" -> kick(s, result.target, false);
      case "SHINING" -> {
        if (s.agito.form(tick) == AgitoState.Form.SHINING) {
          s.shiningKickUsed = true;
          s.ultimateUntil = tick + 8;
          Vec3d start = p.getPos();
          dash(s, 7);
          for (var t : near(s, start, 8))
            if (t.getBoundingBox()
                .expand(1)
                .raycast(start.add(0, 1, 0), p.getPos().add(0, 1, 0))
                .isPresent()) hit(s, t, n(s, "kick-damage"), false);
          ring(s, p.getPos(), 2, ParticleTypes.END_ROD);
        } else {
          s.shiningKickUsed = false;
          s.agito.elevate(AgitoState.Form.SHINING, tick, (int) n(s, "shining-duration-ticks"));
          p.removeStatusEffect(StatusEffects.SLOWNESS);
          sound(s, SoundEvents.ITEM_TRIDENT_THUNDER, .7f);
        }
      }
      case "MAGNA_SHOT" -> {
        rocket(s, p.getEyePos(), p.getRotationVec(1), n(s, "shot-damage"), 0, false);
        sound(s, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1);
      }
      case "STRIKE_VENT" -> {
        if (s.zolda.release(tick)) {
          area(s, p.getPos().add(forward(p)), 3, n(s, "strike-damage"), .4, false);
          ring(s, p.getPos().add(0, 1, 0), 2, ParticleTypes.EXPLOSION);
          sound(s, SoundEvents.ENTITY_GENERIC_EXPLODE, .8f);
        } else {
          s.zolda.shield(
              p.getMaxHealth(),
              n(s, "strike-shield-fraction"),
              tick,
              (int) n(s, "strike-duration-ticks"));
          sound(s, SoundEvents.ITEM_SHIELD_BLOCK, 1);
        }
      }
      case "SHOOT_VENT" -> {
        rocket(s, p.getEyePos(), p.getRotationVec(1), n(s, "rocket-damage"), 0, true);
        sound(s, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, .8f);
      }
      case "GIGA_CANNONS" -> {
        Vec3d center = result.point;
        for (int i = 0; i < 3; i++)
          later(
              s,
              i * 10,
              () -> {
                area(s, center, n(s, "cannon-radius"), n(s, "cannon-damage"), .1, false);
                s.world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    center.x,
                    center.y + 1,
                    center.z,
                    30,
                    1.5,
                    1,
                    1.5,
                    .03);
                for (var t : near(s, center, n(s, "cannon-radius")))
                  if (RpgEntityAdapters.allows(p, t, RpgEntityAdapters.Effect.STATUS))
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0));
                soundAt(s, center, SoundEvents.ENTITY_GENERIC_EXPLODE, .7f);
              });
      }
      case "GUARD_VENT" -> {
        s.zolda.guardUntil = tick + (int) n(s, "guard-duration-ticks");
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        sound(s, SoundEvents.ITEM_SHIELD_BLOCK, 1);
      }
      case "FINAL_VENT" -> {
        s.action.artillery = true;
        RiderSummons.remove(s.summon);
        s.summon = RiderSummons.magnugiga(p, p.getPos().subtract(forward(p).multiply(1.8)));
        later(
            s,
            65,
            () -> {
              RiderSummons.remove(s.summon);
              s.summon = null;
            });
        s.ultimateUntil = tick + 60;
        s.zolda.finalUntil = s.ultimateUntil;
        var origin = p.getPos();
        var facing = forward(p);
        int count = (int) n(s, "final-projectiles");
        for (int i = 0; i < count; i++) {
          int index = i;
          later(
              s,
              20 + i * (int) n(s, "final-duration-ticks") / count,
              () -> {
                double angle = -60 + 120d * (index % 8) / 7;
                var direction = facing.rotateY((float) Math.toRadians(angle));
                rocket(s, origin.add(0, 1.8, 0), direction, n(s, "final-damage"), 0, true);
                soundAt(s, origin, SoundEvents.ENTITY_GENERIC_EXPLODE, .35f);
              });
        }
        sound(s, SoundEvents.BLOCK_BEACON_ACTIVATE, 1);
      }
      default -> throw new IllegalArgumentException("Unknown Rider ability");
    }
  }

  private static void kick(Session s, LivingEntity target, boolean shining) {
    if (target == null) return;
    Vec3d point = target.getPos();
    s.ultimateUntil = tick + 30;
    ring(s, s.player.getPos(), 1.5, ParticleTypes.END_ROD);
    later(
        s,
        20,
        () -> {
          if (!ReferenceClassSkillRuntime.canTarget(s.player, target) || !s.player.canSee(target))
            return;
          dashToward(s, point, 8);
          velocity(s.player, new Vec3d(0, .6, 0));
          later(
              s,
              8,
              () -> {
                area(s, point, 3, n(s, "kick-damage"), .5, false);
                soundAt(s, point, SoundEvents.ENTITY_GENERIC_EXPLODE, 1);
              });
        });
  }

  private static void rocket(
      Session s, Vec3d start, Vec3d direction, double damage, int age, boolean explosive) {
    if (!valid(s) || !s.active() || age > 32) return;
    if (age == 0) {
      if (s.activeProjectiles >= 32) return;
      s.activeProjectiles++;
    }
    Vec3d end = start.add(direction.multiply(explosive ? .9 : 1.8));
    var wall =
        s.world.raycast(
            new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                s.player));
    if (wall.getType() != HitResult.Type.MISS) end = wall.getPos();
    LivingEntity chosen = null;
    Vec3d impact = end;
    double nearest = start.squaredDistanceTo(end) + 1e-8;
    for (var t :
        s.world.getEntitiesByClass(
            LivingEntity.class,
            new Box(start, end).expand(.4),
            t -> ReferenceClassSkillRuntime.canTarget(s.player, t))) {
      var contact = RiderCollision.contact(t.getBoundingBox().expand(.1), start, end);
      if (contact.isPresent() && contact.get().squaredDistanceTo(start) < nearest) {
        chosen = t;
        impact = contact.get();
        nearest = impact.squaredDistanceTo(start);
      }
    }
    s.world.spawnParticles(
        explosive ? ParticleTypes.SMOKE : ParticleTypes.ELECTRIC_SPARK,
        end.x,
        end.y,
        end.z,
        3,
        .03,
        .03,
        .03,
        .01);
    if (chosen != null || wall.getType() != HitResult.Type.MISS) {
      if (explosive) {
        area(s, impact, n(s, "rocket-radius"), damage, .15, false);
        for (var t : near(s, impact, n(s, "rocket-radius"))) slow(s, t, 40, .4);
        s.world.spawnParticles(
            ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
        soundAt(s, impact, SoundEvents.ENTITY_GENERIC_EXPLODE, .65f);
      } else if (chosen != null) hit(s, chosen, damage, false);
      s.activeProjectiles--;
      return;
    }
    if (age == 32) {
      s.activeProjectiles--;
      return;
    }
    Vec3d next = end;
    later(s, 1, () -> rocket(s, next, direction, damage, age + 1, explosive));
  }

  static boolean hit(Session s, LivingEntity target, double damage, boolean fire) {
    if (!valid(s) || !s.active() || !CardRiderSkills.legal(s, target)) return false;
    if (s.action != null && !s.action.awarded) s.action.empowered = s.agito.force() == 6;
    boolean previousExecuting = s.executing;
    s.executing = true;
    try {
      var packet =
          new DamageMetadata(
              damage
                  * (s.action == null ? 1 : s.action.power)
                  * (CardRiderSkills.supports(s)
                      ? n(s, "damage-multiplier")
                          * (target instanceof ServerPlayerEntity
                              ? n(s, "pvp-damage-multiplier")
                              : 1)
                      : 1),
              fire ? SVFrameLib.inst().getElements().getOrNull("FIRE") : null,
              List.of(DamageType.PHYSICAL, DamageType.SKILL));
      if (s.type.equals("AGITO") && s.agito.form(tick) == AgitoState.Form.TRINITY)
        packet.add(n(s, "trinity-bonus-damage"), DamageType.MAGIC, DamageType.SKILL);
      var attack =
          new AttackMetadata(
              packet,
              target,
              new PlayerMetadata(
                  MMOPlayerData.setup(s.player).getStatMap(),
                  vn.svframe.svframelib.api.player.EquipmentSlot.MAIN_HAND));
      net.minecraft.entity.damage.DamageSource source = null;
      if (s.action != null && (s.action.artillery || s.action.trueDamage)) {
        var key =
            net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,
                Identifier.of(
                    "fantasyhub", s.action.trueDamage ? "rider_true" : "rider_artillery"));
        source =
            new DamageSource(
                s.world
                    .getRegistryManager()
                    .get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE)
                    .entryOf(key),
                s.player);
      }
      var armor = target.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
      int armorBefore = target.getArmor();
      var pierceId = Identifier.of("fantasyhub", "card_pierce");
      if (armor != null && s.action != null && s.action.armorPierce > 0)
        armor.addTemporaryModifier(
            new EntityAttributeModifier(
                pierceId,
                -s.action.armorPierce,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
      boolean applied;
      int armorDuring = target.getArmor();
      try {
        applied =
            SVFrameLib.inst()
                .getDamage()
                .registerAttack(attack, true, s.action != null && s.action.rapid, source);
      } finally {
        if (armor != null) armor.removeModifier(pierceId);
      }
      if (applied) {
        RiderQa.hit(
            s.player,
            target,
            attack.getAppliedDamage(),
            fire,
            armorBefore,
            armorDuring,
            packet.isWeaponCriticalStrike() || packet.isSkillCriticalStrike());
        if (s.type.equals("AGITO")) gain(s, s.action);
        else if (s.type.equals("ZOLDA")) mark(s, target);
        else CardRiderSkills.skillHit(s, target, attack.getAppliedDamage());
      }
      return applied;
    } finally {
      s.executing = previousExecuting;
    }
  }

  private static void gain(Session s, CombatAction action) {
    if (action != null) {
      if (action.awarded) return;
      action.awarded = true;
    }
    if (s.agito.consume()) {
      for (String ability : List.of("rider_kick", "shining")) {
        var cooldown = MMOPlayerData.setup(s.player).getCooldownMap().getInfo("agito_" + ability);
        if (cooldown != null) cooldown.reduceRemainingCooldown(.2);
      }
    } else if (s.agito.gain()) {
      ring(s, s.player.getEyePos(), .6, ParticleTypes.END_ROD);
      sound(s, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1);
    }
  }

  private static void mark(Session s, LivingEntity t) {
    s.zolda.mark(t.getUuid(), tick, (int) n(s, "mark-duration-ticks"));
    SUNDERED.put(t.getUuid(), t);
    attribute(t, EntityAttributes.GENERIC_ARMOR, MARK, -n(s, "mark-armor-reduction"));
  }

  private static void reconcileMarks() {
    for (var entry : List.copyOf(SUNDERED.entrySet())) {
      boolean marked =
          entry.getValue().isAlive()
              && !entry.getValue().isRemoved()
              && SESSIONS.values().stream()
                  .anyMatch(
                      s ->
                          s.active()
                              && valid(s)
                              && s.type.equals("ZOLDA")
                              && s.zolda.stacks(entry.getKey(), tick) > 0);
      if (!marked) {
        attribute(entry.getValue(), EntityAttributes.GENERIC_ARMOR, MARK, 0);
        SUNDERED.remove(entry.getKey());
      }
    }
  }

  public static float incoming(LivingEntity target, DamageSource source, float amount) {
    var s = SESSIONS.get(target.getUuid());
    if (s == null
        || !valid(s)
        || !s.active()
        || amount <= 0
        || source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY))
      return amount;
    if (source.isOf(
        net.minecraft.registry.RegistryKey.of(
            net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,
            Identifier.of("fantasyhub", "rider_true")))) return amount;
    if (CardRiderSkills.supports(s)) return CardRiderSkills.incoming(s, source, amount);
    if (s.type.equals("AGITO"))
      return s.groundUntil > tick ? (float) (amount * (1 - n(s, "ground-reduction"))) : amount;
    if (source.isOf(
        net.minecraft.registry.RegistryKey.of(
            net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,
            Identifier.of("fantasyhub", "rider_artillery")))) {
      s.zolda.breakBarrier();
      return amount;
    }
    Vec3d position = source.getPosition();
    if (s.zolda.guardUntil > tick
        && position != null
        && forward(s.player).dotProduct(position.subtract(target.getPos()).normalize()) >= 0)
      amount *= 1 - n(s, "guard-reduction");
    return (float) s.zolda.absorb(amount, tick);
  }

  static void area(Session s, Vec3d center, double radius, double damage, double up, boolean fire) {
    for (var target : near(s, center, radius)) {
      if (!s.player.canSee(target) || !hit(s, target, damage, fire)) continue;
      if (fire && RpgEntityAdapters.allows(s.player, target, RpgEntityAdapters.Effect.STATUS))
        target.setOnFireFor(3);
      if (RpgEntityAdapters.allows(s.player, target, RpgEntityAdapters.Effect.MOVEMENT))
        velocity(target, target.getPos().subtract(center).normalize().multiply(.4).add(0, up, 0));
    }
  }

  static List<LivingEntity> near(Session s, Vec3d point, double radius) {
    return s
        .world
        .getEntitiesByClass(
            LivingEntity.class,
            new Box(point, point).expand(radius),
            t -> CardRiderSkills.legal(s, t) && t.squaredDistanceTo(point) <= radius * radius)
        .stream()
        .sorted(Comparator.comparingDouble(t -> t.squaredDistanceTo(point)))
        .limit(CardRiderSkills.supports(s) ? (int) n(s, "target-limit") : 32)
        .toList();
  }

  static LivingEntity aim(ServerPlayerEntity p, double range) {
    Vec3d start = p.getEyePos(), end = p.raycast(range, 1, false).getPos();
    return p
        .getServerWorld()
        .getEntitiesByClass(
            LivingEntity.class,
            new Box(start, end).expand(.4),
            t ->
                ReferenceClassSkillRuntime.canTarget(p, t)
                    && RiderCollision.contact(t.getBoundingBox().expand(.2), start, end)
                        .isPresent())
        .stream()
        .min(Comparator.comparingDouble(t -> t.squaredDistanceTo(p)))
        .orElse(null);
  }

  static void slow(Session s, LivingEntity target, int duration, double fraction) {
    if (!RpgEntityAdapters.allows(s.player, target, RpgEntityAdapters.Effect.MOVEMENT)) return;
    attribute(target, EntityAttributes.GENERIC_MOVEMENT_SPEED, SLOW, -fraction);
    SLOWS.put(target.getUuid(), new Slow(s.player, target, tick + duration));
  }

  static void dash(Session s, double distance) {
    dashToward(s, s.player.getPos().add(forward(s.player).multiply(distance)), distance);
  }

  static void dashToward(Session s, Vec3d point, double distance) {
    Vec3d end = safeDashEnd(s, point, distance);
    if (end.squaredDistanceTo(s.player.getPos()) < .0001) return;
    s.player.networkHandler.requestTeleport(
        end.x, end.y, end.z, s.player.getYaw(), s.player.getPitch());
    s.player.fallDistance = 0;
  }

  static Vec3d safeDashEnd(Session s, Vec3d point, double distance) {
    Vec3d start = s.player.getPos();
    if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z))
      return start;
    Vec3d delta = point.subtract(start);
    double length = Math.min(64, Math.min(Math.max(0, distance), delta.length()));
    delta = delta.normalize().multiply(length);
    final Vec3d movement = delta;
    double fraction =
        vn.svframe.fantasyhub.core.rider.SweptPath.clearFraction(
            length, t -> safeBody(s, s.player.getBoundingBox().offset(movement.multiply(t))));
    return start.add(delta.multiply(fraction));
  }

  private static boolean safeBody(Session s, Box box) {
    if (box.minY < s.world.getBottomY()
        || box.maxY > s.world.getTopY()
        || !s.world.getWorldBorder().contains(box)) return false;
    for (int x = MathHelper.floor(box.minX) >> 4; x <= MathHelper.floor(box.maxX) >> 4; x++)
      for (int z = MathHelper.floor(box.minZ) >> 4; z <= MathHelper.floor(box.maxZ) >> 4; z++)
        if (!s.world.isChunkLoaded(x, z)) return false;
    return s.world.isSpaceEmpty(s.player, box);
  }

  static boolean safeBehind(Session s, LivingEntity target, double range) {
    Vec3d behind =
        target
            .getPos()
            .subtract(target.getRotationVec(1).multiply(1, 0, 1).normalize().multiply(1.2));
    if (behind.distanceTo(s.player.getPos()) > range) return false;
    for (double y : new double[] {0, .5, 1, -.5, -1}) {
      Vec3d candidate = behind.add(0, y, 0);
      Box body = s.player.getBoundingBox().offset(candidate.subtract(s.player.getPos()));
      if (!safeBody(s, body) || s.world.isSpaceEmpty(s.player, body.offset(0, -.15, 0))) continue;
      Vec3d reachable = safeDashEnd(s, candidate, range);
      if (reachable.squaredDistanceTo(candidate) > .0001) continue;
      dashToward(s, candidate, range);
      return true;
    }
    return false;
  }

  static void velocity(LivingEntity target, Vec3d velocity) {
    target.setVelocity(velocity);
    target.velocityDirty = target.velocityModified = true;
    if (target instanceof ServerPlayerEntity p)
      p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
  }

  static void attribute(
      LivingEntity target, RegistryEntry<EntityAttribute> type, Identifier id, double value) {
    var instance = target.getAttributeInstance(type);
    if (instance == null) return;
    instance.removeModifier(id);
    if (value != 0)
      instance.addTemporaryModifier(
          new EntityAttributeModifier(
              id, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
  }

  private static void applyForm(Session s) {
    for (var stat : s.stats) stat.unregister(MMOPlayerData.setup(s.player));
    s.stats.clear();
    s.applied = formKey(s);
    boolean storm =
        s.active()
            && s.type.equals("AGITO")
            && (s.agito.form(tick) == AgitoState.Form.STORM
                || s.agito.form(tick) == AgitoState.Form.TRINITY);
    attribute(
        s.player,
        EntityAttributes.GENERIC_MOVEMENT_SPEED,
        FORM,
        storm ? n(s, "storm-speed-multiplier") - 1 : 0);
    attribute(
        s.player,
        EntityAttributes.GENERIC_ATTACK_SPEED,
        FORM,
        storm ? n(s, "storm-attack-speed-multiplier") - 1 : 0);
    if (CardRiderSkills.supports(s)) CardRiderSkills.applyForm(s);
    if (s.active()
        && s.type.equals("AGITO")
        && (s.agito.form(tick) == AgitoState.Form.FLAME
            || s.agito.form(tick) == AgitoState.Form.TRINITY)
        && flameReady(s)) {
      var modifier =
          new StatModifier(
              "agito_flame",
              "CRITICAL_STRIKE_CHANCE",
              n(s, "flame-critical-chance"),
              ModifierType.FLAT,
              vn.svframe.svframelib.api.player.EquipmentSlot.OTHER,
              ModifierSource.OTHER);
      modifier.register(MMOPlayerData.setup(s.player));
      s.stats.add(modifier);
    }
  }

  private static boolean flameReady(Session s) {
    return !MMOPlayerData.setup(s.player).getCooldownMap().isOnCooldown("agito_flame_saber");
  }

  private static String formKey(Session s) {
    return s.form() + ":" + flameReady(s) + ":" + s.cards.elevated + ":" + s.cards.spirit(tick);
  }

  private static void henshin(Session s) {
    if (s.type.equals("AGITO")) {
      s.agito.clear();
      s.agito.henshin();
      s.agito.stance(AgitoState.Form.GROUND);
    } else if (s.type.equals("ZOLDA")) s.zolda.transformed = true;
    else s.cards.henshin();
    FilamentCosmetics.hide(s.player);
    if (CardRiderSkills.supports(s))
      CardRiderSkills.cue(s, "POSE_SHIFT", s.player.getPos(), s.player.getPos(), 25);
    CardRiderSkills.cue(s, "HENSHIN", s.player.getPos(), s.player.getPos(), 35);
    applyForm(s);
    ring(s, s.player.getPos().add(0, 1, 0), 1, ParticleTypes.ELECTRIC_SPARK);
    sound(s, SoundEvents.BLOCK_BEACON_ACTIVATE, 1);
    publish(s);
  }

  static void detransform(Session s) {
    RiderSummons.remove(s.summon);
    s.summon = null;
    s.generation++;
    s.agito.clear();
    s.zolda.clear();
    CardRiderSkills.clear(s);
    s.cards.clear();
    s.groundUntil = s.ultimateUntil = 0;
    s.activeProjectiles = 0;
    s.player.fallDistance = 0;
    applyForm(s);
    FilamentCosmetics.restore(s.player);
    publish(s);
    reconcileMarks();
  }

  private static void clear(Session s) {
    SESSIONS.remove(s.player.getUuid());
    detransform(s);
  }

  private static Item belt(String type) {
    return switch (type) {
      case "AGITO" -> Protocol.agitoBelt;
      case "ZOLDA" -> Protocol.zoldaBelt;
      case "RYUKI" -> Protocol.ryukiBelt;
      case "KNIGHT" -> Protocol.knightBelt;
      case "DECADE" -> Protocol.decadeBelt;
      default -> throw new IllegalArgumentException("Unknown Rider " + type);
    };
  }

  private static String type(ServerPlayerEntity p) {
    return SVFrameMMO.playerData().get(p).getClassId();
  }

  static Map<String, Object> uiState(ServerPlayerEntity p) {
    var s = SESSIONS.get(p.getUuid());
    if (s == null || !s.active() || !CardRiderSkills.supports(s)) return Map.of();
    return Map.of(
        "flame",
        s.cards.flame(),
        "threshold",
        s.type.equals("RYUKI") ? (int) n(s, "flame-stack-threshold") : 0,
        "cardsReady",
        s.cards.nextCards,
        "spirit",
        s.cards.spirit(tick));
  }

  static boolean valid(Session s) {
    return SESSIONS.get(s.player.getUuid()) == s
        && s.player.isAlive()
        && !s.player.isRemoved()
        && !s.player.isDisconnected()
        && s.player.getServerWorld() == s.world
        && type(s.player).equals(s.type)
        && !BeltEquipment.equipped(s.player, belt(s.type)).isEmpty();
  }

  static boolean flag(Session s, String key) {
    return Boolean.TRUE.equals(
        YamlLite.map(settings.get(s.type.toLowerCase(Locale.ROOT))).get(key));
  }

  static double n(Session s, String key) {
    Object value = YamlLite.map(settings.get(s.type.toLowerCase(Locale.ROOT))).get(key);
    if (!(value instanceof Number number))
      throw new IllegalArgumentException("Missing Rider setting " + key);
    return number.doubleValue();
  }

  static Protocol.Rider packet(Session s) {
    return new Protocol.Rider(
        s.player.getId(),
        s.form(),
        s.type.equals("AGITO") ? s.agito.force() : s.cards.elevated ? 1 : 0,
        s.type.equals("AGITO") ? ((s.agito.remaining(tick) + 4) / 5) * 5 : 0);
  }

  static void publish(Session s) {
    var packet = packet(s);
    if (packet.equals(s.sent)) return;
    s.sent = packet;
    for (var p : PlayerLookup.tracking(s.player))
      if (ServerPlayNetworking.canSend(p, Protocol.Rider.ID)) ServerPlayNetworking.send(p, packet);
    if (ServerPlayNetworking.canSend(s.player, Protocol.Rider.ID))
      ServerPlayNetworking.send(s.player, packet);
  }

  static void later(Session s, int delay, Runnable task) {
    int generation = s.generation;
    CombatAction action = s.action;
    schedule(
        delay,
        () -> {
          if (valid(s) && s.active() && s.generation == generation) {
            var previous = s.action;
            s.action = action;
            try {
              task.run();
            } finally {
              s.action = previous;
            }
          }
        });
  }

  private static void schedule(int delay, Runnable task) {
    vn.svframe.svframelib.fabric.SVFrameLibFabricMod.schedule(Math.max(1, delay), task);
  }

  static Vec3d forward(ServerPlayerEntity p) {
    var direction = p.getRotationVec(1);
    return new Vec3d(direction.x, 0, direction.z).normalize();
  }

  static void ring(
      Session s, Vec3d point, double radius, net.minecraft.particle.SimpleParticleType particle) {
    for (int i = 0; i < 28; i++) {
      double angle = i * Math.PI / 14;
      s.world.spawnParticles(
          particle,
          point.x + Math.cos(angle) * radius,
          point.y,
          point.z + Math.sin(angle) * radius,
          1,
          0,
          0,
          0,
          0);
    }
  }

  static void sound(Session s, RegistryEntry<SoundEvent> sound, float volume) {
    sound(s, sound.value(), volume);
  }

  static void soundAt(Session s, Vec3d point, RegistryEntry<SoundEvent> sound, float volume) {
    soundAt(s, point, sound.value(), volume);
  }

  static void sound(Session s, SoundEvent sound, float volume) {
    soundAt(s, s.player.getPos(), sound, volume);
  }

  static void soundAt(Session s, Vec3d point, SoundEvent sound, float volume) {
    s.world.playSound(null, point.x, point.y, point.z, sound, SoundCategory.PLAYERS, volume, 1);
  }

  static Map<String, Object> inspect(ServerPlayerEntity p) {
    var s = SESSIONS.get(p.getUuid());
    if (s == null) return Map.of("active", false);
    var out = new LinkedHashMap<String, Object>();
    out.put("active", s.active());
    out.put("class", s.type);
    out.put("form", s.form());
    out.put("force", s.agito.force());
    out.put("formRemaining", s.agito.remaining(tick));
    out.put("shield", CardRiderSkills.supports(s) ? s.cards.barrier : s.zolda.shield(tick));
    out.put("spiritSelected", s.cards.selectedSpirit);
    out.put("projection", s.cards.ultimateProjection);
    out.put("recastRemaining", Math.max(0, s.cards.recastUntil - tick));
    out.put("spiritSelectionRemaining", Math.max(0, s.cards.spiritSelectUntil - tick));
    out.put(
        "guard",
        CardRiderSkills.supports(s) ? s.cards.guardUntil > tick : s.zolda.guardUntil > tick);
    out.put("projectiles", s.activeProjectiles);
    out.put("elevated", s.cards.elevated);
    out.put("dragonFlame", s.cards.flame());
    out.put("nextCards", s.cards.nextCards);
    out.put("spirit", s.cards.spirit(tick));
    out.put("tick", tick);
    out.put("summon", s.summon == null ? "" : s.summon.getUuidAsString());
    return out;
  }
}
