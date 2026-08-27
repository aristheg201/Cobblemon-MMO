package vn.svframe.svframelib.skill.handler;

import vn.svframe.svframelib.manager.SkillManager;
import vn.svframe.svframelib.script.Script;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registers the native SVFrameLib built-in and script skill sources. */
public final class NativeBuiltinSkillBootstrap {
    private static final String ROOT = "vn.svframe.svframelib.skill.handler.def.";
    private static final String[] TYPES = {
            "item.Item_Bomb","item.Item_Throw",
            "location.Arcane_Hail","location.Black_Hole","location.Contamination","location.Corrosion","location.Corrupt","location.Freeze","location.Freezing_Curse","location.Ice_Spikes","location.Ignite","location.Life_Ender","location.Lightning_Beam","location.Minor_Explosion","location.Power_Mark","location.Snowman_Turret",
            "misc.Warp",
            "passive.Backstab","passive.Fire_Berserker","passive.Vampirism",
            "simple.Blink","simple.Blizzard","simple.Bunny_Mode","simple.Burning_Hands","simple.Chicken_Wraith","simple.Circular_Slash","simple.Empowered_Attack","simple.Evade","simple.Fire_Rage","simple.Fireball","simple.Firefly","simple.Frog_Mode","simple.Frozen_Aura","simple.Grand_Heal","simple.Heal","simple.Hoearthquake","simple.Leap","simple.Light_Dash","simple.Magical_Path","simple.Magical_Shield","simple.Overload","simple.Present_Throw","simple.Shadow_Veil","simple.Shockwave","simple.Sky_Smash","simple.Swiftness","simple.Throw_Up","simple.Void_Zapper",
            "target.Blind","target.Bloodbath","target.Burn","target.Combo_Attack","target.Confuse","target.Control","target.Death_Mark","target.Deep_Wound","target.Fire_Storm","target.Furtive_Strike","target.Greater_Healings","target.Human_Shield","target.Magma_Fissure","target.Minor_Healings","target.Poison","target.Regen_Ally","target.Shock","target.Slow","target.Smite","target.Sparkle","target.Starfall","target.Stun","target.Tactical_Grenade","target.Targeted_Fireball","target.Telekinesy","target.Weaken","target.Weaken_Target","target.Wither",
            "vector.Arcane_Rift","vector.Bouncy_Fireball","vector.Corrupted_Fangs","vector.Cursed_Beam","vector.Earthquake","vector.Explosive_Turkey","vector.Fire_Meteor","vector.Firebolt","vector.Heavy_Charge","vector.Holy_Missile","vector.Ice_Crystal","vector.Shulker_Missile","vector.TNT_Throw","vector.Thrust"
    };

    private NativeBuiltinSkillBootstrap() { }

    public static void register(SkillManager manager) {
        Map<String, Class<? extends SkillHandler<?>>> types = loadTypes(manager);
        manager.registerSkillHandlerSource(new SkillHandlerSource("default", (config, internal) -> construct(types, config, internal), List.of()));
        manager.registerSkillHandlerSource(new SkillHandlerSource("svframelib", (config, internal) -> {
            Script script = manager.getScriptOrThrow(internal);
            return new MythicLibSkillHandler(script);
        }, List.of("svframelib-skill-id")));
    }

    private static Map<String, Class<? extends SkillHandler<?>>> loadTypes(SkillManager manager) {
        Map<String, Class<? extends SkillHandler<?>>> result = new LinkedHashMap<>();
        for (String path : TYPES) {
            try {
                Class<?> raw = Class.forName(ROOT + path);
                if (!SkillHandler.class.isAssignableFrom(raw))
                    throw new IllegalStateException(raw.getName() + " is not a SkillHandler");
                @SuppressWarnings("unchecked")
                Class<? extends SkillHandler<?>> type = (Class<? extends SkillHandler<?>>) raw;
                manager.registerBuiltinSkillHandlerType(type);
                result.put(norm(type.getSimpleName()), type);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Missing native built-in skill handler " + ROOT + path, exception);
            }
        }
        if (result.size() != 90) throw new IllegalStateException("Expected 90 SVFrameLib built-ins, got " + result.size());
        return Map.copyOf(result);
    }

    private static SkillHandler<?> construct(Map<String, Class<? extends SkillHandler<?>>> types, ConfigObject config, String internal) {
        Class<? extends SkillHandler<?>> type = types.get(norm(internal));
        if (type == null) throw new IllegalArgumentException("Could not find builtin skill with ID '" + internal + "'");
        try {
            Constructor<? extends SkillHandler<?>> constructor = type.getDeclaredConstructor(ConfigObject.class);
            constructor.setAccessible(true);
            return constructor.newInstance(config);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Could not instantiate builtin skill handler '" + internal + "': " + exception.getMessage(), exception);
        }
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
