package vn.svframe.mythiclibfabric;

import java.util.Locale;
import java.util.Set;

/** Exact MythicLib 1.7.1 ownership split for bundled default:* skill IDs. */
public final class BuiltinSkillOwnership {
    private static final Set<String> EXTERNAL_PROVIDER_IDS = Set.of("AMBERS", "NEPTUNE_GIFT", "SNEAKY_PICKY");
    private static final Set<String> NATIVE_IDS = Set.of(
            "ARCANE_HAIL","ARCANE_RIFT","BACKSTAB","BLACK_HOLE","BLIND","BLINK","BLIZZARD","BLOODBATH",
            "BOUNCY_FIREBALL","BUNNY_MODE","BURN","BURNING_HANDS","CHICKEN_WRAITH","CIRCULAR_SLASH","COMBO_ATTACK","CONFUSE",
            "CONTAMINATION","CONTROL","CORROSION","CORRUPT","CORRUPTED_FANGS","CURSED_BEAM","DEATH_MARK","DEEP_WOUND",
            "EARTHQUAKE","EMPOWERED_ATTACK","EVADE","EXPLOSIVE_TURKEY","FIRE_BERSERKER","FIRE_METEOR","FIRE_RAGE","FIRE_STORM",
            "FIREBALL","FIREBOLT","FIREFLY","FREEZE","FREEZING_CURSE","FROG_MODE","FROZEN_AURA","FURTIVE_STRIKE","GRAND_HEAL",
            "GREATER_HEALINGS","HEAL","HEAVY_CHARGE","HOEARTHQUAKE","HOLY_MISSILE","HUMAN_SHIELD","ICE_CRYSTAL","ICE_SPIKES","IGNITE",
            "ITEM_BOMB","ITEM_THROW","LEAP","LIFE_ENDER","LIGHT_DASH","LIGHTNING_BEAM","MAGICAL_PATH","MAGICAL_SHIELD","MAGMA_FISSURE",
            "MINOR_EXPLOSION","MINOR_HEALINGS","OVERLOAD","POISON","POWER_MARK","PRESENT_THROW","REGEN_ALLY","SHADOW_VEIL",
            "SHOCK","SHOCKWAVE","SHULKER_MISSILE","SKY_SMASH","SLOW","SMITE","SNOWMAN_TURRET","SPARKLE","STARFALL",
            "STUN","SWIFTNESS","TACTICAL_GRENADE","TARGETED_FIREBALL","TELEKINESY","THROW_UP","THRUST","TNT_THROW","VAMPIRISM",
            "VOID_ZAPPER","WARP","WEAKEN","WEAKEN_TARGET","WITHER");

    static {
        if (NATIVE_IDS.size() != 90) throw new ExceptionInInitializerError("Expected 90 MythicLib native built-ins, got " + NATIVE_IDS.size());
        if (EXTERNAL_PROVIDER_IDS.size() != 3) throw new ExceptionInInitializerError("Expected 3 external provider built-ins, got " + EXTERNAL_PROVIDER_IDS.size());
        for (String external : EXTERNAL_PROVIDER_IDS)
            if (NATIVE_IDS.contains(external)) throw new ExceptionInInitializerError("Built-in ownership overlap: " + external);
    }

    private BuiltinSkillOwnership() { }

    public static Set<String> nativeIds() { return NATIVE_IDS; }
    public static Set<String> externalProviderIds() { return EXTERNAL_PROVIDER_IDS; }
    public static boolean isNative(String id) { return NATIVE_IDS.contains(norm(id)); }
    public static boolean isExternalProvider(String id) { return EXTERNAL_PROVIDER_IDS.contains(norm(id)); }

    private static String norm(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
