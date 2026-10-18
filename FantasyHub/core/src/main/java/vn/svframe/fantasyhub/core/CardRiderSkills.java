package vn.svframe.fantasyhub.core;

import static vn.svframe.fantasyhub.core.RiderRuntime.*;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.*;
import net.minecraft.entity.effect.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import vn.svframe.fantasyhub.protocol.Protocol;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.DamageMetadata;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.fabric.ReferenceClassSkillRuntime;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;

/**
 * Card Riders use the same sessions, cast validation, RPG damage and collision helpers as
 * Agito/Zolda.
 */
final class CardRiderSkills {
  private static final Identifier SPEED = Identifier.of("fantasyhub", "card_speed"),
      ATTACK_SPEED = Identifier.of("fantasyhub", "card_attack_speed");

  private record Control(Session owner, LivingEntity target, long until) {}

  private static final Map<UUID, Control> STUNS = new HashMap<>(), WOUNDS = new HashMap<>();

  static boolean supports(Session s) {
    return Set.of("RYUKI", "KNIGHT", "DECADE").contains(s.type);
  }

  static void install() {
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          STUNS.entrySet().removeIf(e -> expired(e.getValue()));
          WOUNDS.entrySet().removeIf(e -> expired(e.getValue()));
          for (var lease : STUNS.values()) {
            if (RpgEntityAdapters.allows(
                lease.owner.player, lease.target, RpgEntityAdapters.Effect.MOVEMENT))
              velocity(lease.target, Vec3d.ZERO);
          }
        });
  }

  private static boolean expired(Control c) {
    return tick >= c.until
        || !valid(c.owner)
        || !c.owner.active()
        || !c.target.isAlive()
        || c.target.isRemoved()
        || c.target.getWorld() != c.owner.world;
  }

  static boolean stunned(LivingEntity e) {
    var c = STUNS.get(e.getUuid());
    return c != null
        && !expired(c)
        && RpgEntityAdapters.allows(c.owner.player, e, RpgEntityAdapters.Effect.MOVEMENT);
  }

  static float healing(LivingEntity e, float amount) {
    var c = WOUNDS.get(e.getUuid());
    return c != null
            && !expired(c)
            && RpgEntityAdapters.allows(c.owner.player, e, RpgEntityAdapters.Effect.STATUS)
        ? amount * .5f
        : amount;
  }

  static boolean ready(Session s, String ability, LivingEntity target, boolean ground) {
    int level = vn.svframe.svframemmo.SVFrameMMO.playerData().get(s.player).getLevel();
    if (Set.of("SURVIVE", "COMPLETE").contains(ability)) return level >= 20;
    if (Set.of("FINAL_VENT", "ULTIMATE_SUMMON", "DIMENSION_KICK").contains(ability) && level < 50)
      return false;
    if (s.type.equals("DECADE")
        && Set.of("ULTIMATE_SUMMON", "DIMENSION_KICK").contains(ability)
        && !s.cards.elevated) return false;
    if (ability.equals("CLOCK_UP") || ability.equals("FINAL_VENT") && !s.cards.elevated)
      return target != null;
    return !ability.equals("SHOOT_VENT") || s.type.equals("ZOLDA") || target != null || ground;
  }

  static void tick(Session s) {
    if (!s.active()) return;
    if (s.cards.guardUntil <= tick) s.cards.barrier = 0;
    if (tick % 10 == 0 && s.cards.elevated)
      cue(s, s.type.equals("DECADE") ? "CARDS" : "AURA", s.player.getPos(), s.player.getPos(), 12);
    if (s.type.equals("RYUKI") && s.cards.elevated && tick % 20 == 0)
      for (var t : near(s, s.player.getPos(), 2.5)) burn(s, t, 40);
  }

  static void applyForm(Session s) {
    attribute(
        s.player,
        EntityAttributes.GENERIC_MOVEMENT_SPEED,
        SPEED,
        !s.active()
            ? 0
            : s.type.equals("DECADE") && s.cards.spirit(tick) == 2
                ? 1
                : s.cards.elevated ? n(s, "survive-speed-multiplier") - 1 : 0);
    attribute(
        s.player,
        EntityAttributes.GENERIC_ATTACK_SPEED,
        ATTACK_SPEED,
        s.active() && s.cards.elevated ? n(s, "survive-attack-speed-multiplier") - 1 : 0);
  }

  static void clear(Session s) {
    STUNS.values().removeIf(c -> c.owner == s);
    WOUNDS.values().removeIf(c -> c.owner == s);
    attribute(s.player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED, 0);
    attribute(s.player, EntityAttributes.GENERIC_ATTACK_SPEED, ATTACK_SPEED, 0);
  }

  static void attack(Session s, LivingEntity target, DamageMetadata damage) {
    if (!supports(s)) return;
    if (s.type.equals("DECADE") && s.cards.elevated) damage.multiplicativeModifier(1.3);
    else if (s.cards.elevated) damage.multiplicativeModifier(n(s, "survive-damage-multiplier"));
    if (s.type.equals("KNIGHT") && vulnerable(s, target)) {
      if (!damage.isWeaponCriticalStrike() && !damage.isSkillCriticalStrike()) {
        damage.multiplicativeModifier(1.5);
        if (damage.hasType(vn.svframe.svframelib.damage.DamageType.SKILL))
          damage.registerSkillCriticalStrike();
        else damage.registerWeaponCriticalStrike();
      }
    }
  }

  private static boolean vulnerable(Session s, LivingEntity target) {
    Vec3d facing = target.getRotationVec(1).multiply(1, 0, 1).normalize();
    return target.hasStatusEffect(StatusEffects.BLINDNESS)
        || stunned(target)
        || facing.dotProduct(s.player.getPos().subtract(target.getPos()).normalize()) < -.35;
  }

  static void normalHit(Session s, LivingEntity target, float amount, DamageSource source) {
    if (!source.isOf(DamageTypes.PLAYER_ATTACK)
        && !source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_PROJECTILE)) return;
    // Secondary projectiles never re-enter this callback: hit() owns the executing flag.
    var previous = s.action;
    s.action = new CombatAction();
    try {
      skillHit(s, target, amount);
      switch (s.type) {
        case "RYUKI" -> {
          if (s.cards.elevated)
            bolt(
                s,
                s.player.getEyePos(),
                target.getEyePos().subtract(s.player.getEyePos()).normalize(),
                5,
                1.7,
                true);
        }
        case "DECADE" -> {
          if (s.cards.consumeCards()) {
            s.action.trueDamage = true;
            for (int i = 0; i < 3; i++) {
              int index = i;
              later(
                  s,
                  i * 2,
                  () -> {
                    hit(s, target, 3, false);
                    cue(
                        s,
                        "CARD_HIT",
                        s.player.getEyePos().add(index * .1, 0, 0),
                        target.getEyePos(),
                        8);
                  });
            }
          }
          if (s.cards.spirit(tick) == 3) area(s, target.getPos(), 2, 4, .1, true);
        }
      }
    } finally {
      s.action = previous;
    }
  }

  static void skillHit(Session s, LivingEntity target, double amount) {
    if (s.action != null && s.action.suppressive) {
      slow(s, target, 60, .5);
      if (RpgEntityAdapters.allows(s.player, target, RpgEntityAdapters.Effect.STATUS))
        WOUNDS.put(target.getUuid(), new Control(s, target, tick + 60));
    }
    if (s.type.equals("KNIGHT") && s.cards.elevated && amount > 0)
      s.player.heal((float) (amount * n(s, "lifesteal-fraction")));
    if (s.action != null && s.action.awarded) return;
    if (s.action != null) s.action.awarded = true;
    var data = vn.svframe.svframemmo.SVFrameMMO.playerData().get(s.player);
    if (s.type.equals("RYUKI") && s.cards.resonance()) {
      data.giveMana(
          data.getMaxResource(vn.svframe.svframemmo.api.player.profess.resource.PlayerResource.MANA)
              * .05,
          ResourceUpdateReason.OTHER);
      area(s, s.player.getPos(), 3, 6, .1, true);
      cue(s, "RESONANCE", s.player.getPos(), s.player.getPos(), 20);
    }
    if (s.type.equals("KNIGHT")) {
      if (vulnerable(s, target)) data.giveStamina(4, ResourceUpdateReason.OTHER);
    }
  }

  static float incoming(Session s, DamageSource source, float amount) {
    if (s.cards.guardUntil > tick) {
      if (s.type.equals("KNIGHT")) return amount * .2f;
      if (s.type.equals("RYUKI")
          && source.getAttacker() instanceof LivingEntity attacker
          && attacker != s.player
          && attacker.squaredDistanceTo(s.player) <= 16
          && !s.executing
          && ReferenceClassSkillRuntime.canTarget(s.player, attacker)) {
        var previous = s.action;
        s.action = new CombatAction();
        s.action.awarded = true;
        try {
          hit(s, attacker, amount * .25, true);
          burn(s, attacker, 40);
        } finally {
          s.action = previous;
        }
      }
      return (float) s.cards.absorb(amount);
    }
    return amount;
  }

  static void execute(Session s, String ability, LivingEntity target, Vec3d point) {
    if (s.type.equals("DECADE")) decade(s, ability, target, point);
    else if (s.type.equals("RYUKI")) ryuki(s, ability, target, point);
    else knight(s, ability, target, point);
    if (s.type.equals("DECADE") && !Set.of("COMPLETE", "HENSHIN").contains(ability))
      s.cards.nextCards = true;
    if (s.type.equals("KNIGHT") && s.cards.elevated && !ability.equals("SURVIVE")) {
      // The original cooldown is already active. This affects only the cast just accepted.
      var cd =
          MMOPlayerData.setup(s.player)
              .getCooldownMap()
              .getInfo("knight_" + ability.toLowerCase(Locale.ROOT));
      if (cd != null) cd.reduceRemainingCooldown(.2);
    }
    cue(s, "VENT", s.player.getPos(), point, 12);
  }

  private static void ryuki(Session s, String ability, LivingEntity target, Vec3d point) {
    var p = s.player;
    boolean survive = s.cards.elevated;
    switch (ability) {
      case "SURVIVE" -> {
        s.cards.toggle();
        cue(s, "SURVIVE", p.getPos(), p.getPos(), 35);
      }
      case "STRIKE_VENT" -> {
        s.action.rapid = true;
        for (int i = 0; i < (survive ? 3 : 1); i++)
          later(
              s,
              i * 5,
              () -> bolt(s, p.getEyePos(), p.getRotationVec(1), n(s, "strike-damage"), 1.8, true));
      }
      case "SWORD_VENT" -> {
        s.action.rapid = true;
        s.action.armorPierce = survive ? .5 : 0;
        Vec3d start = p.getPos();
        if (survive) dash(s, 6);
        for (int i = 0; i < (survive ? 1 : 2); i++)
          later(
              s,
              i * 4,
              () -> {
                if (survive) line(s, start, p.getPos(), 1.5, n(s, "sword-damage") * 2, true);
                else
                  for (var t : cone(s, p.getPos(), 3, 70))
                    if (hit(s, t, n(s, "sword-damage"), true)) bleed(s, t);
                cue(
                    s,
                    survive ? "PURPLE_SLASH" : "FIRE_SLASH",
                    p.getPos(),
                    p.getPos().add(forward(p).multiply(3)),
                    12);
              });
      }
      case "GUARD_VENT" -> {
        s.cards.barrier = p.getMaxHealth() * n(s, "guard-fraction");
        s.cards.guardUntil = tick + (int) n(s, "guard-duration-ticks");
        cue(s, "SCALES", p.getPos(), p.getPos(), (int) n(s, "guard-duration-ticks"));
      }
      case "ADVENT" -> {
        s.action.rapid = true;
        var dragon = summon(s, "dragreder", p.getPos().add(0, 3, 0), 1);
        for (int i = 0; i < 50; i += 5) {
          int phase = i;
          later(
              s,
              i,
              () -> {
                if (dragon != null)
                  dragon.setPosition(
                      p.getPos()
                          .add(
                              Math.cos(phase * .16) * 3,
                              2 + phase * .02,
                              Math.sin(phase * .16) * 3));
                area(s, point, 3, n(s, "advent-damage"), .5, true);
                cue(s, "FIRE_RAIN", point.add(0, 3, 0), point, 8);
              });
        }
        later(s, 55, () -> retire(s, dragon));
      }
      case "SHOOT_VENT" -> {
        s.action.suppressive = true;
        s.action.rapid = true;
        for (int i = 0; i < 8; i++) {
          int index = i;
          later(
              s,
              i * 3,
              () -> {
                var origin = p.getEyePos().add((index % 3 - 1) * .25, .3, 0);
                bolt(
                    s,
                    origin,
                    point.add(0, .6, 0).subtract(origin).normalize(),
                    n(s, "shoot-damage"),
                    2,
                    true);
              });
        }
      }
      case "FINAL_VENT" -> {
        if (survive) {
          var dragon = summon(s, "dragranzer", p.getPos().add(0, .6, 0), 1.25);
          rush(s, 14, 2.5, n(s, "final-damage"), true, dragon);
        } else airborneKick(s, target, "dragreder", true);
      }
      default -> throw new IllegalArgumentException("Unknown Ryuki card " + ability);
    }
  }

  private static void knight(Session s, String ability, LivingEntity target, Vec3d point) {
    var p = s.player;
    switch (ability) {
      case "SURVIVE" -> {
        s.cards.toggle();
        cue(s, "SURVIVE", p.getPos(), p.getPos(), 35);
      }
      case "SWORD_VENT" -> {
        s.action.armorPierce = .5;
        s.action.rapid = true;
        Vec3d origin = p.getPos();
        dash(s, 5);
        line(s, origin, p.getPos(), 1, n(s, "sword-damage"), false);
        for (var t : near(s, p.getPos(), 2)) slow(s, t, 40, .35);
        if (s.cards.elevated)
          later(
              s,
              5,
              () -> {
                Vec3d a = p.getPos();
                dashToward(s, origin, 5);
                line(s, a, p.getPos(), 1, n(s, "sword-damage"), false);
              });
        cue(s, "DARK_SLASH", origin, p.getPos(), 15);
      }
      case "NASTY_VENT" -> {
        var bat = summon(s, "darkwing", p.getPos().add(0, 2, 0), .8);
        later(s, 35, () -> retire(s, bat));
        for (var t : cone(s, p.getPos(), 7, 55))
          if (hit(s, t, n(s, "strike-damage"), false)) {
            if (RpgEntityAdapters.allows(p, t, RpgEntityAdapters.Effect.MOVEMENT)) {
              STUNS.put(t.getUuid(), new Control(s, t, tick + 30));
              slow(s, t, 30, 1);
            }
            status(s, t, StatusEffects.BLINDNESS, 60);
          }
        cue(s, "SONIC", p.getEyePos(), p.getEyePos().add(forward(p).multiply(7)), 30);
      }
      case "TRICK_VENT" -> {
        status(s, p, StatusEffects.INVISIBILITY, 50);
        s.action.rapid = true;
        for (int i = 0; i < 3; i++) {
          int index = i;
          later(
              s,
              i * 4,
              () -> {
                Vec3d at =
                    p.getPos()
                        .add(forward(p).rotateY((float) (index * 2 * Math.PI / 3)).multiply(1.4));
                cue(s, "KNIGHT_PHANTOM", at, point, 22);
                for (var t : cone(s, at, 5, 70)) hit(s, t, n(s, "sword-damage"), false);
              });
        }
      }
      case "GUARD_VENT" -> {
        s.cards.guardUntil = tick + 40;
        area(s, p.getPos(), 3, 2, .25, false);
        cue(s, "CLOAK", p.getPos(), p.getPos(), 40);
      }
      case "BLAST_VENT" -> {
        s.action.rapid = true;
        for (int i = 0; i < 30; i += 3)
          later(
              s,
              i,
              () -> {
                for (var t : near(s, p.getPos(), 3.5))
                  if (hit(s, t, 3, false)
                      && RpgEntityAdapters.allows(p, t, RpgEntityAdapters.Effect.MOVEMENT))
                    velocity(
                        t,
                        p.getPos().subtract(t.getPos()).normalize().multiply(.35).add(0, .08, 0));
                cue(s, "TORNADO", p.getPos(), p.getPos(), 6);
              });
      }
      case "FINAL_VENT" -> {
        if (s.cards.elevated) {
          var bat = summon(s, "darkwing", p.getPos().add(0, 1.5, 0), 1);
          rush(s, 15, 1.8, n(s, "final-damage"), false, bat);
        } else airborneKick(s, target, "darkwing", false);
      }
      default -> throw new IllegalArgumentException("Unknown Knight card " + ability);
    }
  }

  private static void decade(Session s, String ability, LivingEntity target, Vec3d point) {
    var p = s.player;
    switch (ability) {
      case "COMPLETE" -> {
        s.cards.toggle();
        if (s.cards.elevated) p.removeStatusEffect(StatusEffects.SLOWNESS);
        cue(s, "COMPLETE", p.getPos(), p.getPos(), 40);
      }
      case "SLASH_BLAST" -> {
        s.action.rapid = true;
        if (s.cards.recast(tick)) {
          cue(s, "BOOKER_GUN", p.getPos(), point, 40);
          Vec3d back = p.getPos().subtract(forward(p).multiply(2));
          dashToward(s, back, 2);
          for (int i = 0; i < 4; i++)
            later(s, i * 2, () -> bolt(s, p.getEyePos(), p.getRotationVec(1), 6, 0, false));
        } else {
          s.cards.recastUntil = tick + 50;
          for (int i = 0; i < 3; i++)
            later(
                s,
                i * 3,
                () -> {
                  for (var t : cone(s, p.getPos(), 3.5, 65)) hit(s, t, n(s, "sword-damage"), false);
                  cue(s, "MATRIX_SLASH", p.getPos(), p.getPos().add(forward(p).multiply(3)), 10);
                });
        }
      }
      case "ILLUSION" -> {
        s.action.rapid = true;
        s.cards.illusionUntil = tick + 40;
        Vec3d side = forward(p).rotateY((float) Math.PI / 2).multiply(1.3);
        for (int i = -1; i <= 1; i++) {
          Vec3d from = p.getEyePos().add(side.multiply(i));
          if (i != 0) cue(s, "DECADE_PHANTOM", from.add(0, -1.6, 0), point, 40);
          bolt(
              s,
              from,
              point.add(0, 1, 0).subtract(from).normalize(),
              n(s, "strike-damage"),
              0,
              false);
        }
      }
      case "CLOCK_UP" -> {
        status(s, p, StatusEffects.INVISIBILITY, 30);
        cue(s, "CLOCK", p.getPos(), target.getPos(), 30);
        later(
            s,
            30,
            () -> {
              if (!ReferenceClassSkillRuntime.canTarget(p, target)
                  || !p.canSee(target)
                  || target.squaredDistanceTo(p) > 576) return;
              Vec3d behind =
                  target.getPos().subtract(target.getRotationVec(1).multiply(1, 0, 1).normalize());
              dashToward(s, behind, 24);
              if (p.squaredDistanceTo(target) > 9) return;
              hit(s, target, n(s, "sword-damage") * 1.5, false);
              cue(s, "MATRIX_SLASH", p.getPos(), target.getEyePos(), 12);
            });
      }
      case "HEISEI_SPIRIT" -> {
        int spirit = s.cards.cycleSpirit(tick, 100);
        if (spirit == 1) {
          s.cards.barrier = p.getMaxHealth() * .35;
          s.cards.guardUntil = tick + 100;
        }
        cue(s, "SPIRIT_" + spirit, p.getPos(), p.getPos(), 100);
      }
      case "ULTIMATE_SUMMON" -> {
        // Complete summons the ultimate power; the player's form string never changes.
        s.action.artillery = true;
        s.action.rapid = true;
        cue(s, "ULTIMATE_PHANTOM", p.getPos().subtract(forward(p).multiply(1.5)), point, 40);
        for (int i = 0; i < 6; i++)
          later(
              s,
              10 + i * 4,
              () ->
                  bolt(
                      s,
                      p.getEyePos().add(0, .4, 0),
                      point.add(0, 1, 0).subtract(p.getEyePos()).normalize(),
                      8,
                      1.8,
                      false));
      }
      case "DIMENSION_KICK" -> {
        s.action.trueDamage = true;
        Vec3d origin = p.getPos();
        Vec3d end =
            origin.add(forward(p).multiply(Math.min(18, Math.max(6, origin.distanceTo(point)))));
        cue(s, "DIMENSION", origin, end, 40);
        s.ultimateUntil = tick + 34;
        later(
            s,
            20,
            () ->
                rush(
                    s,
                    Math.min(18, origin.distanceTo(end)),
                    1.5,
                    n(s, "final-damage") * 1.5,
                    false,
                    null));
      }
      default -> throw new IllegalArgumentException("Unknown Decade card " + ability);
    }
  }

  private static void burn(Session s, LivingEntity t, int ticks) {
    if (RpgEntityAdapters.allows(s.player, t, RpgEntityAdapters.Effect.STATUS))
      t.setOnFireFor(ticks / 20f);
  }

  private static void status(
      Session s,
      LivingEntity t,
      net.minecraft.registry.entry.RegistryEntry<StatusEffect> effect,
      int ticks) {
    if (t == s.player || RpgEntityAdapters.allows(s.player, t, RpgEntityAdapters.Effect.STATUS))
      t.addStatusEffect(new StatusEffectInstance(effect, ticks, 0));
  }

  private static void bleed(Session s, LivingEntity target) {
    burn(s, target, 60);
    for (int i = 20; i <= 60; i += 20) later(s, i, () -> hit(s, target, 2, true));
  }

  private static List<LivingEntity> cone(Session s, Vec3d origin, double range, double halfAngle) {
    double cosine = Math.cos(Math.toRadians(halfAngle));
    return near(s, origin, range).stream()
        .filter(
            t ->
                s.player.canSee(t)
                    && forward(s.player).dotProduct(t.getPos().subtract(origin).normalize())
                        >= cosine)
        .toList();
  }

  private static void line(Session s, Vec3d a, Vec3d b, double width, double damage, boolean fire) {
    for (var t : near(s, a, Math.min(32, a.distanceTo(b) + width)))
      if (t.getBoundingBox().expand(width).raycast(a.add(0, 1, 0), b.add(0, 1, 0)).isPresent()
          && s.player.canSee(t)) hit(s, t, damage, fire);
  }

  private static void rush(
      Session s,
      double range,
      double width,
      double damage,
      boolean fire,
      net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity summon) {
    Vec3d origin = s.player.getPos();
    Vec3d destination = origin.add(forward(s.player).multiply(range));
    Set<UUID> struck = new HashSet<>();
    s.ultimateUntil = tick + 12;
    for (int i = 1; i <= 6; i++) {
      int step = i;
      later(
          s,
          i,
          () -> {
            Vec3d from = s.player.getPos();
            dashToward(s, origin.lerp(destination, step / 6d), range / 6 + .25);
            Vec3d to = s.player.getPos();
            if (summon != null) summon.setPosition(to.add(0, .6, 0));
            for (var t : near(s, from, width + range / 6))
              if (!struck.contains(t.getUuid())
                  && t.getBoundingBox()
                      .expand(width)
                      .raycast(from.add(0, 1, 0), to.add(0, 1, 0))
                      .isPresent()
                  && s.player.canSee(t)) {
                if (hit(s, t, damage, fire)) {
                  struck.add(t.getUuid());
                  if (fire) burn(s, t, 80);
                }
              }
            cue(s, fire ? "DRAGON_CHARGE" : "DARK_RUSH", from, to, 10);
          });
    }
    later(s, 12, () -> retire(s, summon));
  }

  private static void airborneKick(Session s, LivingEntity target, String monster, boolean fire) {
    if (target == null) return;
    var p = s.player;
    Vec3d origin = p.getPos();
    s.ultimateUntil = tick + 34;
    cue(s, fire ? "DRAGON_CREST" : "TORNADO", origin, target.getPos(), 30);
    var summon = summon(s, monster, origin.subtract(forward(p).multiply(2)).add(0, 2, 0), .9);
    later(
        s,
        20,
        () -> {
          if (!p.canSee(target)) return;
          dashToward(s, p.getPos().add(0, 2, 0), 2);
          velocity(p, new Vec3d(0, .5, 0));
        });
    later(
        s,
        27,
        () -> {
          if (!ReferenceClassSkillRuntime.canTarget(p, target)
              || !p.canSee(target)
              || target.squaredDistanceTo(p) > 400) return;
          dashToward(s, target.getPos(), 12);
          area(s, p.getPos(), 3, n(s, "final-damage"), .5, fire);
          cue(s, fire ? "KICK_IMPACT" : "DARK_RUSH", p.getPos(), p.getPos(), 20);
        });
    later(s, 34, () -> retire(s, summon));
  }

  private static net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity summon(
      Session s, String model, Vec3d point, double scale) {
    RiderSummons.remove(s.summon);
    s.summon = RiderSummons.spawn(s.player, point, model, (float) scale);
    return s.summon;
  }

  private static void retire(
      Session s, net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity entity) {
    RiderSummons.remove(entity);
    if (s.summon == entity) s.summon = null;
  }

  private static void bolt(
      Session s, Vec3d origin, Vec3d direction, double damage, double radius, boolean fire) {
    if (s.activeProjectiles >= 32) return;
    s.activeProjectiles++;
    advance(s, origin, direction.normalize(), damage, radius, fire, 0);
  }

  private static void advance(
      Session s,
      Vec3d start,
      Vec3d direction,
      double damage,
      double radius,
      boolean fire,
      int age) {
    Vec3d end = start.add(direction.multiply(1.5));
    var block =
        s.world.raycast(
            new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                s.player));
    if (block.getType() != HitResult.Type.MISS) end = block.getPos();
    LivingEntity chosen = null;
    Vec3d impact = end;
    double closest = start.squaredDistanceTo(end) + 1e-8;
    for (var t :
        s.world.getEntitiesByClass(
            LivingEntity.class,
            new Box(start, end).expand(.3),
            e -> ReferenceClassSkillRuntime.canTarget(s.player, e))) {
      var contact = t.getBoundingBox().expand(.1).raycast(start, end);
      if (contact.isPresent() && start.squaredDistanceTo(contact.get()) < closest) {
        chosen = t;
        impact = contact.get();
        closest = start.squaredDistanceTo(impact);
      }
    }
    cue(s, fire ? "FIRE_BOLT" : "MATRIX_BOLT", start, end, 3);
    if (chosen != null || block.getType() != HitResult.Type.MISS) {
      if (radius > 0) area(s, impact, radius, damage, .2, fire);
      else if (chosen != null) hit(s, chosen, damage, fire);
      if (fire) for (var t : near(s, impact, Math.max(.5, radius))) burn(s, t, 60);
      cue(s, fire ? "KICK_IMPACT" : "CARD_HIT", impact, impact, 10);
      s.activeProjectiles--;
      return;
    }
    if (age >= 23) {
      s.activeProjectiles--;
      return;
    }
    Vec3d next = end;
    later(s, 1, () -> advance(s, next, direction, damage, radius, fire, age + 1));
  }

  static void cue(Session s, String phase, Vec3d start, Vec3d end, int duration) {
    var packet =
        new Protocol.RiderFx(
            s.player.getId(),
            s.type,
            phase,
            start.x,
            start.y,
            start.z,
            end.x,
            end.y,
            end.z,
            duration);
    for (var observer : PlayerLookup.around(s.world, start, 64))
      if (ServerPlayNetworking.canSend(observer, Protocol.RiderFx.ID))
        ServerPlayNetworking.send(observer, packet);
    if (phase.equals("KICK_IMPACT") || phase.equals("MATRIX_BOLT") && s.activeProjectiles < 2)
      s.world.playSound(
          null,
          start.x,
          start.y,
          start.z,
          net.minecraft.sound.SoundEvent.of(
              Identifier.of(
                  "fantasyhub", phase.equals("KICK_IMPACT") ? "riders.impact" : "riders.shot")),
          net.minecraft.sound.SoundCategory.PLAYERS,
          .6f,
          1);
    if (phase.equals("VENT") || Set.of("HENSHIN", "SURVIVE", "COMPLETE").contains(phase))
      s.world.playSound(
          null,
          start.x,
          start.y,
          start.z,
          net.minecraft.sound.SoundEvent.of(
              Identifier.of(
                  "fantasyhub",
                  Set.of("HENSHIN", "SURVIVE", "COMPLETE").contains(phase)
                      ? "riders.form"
                      : "riders.vent")),
          net.minecraft.sound.SoundCategory.PLAYERS,
          1,
          1);
  }
}
