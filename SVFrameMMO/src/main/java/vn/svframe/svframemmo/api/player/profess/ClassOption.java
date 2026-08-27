package vn.svframe.svframemmo.api.player.profess;

import java.util.Locale;

public enum ClassOption {
    DEFAULT,
    NEEDS_PERMISSION,
    DISPLAY(true),
    OFF_COMBAT_HEALTH_REGEN,
    OFF_COMBAT_MANA_REGEN,
    OFF_COMBAT_STAMINA_REGEN,
    OFF_COMBAT_STELLIUM_REGEN;

    private final boolean defaultValue;

    ClassOption() { this(false); }
    ClassOption(boolean defaultValue) { this.defaultValue = defaultValue; }

    public boolean getDefault() { return defaultValue; }
    public String getPath() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }

    public static ClassOption fromPath(String path) {
        return valueOf(path.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
    }
}
