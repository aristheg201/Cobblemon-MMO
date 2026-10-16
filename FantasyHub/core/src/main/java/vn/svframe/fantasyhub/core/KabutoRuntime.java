package vn.svframe.fantasyhub.core;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.component.*;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import vn.svframe.fantasyhub.protocol.*;
import vn.svframe.svframeitems.runtime.*;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.damage.*;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.fabric.ReferenceClassSkillRuntime;
import vn.svframe.svframelib.player.PlayerMetadata;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframelib.skill.*;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.util.configobject.MapConfigObject;
import vn.svframe.svframemmo.SVFrameMMO;

/** Native, server-authoritative Kabuto state machine and collision-aware ability execution. */
public final class KabutoRuntime {
  private static final Identifier FORM = Identifier.of("fantasyhub", "kabuto_form");
  private static final Map<UUID, Session> PLAYERS = new HashMap<>();
  private static final Map<UUID, Frozen> FROZEN = new HashMap<>();
  static final List<String> SKILLS =
      List.of(
          "HENSHIN",
          "CAST_OFF",
          "CLOCK_UP",
          "RIDER_KICK",
          "KUNAI_MODE",
          "KUNAI_STRIKE",
          "HYPER_FORM",
          "ZECTER_BOND");
  private static Map<String, Object> config = Map.of();
  private static long tick;

  private static final class Session {
    final ServerPlayerEntity player;
    final ServerWorld world;
    final KabutoState state = new KabutoState();
    final List<StatModifier> modifiers = new ArrayList<>();
    final Map<UUID, Bleed> bleeds = new LinkedHashMap<>();
    Protocol.Kabuto sent;
    boolean executing;
    long lastBlockTick = -1;
    long leapUntil, leapStarted;
    Vec3d landing;
    double leapDamage;

    Session(ServerPlayerEntity player) {
      this.player = player;
      this.world = player.getServerWorld();
    }
  }

  private record Bleed(LivingEntity target, long until, long next) {}

  private record Frozen(ServerPlayerEntity actor, MobEntity mob, Vec3d position, long until) {}

  private record Prepared(boolean isSuccessful, LivingEntity target, Vec3d point)
      implements SkillResult {}

  private KabutoRuntime() {}

  public static boolean clock(LivingEntity entity) {
    var session = PLAYERS.get(entity.getUuid());
    return session != null && session.state.clock(tick);
  }

  static Map<String, Object> inspect(ServerPlayerEntity player) {
    var s = PLAYERS.get(player.getUuid());
    if (s == null) return Map.of("active", false);
    return Map.of(
        "active",
        s.state.transformed,
        "form",
        s.state.form.name(),
        "energy",
        s.state.energy,
        "gauge",
        s.state.gauge,
        "clock",
        s.state.clock(tick),
        "hyper",
        s.state.hyper,
        "combo",
        s.state.combo,
        "gun",
        s.state.gun);
  }

  static void install() {
    reload();
    net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) ->
            dispatcher.register(
                net.minecraft.server.command.CommandManager.literal("kabuto")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(
                        net.minecraft.server.command.CommandManager.literal("reload")
                            .executes(
                                context -> {
                                  try {
                                    reload();
                                    context
                                        .getSource()
                                        .sendFeedback(
                                            () -> Text.translatable("fantasyhub.kabuto.reloaded"),
                                            false);
                                    return 1;
                                  } catch (IllegalArgumentException
                                      | IllegalStateException invalid) {
                                    throw new com.mojang.brigadier.exceptions
                                            .SimpleCommandExceptionType(
                                            Text.translatable(
                                                "fantasyhub.kabuto.invalid_config",
                                                invalid.getMessage()))
                                        .create();
                                  }
                                }))));
    vn.svframe.svframeitems.SVFrameItems.generator()
        .registerMechanic(
            (stack, definition, instance, phase) -> {
              if (definition.id().startsWith("added_") || definition.id().startsWith("kabuto_"))
                stack.set(
                    DataComponentTypes.CUSTOM_NAME,
                    Text.translatable("item.fantasyhub." + definition.id()));
            });
    BeltEquipment.classAllowed =
        entity -> !(entity instanceof ServerPlayerEntity player) || isClass(player);
    EquipmentProviderRegistry.register(
        player -> {
          ItemStack stack = BeltEquipment.equipped(player);
          return stack.isEmpty()
              ? List.of()
              : List.of(
                  new EquipmentProvider.EquippedItem(
                      "fantasyhub:kabuto_belt",
                      stack,
                      vn.svframe.svframelib.fabric.runtime.NativeStatEngine.EquipmentSlot
                          .ACCESSORY));
        });
    // Formal Cobblemon battles and ownership remain governed by the shared
    // adapter; this adapter only adds the Kabuto player's movement immunity.
    RpgEntityAdapters.register(
        "fantasyhub:kabuto",
        new RpgEntityAdapters.Adapter() {
          public boolean supports(LivingEntity target) {
            return target instanceof ServerPlayerEntity;
          }

          public boolean allows(
              Entity actor, LivingEntity target, RpgEntityAdapters.Effect effect) {
            return effect != RpgEntityAdapters.Effect.MOVEMENT || !clock(target);
          }
        });
    vn.svframe.svframemmo.api.event.PlayerEnterCastingModeEvent.EVENT.register(
        event ->
            later(
                1,
                () -> {
                  var p = event.getPlayer();
                  if (event.isCancelled()
                      || !p.isAlive()
                      || !isClass(p)
                      || !SVFrameMMO.skillBar().isCasting(p.getUuid())
                      || BeltEquipment.equipped(p).isEmpty()) return;
                  var session = PLAYERS.get(p.getUuid());
                  if (session == null) {
                    session = new Session(p);
                    PLAYERS.put(p.getUuid(), session);
                  }
                  // Reopening F after Cast Off must preserve Rider for the next bound skill.
                  if (!session.state.transformed) {
                    henshin(session);
                    publish(session);
                    cue(session, "HENSHIN", 24);
                  }
                }));
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          tick++;
          for (var player : server.getPlayerManager().getPlayerList()) {
            boolean eligible =
                player.isAlive() && isClass(player) && !BeltEquipment.equipped(player).isEmpty();
            Session s = PLAYERS.get(player.getUuid());
            if (!eligible) {
              if (s != null) clear(s);
              continue;
            }
            if (s == null || s.player != player || s.world != player.getServerWorld()) {
              if (s != null) clear(s);
              s = new Session(player);
              PLAYERS.put(player.getUuid(), s);
              applyForm(s);
            }
            update(s);
          }
          Iterator<Frozen> frozen = FROZEN.values().iterator();
          while (frozen.hasNext()) {
            Frozen lease = frozen.next();
            if (!validLease(lease)) {
              frozen.remove();
              continue;
            }
            lease.mob.setPosition(lease.position);
            lease.mob.setVelocity(Vec3d.ZERO);
          }
        });
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> {
          var s = PLAYERS.get(handler.player.getUuid());
          if (s != null) clear(s);
        });
    EntityTrackingEvents.START_TRACKING.register(
        (entity, player) -> {
          var s = PLAYERS.get(entity.getUuid());
          if (s != null && ServerPlayNetworking.canSend(player, Protocol.Kabuto.ID))
            ServerPlayNetworking.send(player, packet(s));
        });
    ServerLifecycleEvents.SERVER_STOPPING.register(
        server -> {
          for (Session s : List.copyOf(PLAYERS.values())) clear(s);
          FROZEN.clear();
        });
    ServerLivingEntityEvents.AFTER_DAMAGE.register(
        (victim, source, base, amount, blocked) -> {
          if (source.getAttacker() instanceof ServerPlayerEntity attacker
              && amount > 0
              && ReferenceClassSkillRuntime.canTarget(attacker, victim)) {
            var s = PLAYERS.get(attacker.getUuid());
            if (s != null && !s.executing) {
              s.state.gain(integer("energy-per-hit"), integer("gauge-per-hit"));
              s.state.combo = Math.min(10, s.state.combo + 1);
              if (!s.state.gun
                  && source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK)
                  && attacker.getMainHandStack().isOf(Protocol.kunaiGun)) {
                bleed(s, victim);
                fx(s, "SLASH", attacker.getEyePos(), surface(attacker, victim), 10, 1);
              }
            }
          }
          if (blocked && victim instanceof ServerPlayerEntity player) {
            var s = PLAYERS.get(player.getUuid());
            if (s != null) blocked(s);
          }
        });
    vn.svframe.svframelib.api.event.DamageMitigationEvent.subscribe(
        event -> {
          var type = event.getType().asLegacy();
          if (type == vn.svframe.svframelib.damage.mitigation.LegacyMitigationType.PARRY
              || type == vn.svframe.svframelib.damage.mitigation.LegacyMitigationType.BLOCK)
            later(
                1,
                () -> {
                  if (!event.isCancelled()) {
                    var s = PLAYERS.get(event.getPlayer().getUuid());
                    if (s != null && valid(s)) blocked(s);
                  }
                });
        });
    UseItemCallback.EVENT.register(
        (player, world, hand) -> {
          var stack = player.getStackInHand(hand);
          if (!stack.isOf(Protocol.kunaiGun)) return TypedActionResult.pass(stack);
          if (world.isClient) return TypedActionResult.pass(stack);
          if (player instanceof ServerPlayerEntity p) {
            var session = PLAYERS.get(p.getUuid());
            if (session != null
                && session.state.form == KabutoState.Form.MASKED
                && !p.isSneaking()
                && p.getOffHandStack().isOf(net.minecraft.item.Items.SHIELD))
              return TypedActionResult.pass(stack);
          }
          if (player instanceof ServerPlayerEntity serverPlayer)
            try {
              SVFrameMMO.skillRuntime()
                  .cast(
                      SVFrameMMO.playerData().get(serverPlayer),
                      serverPlayer.isSneaking() ? "KABUTO_KUNAI_MODE" : "KABUTO_KUNAI_STRIKE");
            } catch (IllegalArgumentException | IllegalStateException rejected) {
              return TypedActionResult.fail(stack);
            }
          return TypedActionResult.success(stack);
        });
  }

  static void reload() {
    try {
      Path file =
          net.fabricmc.loader.api.FabricLoader.getInstance()
              .getConfigDir()
              .resolve("FantasyHub/kabuto.yml");
      var parsed = KabutoSettings.validate(YamlLite.map(YamlLite.parse(file)));
      KabutoEffects.reload();
      config = parsed;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot load Kabuto balancing", e);
    }
    for (var session : PLAYERS.values()) applyForm(session);
    for (String name : SKILLS) {
      String id = "KABUTO_" + name;
      Map<String, Object> values = new LinkedHashMap<>();
      if (config.get("skills") instanceof Map<?, ?> skills
          && skills.get(name) instanceof Map<?, ?> costs)
        costs.forEach((k, v) -> values.put(String.valueOf(k), v));
      if (name.equals("ZECTER_BOND")) {
        values.put("timer", 20);
      }
      var handler =
          new Handler(
              name,
              new MapConfigObject(
                  id,
                  Map.of(
                      "name",
                      title(name),
                      "trigger",
                      name.equals("ZECTER_BOND") ? "TIMER" : "CAST",
                      "parameters",
                      values,
                      "icon",
                      Map.of(
                          "material",
                          switch (name) {
                            case "HENSHIN", "HYPER_FORM", "ZECTER_BOND" -> "fantasyhub:zecter_belt";
                            case "CAST_OFF" -> "fantasyhub:kabuto_hat";
                            case "KUNAI_MODE", "KUNAI_STRIKE" -> "fantasyhub:kunai_gun";
                            case "RIDER_KICK" -> "IRON_BOOTS";
                            default -> "CLOCK";
                          }),
                      "lore",
                      List.of("<cooldown>s • <mana> mana • <stamina> stamina"))));
      var manager = SVFrameLib.inst().getSkills();
      if (manager.getHandler(id) == null) manager.registerSkillHandler(handler);
      else
        for (String key : values.keySet())
          manager
              .getHandler(id)
              .addParameter(
                  key,
                  vn.svframe.svframelib.skill.parameter.SkillParameter.fromConfig(
                      values.get(key), key));
    }
  }

  private static String title(String name) {
    return switch (name) {
      case "HENSHIN" -> "Henshin";
      case "CAST_OFF" -> "Cast Off / Rider Jump";
      case "CLOCK_UP" -> "Clock Up";
      case "RIDER_KICK" -> "Rider Kick";
      case "HYPER_FORM" -> "Hyper Form";
      case "KUNAI_MODE" -> "Kunai Gun / Kunai Mode";
      case "ZECTER_BOND" -> "Zecter Bond";
      default -> "Kunai Strike";
    };
  }

  private static final class Handler extends SkillHandler<Prepared> {
    final String ability;

    Handler(String name, MapConfigObject config) {
      super(config);
      ability = name;
    }

    @Override
    public Prepared getResult(SkillMetadata metadata) {
      ServerPlayerEntity p = metadata.getCaster().getPlayer();
      Session s = PLAYERS.get(p.getUuid());
      boolean valid =
          s != null && p.isAlive() && isClass(p) && !BeltEquipment.equipped(p).isEmpty();
      if (valid)
        valid =
            switch (ability) {
              case "CAST_OFF" ->
                  s.state.transformed
                      && s.state.form == KabutoState.Form.MASKED
                      && s.leapUntil <= tick;
              case "CLOCK_UP" ->
                  s.state.transformed
                      && s.state.form == KabutoState.Form.RIDER
                      && !s.state.clock(tick)
                      && s.state.gauge >= integer("clock-gauge-cost");
              case "RIDER_KICK" -> s.state.transformed && s.state.form == KabutoState.Form.RIDER;
              case "HYPER_FORM" ->
                  SVFrameMMO.playerData().get(p).getLevel()
                          >= SVFrameMMO.playerData().get(p).getProfess().getMaxLevel()
                      && s.state.transformed
                      && s.state.form == KabutoState.Form.RIDER
                      && !s.state.hyper;
              case "KUNAI_STRIKE", "KUNAI_MODE" -> p.getMainHandStack().isOf(Protocol.kunaiGun);
              default -> true;
            };
      LivingEntity target = null;
      Vec3d point = null;
      if (valid && (ability.equals("RIDER_KICK") || ability.equals("KUNAI_STRIKE"))) {
        target = aim(p, ability.equals("RIDER_KICK") ? 12 : s.state.gun ? 32 : 3.4);
        valid = target != null;
      }
      if (valid && ability.equals("CAST_OFF")) {
        var hit = p.raycast(18, 1, false);
        point = hit.getPos();
        if (hit.getType() == HitResult.Type.MISS) point = p.getPos().add(forward(p).multiply(6));
      }
      if (!valid && !ability.equals("ZECTER_BOND"))
        p.sendMessage(Text.translatable("fantasyhub.kabuto.requirements"), true);
      return new Prepared(valid, target, point);
    }

    @Override
    public void whenCast(Prepared result, SkillMetadata metadata) {
      Session s = PLAYERS.get(metadata.getCaster().getPlayer().getUuid());
      if (s == null) return;
      if (ability.equals("ZECTER_BOND")) {
        publish(s);
        return;
      }
      boolean empowered =
          !Set.of("HENSHIN", "KUNAI_MODE").contains(ability) && s.state.consumeEmpowerment();
      if (empowered) {
        var cooldown = metadata.getCaster().getData().getCooldownMap().getInfo(metadata.getCast());
        if (cooldown != null)
          cooldown.reduceRemainingCooldown(number("empowered-cooldown-reduction"));
        fx(s, "EMPOWER", s.player.getPos(), s.player.getPos(), 14, 1.1);
      }
      double scale =
          (empowered ? number("empowered-damage-multiplier") : 1)
              * (1
                  + .025
                      * Math.max(0, SVFrameMMO.playerData().get(s.player).getSkillLevel(this) - 1));
      s.executing = true;
      try {
        switch (ability) {
          case "HENSHIN" -> henshin(s);
          case "CAST_OFF" -> {
            s.state.form = KabutoState.Form.RIDER;
            applyForm(s);
            var p = s.player;
            var w = p.getServerWorld();
            fx(s, "CAST_OFF", p.getPos(), p.getPos(), 14, 4);
            aoe(s, p.getPos(), 4, number("cast-off-damage") * scale, .45);
            s.landing = result.point;
            s.leapStarted = tick;
            s.leapUntil = tick + 60;
            fx(s, "LEAP", p.getPos(), result.point, 60, 1.2);
            s.leapDamage = number("slam-damage") * scale;
            velocity(p, forward(p).multiply(.35).add(0, 1.1, 0));
            sound(s, "cast_off", .8f);
          }
          case "CLOCK_UP" -> {
            s.state.startClock(tick, integer("clock-gauge-cost"), integer("clock-duration-ticks"));
            applySpeed(s);
            fx(
                s,
                "CLOCK",
                s.player.getPos(),
                s.player.getPos(),
                integer("clock-duration-ticks"),
                number("clock-radius"));
            sound(s, "clock_up", 1);
            if (s.state.hyper) hyperDash(s);
          }
          case "RIDER_KICK" -> {
            kick(s, result.target, scale, metadata);
          }
          case "KUNAI_MODE" -> {
            s.state.gun = !s.state.gun;
            NbtComponent.set(
                DataComponentTypes.CUSTOM_DATA,
                s.player.getMainHandStack(),
                nbt ->
                    nbt.putString(
                        "SVFRAME_ITEM_MODEL",
                        "fantasyhub:item/additions/" + (s.state.gun ? "kunai_gun" : "kunai")));
            fx(s, "MODE", s.player.getPos(), s.player.getPos(), 8, .6);
            sound(s, "mode", 1.3f);
          }
          case "KUNAI_STRIKE" -> {
            s.player.swingHand(Hand.MAIN_HAND);
            if (s.state.gun) shoot(s, result.target, number("gun-damage") * scale);
            else {
              boolean damaged = hit(s, result.target, number("kunai-damage") * scale, false);
              bleed(s, result.target);
              if (damaged)
                fx(s, "SLASH", s.player.getEyePos(), surface(s.player, result.target), 10, 1);
            }
            s.state.gain(integer("energy-per-hit"), integer("gauge-per-hit"));
          }
          case "HYPER_FORM" -> {
            s.state.hyper = true;
            s.state.gauge = 100;
            fx(s, "HYPER", s.player.getPos(), s.player.getPos(), 24, 1.2);
            sound(s, "hyper", .8f);
          }
        }
      } finally {
        s.executing = false;
        publish(s);
        cue(
            s,
            ability.equals("KUNAI_STRIKE") && s.state.gun ? "GUN_FIRE" : ability,
            ability.equals("RIDER_KICK") ? 14 : 24);
      }
    }
  }

  private static void update(Session s) {
    var p = s.player;
    var state = s.state;
    var dots = s.bleeds.entrySet().iterator();
    while (dots.hasNext()) {
      var entry = dots.next();
      var dot = entry.getValue();
      if (dot.until < tick || !dot.target.isAlive() || dot.target.getWorld() != s.world) {
        dots.remove();
        continue;
      }
      if (dot.next <= tick) {
        if (hit(s, dot.target, number("bleed-damage"), false))
          fx(s, "BLEED", surface(p, dot.target), dot.target.getPos(), 5, .2);
        entry.setValue(new Bleed(dot.target, dot.until, tick + 20));
      }
    }
    if (state.clockUntil > 0 && !state.clock(tick)) {
      state.clockUntil = 0;
      applySpeed(s);
      fx(s, "CLOCK_END", p.getPos(), p.getPos(), 10, 1.3);
      sound(s, "clock_over", .7f);
    }
    if (state.clock(tick)) {
      p.removeStatusEffect(StatusEffects.SLOWNESS);
      if (tick % 5 == 0)
        for (LivingEntity target : near(p, p.getPos(), number("clock-radius"))) {
          if (!RpgEntityAdapters.allows(p, target, RpgEntityAdapters.Effect.MOVEMENT)) continue;
          if (target instanceof MobEntity mob) {
            Frozen old = FROZEN.get(mob.getUuid());
            FROZEN.put(
                mob.getUuid(),
                new Frozen(
                    old != null && old.until > Math.min(s.state.clockUntil - 1, tick + 6)
                        ? old.actor
                        : p,
                    mob,
                    old == null ? mob.getPos() : old.position,
                    Math.max(
                        old == null ? 0 : old.until, Math.min(s.state.clockUntil - 1, tick + 6))));
          } else if (target instanceof ServerPlayerEntity other)
            other.addStatusEffect(
                new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 2, false, false, true));
        }
    }
    if (s.leapUntil > tick) {
      if (tick > s.leapStarted + 15) {
        Vec3d towards = s.landing.subtract(p.getPos());
        velocity(
            p,
            new Vec3d(towards.x, 0, towards.z)
                .normalize()
                .multiply(Math.min(.7, towards.horizontalLength() / 5))
                .add(0, -1.15, 0));
      }
      if (tick > s.leapStarted + 6 && p.isOnGround()) {
        s.leapUntil = 0;
        s.executing = true;
        try {
          aoe(s, p.getPos(), 4, s.leapDamage, .65);
        } finally {
          s.executing = false;
        }
        fx(s, "LAND", p.getPos(), p.getPos(), 16, 4);
        p.fallDistance = 0;
      } else p.fallDistance = 0;
    } else if (s.leapUntil > 0) {
      s.leapUntil = 0;
      p.fallDistance = 0;
    }
    if (tick % 10 == 0) {
      var held = p.getMainHandStack();
      if (held.isOf(Protocol.kunaiGun)) {
        String expected = "fantasyhub:item/additions/" + (state.gun ? "kunai_gun" : "kunai");
        var data = held.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || !data.copyNbt().getString("SVFRAME_ITEM_MODEL").equals(expected))
          NbtComponent.set(
              DataComponentTypes.CUSTOM_DATA,
              held,
              nbt -> nbt.putString("SVFRAME_ITEM_MODEL", expected));
      }
      publish(s);
    }
  }

  private static void kick(Session s, LivingEntity target, double scale, SkillMetadata metadata) {
    var p = s.player;
    Vec3d start = p.getPos(), end = target.getPos();
    Vec3d delta = end.subtract(start);
    var ray =
        p.getServerWorld()
            .raycast(
                new net.minecraft.world.RaycastContext(
                    p.getEyePos(),
                    target.getEyePos(),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    p));
    if (ray.getType() != HitResult.Type.MISS) return;
    Vec3d destination = end.subtract(delta.normalize().multiply(1.25));
    if (p.getServerWorld().isSpaceEmpty(p, p.getBoundingBox().offset(destination.subtract(start))))
      p.networkHandler.requestTeleport(
          destination.x, destination.y, destination.z, p.getYaw(), p.getPitch());
    fx(s, "KICK", start, end, 12, 1);
    boolean damaged =
        hit(
            s,
            target,
            number("kick-damage")
                * scale
                * (s.state.hyper ? 1.5 : 1)
                * (1 + s.state.combo * number("kick-combo-damage-per-point")),
            true);
    if (damaged) fx(s, "KICK_HIT", surface(p, target), target.getPos(), 12, 1.5);
    s.state.combo = 0;
    velocity(p, Vec3d.ZERO);
    sound(s, "rider_kick", 1);
    if (!target.isAlive())
      metadata.getCaster().getData().getCooldownMap().resetCooldown(metadata.getCast());
  }

  private static void blocked(Session s) {
    if (!s.state.transformed || s.state.form != KabutoState.Form.MASKED || s.lastBlockTick == tick)
      return;
    s.lastBlockTick = tick;
    s.state.gain(integer("energy-per-block"), integer("gauge-per-block"));
    fx(s, "BLOCK", s.player.getPos(), s.player.getPos(), 10, .7);
  }

  private static void bleed(Session s, LivingEntity target) {
    if (s.bleeds.size() >= 32 && !s.bleeds.containsKey(target.getUuid())) return;
    var old = s.bleeds.get(target.getUuid());
    s.bleeds.put(
        target.getUuid(),
        new Bleed(
            target, tick + integer("bleed-duration-ticks"), old == null ? tick + 20 : old.next));
  }

  private static void shoot(Session s, LivingEntity target, double damage) {
    var p = s.player;
    var world = p.getServerWorld();
    Vec3d
        start =
            p.getEyePos()
                .add(forward(p).multiply(.5))
                .add(
                    forward(p)
                        .crossProduct(new Vec3d(0, 1, 0))
                        .multiply(p.getMainArm() == net.minecraft.util.Arm.LEFT ? -.25 : .25))
                .add(0, -.25, 0),
        direction = target.getBoundingBox().getCenter().subtract(start).normalize();
    // A fast projectile advances over real ticks and stops on the first
    // eligible entity or block, including a bystander crossing its path.
    fx(s, "GUN_MUZZLE", start, start.add(direction), 5, .3);
    projectile(s, start, direction, damage, 0);
  }

  private static void projectile(
      Session s, Vec3d position, Vec3d direction, double damage, int age) {
    if (!valid(s) || age >= 10) return;
    ServerWorld w = s.player.getServerWorld();
    Vec3d next = position.add(direction.multiply(3.2));
    var block =
        w.raycast(
            new net.minecraft.world.RaycastContext(
                position,
                next,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                s.player));
    if (block.getType() != HitResult.Type.MISS) next = block.getPos();
    LivingEntity hit = null;
    Vec3d hitPoint = null;
    double closest = Double.MAX_VALUE;
    for (LivingEntity t :
        w.getEntitiesByClass(
            LivingEntity.class,
            new Box(position, next).expand(.3),
            t -> ReferenceClassSkillRuntime.canTarget(s.player, t))) {
      var contact = t.getBoundingBox().expand(.15).raycast(position, next);
      if (contact.isPresent() && contact.get().squaredDistanceTo(position) < closest) {
        hit = t;
        hitPoint = contact.get();
        closest = contact.get().squaredDistanceTo(position);
      }
    }
    fx(s, "GUN", position, hitPoint == null ? next : hitPoint, 3, .2);
    if (hit != null) {
      boolean damaged = hit(s, hit, damage, false);
      fx(s, damaged ? "GUN_HIT" : "GUN_BLOCK", hitPoint, hitPoint.add(direction), 8, .7);
      return;
    }
    if (block.getType() != HitResult.Type.MISS) {
      fx(s, "GUN_BLOCK", next, next.add(direction), 8, .5);
      return;
    }
    Vec3d end = next;
    later(1, () -> projectile(s, end, direction, damage, age + 1));
  }

  private static void hyperDash(Session s) {
    var p = s.player;
    Vec3d start = p.getPos(), delta = forward(p).multiply(6);
    for (double factor = 1; factor > .1; factor -= .1) {
      Vec3d end = start.add(delta.multiply(factor));
      var ray =
          p.getServerWorld()
              .raycast(
                  new net.minecraft.world.RaycastContext(
                      p.getEyePos(),
                      p.getEyePos().add(delta.multiply(factor)),
                      net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                      net.minecraft.world.RaycastContext.FluidHandling.NONE,
                      p));
      if (ray.getType() == HitResult.Type.MISS
          && p.getServerWorld().isSpaceEmpty(p, p.getBoundingBox().offset(end.subtract(start)))) {
        p.networkHandler.requestTeleport(end.x, end.y, end.z, p.getYaw(), p.getPitch());
        fx(s, "DASH", start, end, 12, 1);
        break;
      }
    }
  }

  private static void aoe(Session s, Vec3d center, double radius, double damage, double up) {
    for (var target : near(s.player, center, radius))
      if (hit(s, target, damage, false)
          && RpgEntityAdapters.allows(s.player, target, RpgEntityAdapters.Effect.MOVEMENT)) {
        velocity(target, target.getPos().subtract(center).multiply(.15).add(0, up, 0));
        target.addStatusEffect(
            new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 2, false, false, true));
      }
  }

  private static boolean hit(Session s, LivingEntity target, double amount, boolean piercing) {
    if (!valid(s) || !ReferenceClassSkillRuntime.canTarget(s.player, target)) return false;
    boolean old = s.executing;
    s.executing = true;
    try {
      var attacker =
          new PlayerMetadata(
              MMOPlayerData.setup(s.player).getStatMap(),
              vn.svframe.svframelib.api.player.EquipmentSlot.MAIN_HAND);
      var attack =
          new AttackMetadata(
              new DamageMetadata(amount, List.of(DamageType.PHYSICAL, DamageType.SKILL)),
              target,
              attacker);
      DamageSource source =
          piercing
              ? new DamageSource(
                  s.player
                      .getServerWorld()
                      .getRegistryManager()
                      .get(RegistryKeys.DAMAGE_TYPE)
                      .entryOf(
                          RegistryKey.of(
                              RegistryKeys.DAMAGE_TYPE, Identifier.of("fantasyhub", "rider_kick"))),
                  s.player)
              : null;
      return SVFrameLib.inst().getDamage().registerAttack(attack, true, false, source);
    } finally {
      s.executing = old;
    }
  }

  private static LivingEntity aim(ServerPlayerEntity p, double range) {
    Vec3d start = p.getEyePos(), end = start.add(p.getRotationVec(1).multiply(range));
    LivingEntity chosen = null;
    double nearest = range * range;
    for (var t :
        p.getServerWorld()
            .getEntitiesByClass(
                LivingEntity.class,
                new Box(start, end).expand(.5),
                t -> ReferenceClassSkillRuntime.canTarget(p, t))) {
      var contact = t.getBoundingBox().expand(.25).raycast(start, end);
      if (contact.isEmpty()) continue;
      double d = contact.get().squaredDistanceTo(start);
      if (d < nearest && p.canSee(t)) {
        nearest = d;
        chosen = t;
      }
    }
    return chosen;
  }

  private static List<LivingEntity> near(ServerPlayerEntity p, Vec3d center, double radius) {
    return p
        .getServerWorld()
        .getEntitiesByClass(
            LivingEntity.class,
            new Box(center, center).expand(radius),
            t ->
                ReferenceClassSkillRuntime.canTarget(p, t)
                    && t.squaredDistanceTo(center) <= radius * radius)
        .stream()
        .sorted(Comparator.comparingDouble(t -> t.squaredDistanceTo(center)))
        .limit(32)
        .toList();
  }

  private static void henshin(Session s) {
    s.state.transformed = true;
    s.state.form = KabutoState.Form.MASKED;
    s.state.clockUntil = 0;
    s.state.hyper = false;
    s.leapUntil = 0;
    applyForm(s);
    fx(s, "HENSHIN", s.player.getPos(), s.player.getPos(), 24, .8);
    sound(s, "henshin", 1);
  }

  private static void applyForm(Session s) {
    float health = s.player.getHealth();
    for (var modifier : s.modifiers) modifier.unregister(MMOPlayerData.setup(s.player));
    s.modifiers.clear();
    if (s.state.transformed) {
      if (s.state.form == KabutoState.Form.MASKED) {
        stat(s, "MAX_HEALTH", number("masked-health"));
        stat(s, "ARMOR", number("masked-armor"));
        stat(s, "PARRY_RATING", number("masked-parry-chance"));
        stat(s, "KNOCKBACK_RESISTANCE", number("masked-knockback-resistance"));
      } else {
        stat(s, "CRITICAL_STRIKE_CHANCE", number("rider-critical-chance"));
        stat(s, "DODGE_RATING", number("rider-dodge-chance"));
      }
    }
    applySpeed(s);
    // Native stat removal may clamp current HP before the replacement modifier
    // is registered. Restore only the original amount, bounded by the final max:
    // repeated Henshin/reload preserves HP and never grants a free heal.
    s.player.setHealth(Math.min(health, s.player.getMaxHealth()));
  }

  private static void stat(Session s, String stat, double amount) {
    var modifier =
        new StatModifier(
            UUID.nameUUIDFromBytes(
                ("kabuto:" + s.player.getUuid() + ":" + stat)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "kabuto_form",
            stat,
            amount,
            ModifierType.FLAT,
            vn.svframe.svframelib.api.player.EquipmentSlot.OTHER,
            vn.svframe.svframelib.player.modifier.ModifierSource.OTHER);
    modifier.register(MMOPlayerData.setup(s.player));
    s.modifiers.add(modifier);
  }

  private static void applySpeed(Session s) {
    if (!s.state.transformed) {
      attribute(s.player, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0);
      attribute(s.player, EntityAttributes.GENERIC_ATTACK_SPEED, 0);
      return;
    }
    attribute(
        s.player,
        EntityAttributes.GENERIC_MOVEMENT_SPEED,
        s.state.clock(tick)
            ? number("clock-speed-multiplier") - 1
            : s.state.form == KabutoState.Form.MASKED ? number("masked-speed-multiplier") - 1 : 0);
    attribute(
        s.player,
        EntityAttributes.GENERIC_ATTACK_SPEED,
        s.state.clock(tick)
            ? number("clock-speed-multiplier") - 1
            : s.state.form == KabutoState.Form.RIDER
                ? number("rider-attack-speed-multiplier") - 1
                : 0);
  }

  private static void attribute(
      ServerPlayerEntity p,
      net.minecraft.registry.entry.RegistryEntry<EntityAttribute> type,
      double value) {
    var attr = p.getAttributeInstance(type);
    if (attr == null) return;
    attr.removeModifier(FORM);
    if (value != 0)
      attr.addTemporaryModifier(
          new EntityAttributeModifier(
              FORM, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
  }

  private static void clear(Session s) {
    PLAYERS.remove(s.player.getUuid());
    for (var m : s.modifiers) m.unregister(MMOPlayerData.setup(s.player));
    attribute(s.player, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0);
    attribute(s.player, EntityAttributes.GENERIC_ATTACK_SPEED, 0);
    s.state.clockUntil = 0;
    s.leapUntil = 0;
    s.player.fallDistance = 0;
    send(s, new Protocol.Kabuto(s.player.getId(), -1, false, false, 0, 0, 0));
  }

  private static Protocol.Kabuto packet(Session s) {
    return new Protocol.Kabuto(
        s.player.getId(),
        s.state.transformed ? s.state.form.ordinal() : -1,
        s.state.clock(tick),
        s.state.hyper,
        s.state.energy,
        s.state.gauge,
        (int) Math.max(0, s.state.clockUntil - tick));
  }

  private static void publish(Session s) {
    var packet = packet(s);
    if (!packet.equals(s.sent)) {
      s.sent = packet;
      send(s, packet);
    }
  }

  private static void cue(Session s, String ability, int duration) {
    var cue = new Protocol.KabutoCue(s.player.getId(), ability, duration);
    for (var player : PlayerLookup.tracking(s.player))
      if (ServerPlayNetworking.canSend(player, Protocol.KabutoCue.ID))
        ServerPlayNetworking.send(player, cue);
    if (ServerPlayNetworking.canSend(s.player, Protocol.KabutoCue.ID))
      ServerPlayNetworking.send(s.player, cue);
  }

  private static void send(Session s, Protocol.Kabuto packet) {
    for (var p : PlayerLookup.tracking(s.player))
      if (ServerPlayNetworking.canSend(p, Protocol.Kabuto.ID)) ServerPlayNetworking.send(p, packet);
    if (ServerPlayNetworking.canSend(s.player, Protocol.Kabuto.ID))
      ServerPlayNetworking.send(s.player, packet);
  }

  private static boolean isClass(ServerPlayerEntity p) {
    return SVFrameMMO.playerData().get(p).getClassId().equals("KABUTO");
  }

  public static boolean frozen(MobEntity mob) {
    var lease = FROZEN.get(mob.getUuid());
    return lease != null && validLease(lease);
  }

  private static boolean validLease(Frozen lease) {
    return lease.until >= tick
        && lease.mob.isAlive()
        && !lease.mob.isRemoved()
        && lease.actor.isAlive()
        && lease.actor.getWorld() == lease.mob.getWorld()
        && clock(lease.actor)
        && ReferenceClassSkillRuntime.canTarget(lease.actor, lease.mob)
        && RpgEntityAdapters.allows(lease.actor, lease.mob, RpgEntityAdapters.Effect.MOVEMENT);
  }

  private static boolean valid(Session s) {
    return PLAYERS.get(s.player.getUuid()) == s
        && s.player.isAlive()
        && s.world == s.player.getServerWorld()
        && !s.player.isDisconnected()
        && isClass(s.player)
        && !BeltEquipment.equipped(s.player).isEmpty();
  }

  private static void later(int delay, Runnable task) {
    vn.svframe.svframelib.fabric.SVFrameLibFabricMod.schedule(delay, task);
  }

  private static Vec3d forward(ServerPlayerEntity p) {
    var d = p.getRotationVec(1);
    return new Vec3d(d.x, 0, d.z).normalize();
  }

  private static void velocity(LivingEntity e, Vec3d v) {
    e.setVelocity(v);
    e.velocityModified = true;
    e.velocityDirty = true;
    if (e instanceof ServerPlayerEntity p)
      p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
  }

  private static void fx(
      Session s, String phase, Vec3d start, Vec3d end, int duration, double radius) {
    if (valid(s)) KabutoEffects.emit(s.player, phase, start, end, s.state.hyper, duration, radius);
  }

  private static Vec3d surface(ServerPlayerEntity actor, LivingEntity target) {
    Vec3d center = target.getBoundingBox().getCenter();
    double radius =
        Math.max(target.getBoundingBox().getLengthX(), target.getBoundingBox().getLengthZ()) * .5;
    return center.add(actor.getEyePos().subtract(center).normalize().multiply(radius + .15));
  }

  private static void sound(Session s, String cue, float pitch) {
    s.player
        .getServerWorld()
        .playSound(
            null,
            s.player.getBlockPos(),
            SoundEvent.of(Identifier.of("fantasyhub", "kabuto." + cue)),
            SoundCategory.PLAYERS,
            1,
            pitch);
  }

  private static double number(String key) {
    Object v = config.get(key);
    if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0)
      throw new IllegalStateException("Invalid Kabuto parameter " + key);
    return n.doubleValue();
  }

  private static int integer(String key) {
    return (int) number(key);
  }
}
