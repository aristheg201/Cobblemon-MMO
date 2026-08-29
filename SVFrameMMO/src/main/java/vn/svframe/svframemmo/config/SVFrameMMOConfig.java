package vn.svframe.svframemmo.config;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.skill.cast.ComboMap;
import vn.svframe.svframemmo.skill.cast.Keybind;
import vn.svframe.svframemmo.skill.cast.PlayerKey;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable native configuration for SVFrameMMO runtime and progression. */
public record SVFrameMMOConfig(
        int resourceTickPeriod,
        int combatTimerSeconds,
        int globalSkillCooldownTicks,
        boolean preventSpawnerXp,
        boolean shouldCobblestoneGeneratorsGiveExp,
        boolean enableGlobalSkillTreeGui,
        boolean enableSpecificSkillTreeGui,
        int skillTreeScrollStepX,
        int skillTreeScrollStepY,
        boolean shiftClickPlayerProfileCheck,
        boolean displayMainClassExpHolograms,
        int autosaveSeconds,
        int defaultLevel,
        int defaultClassPoints,
        int defaultSkillPoints,
        int defaultAttributePoints,
        int defaultReallocationPoints,
        double defaultHealth,
        double defaultMana,
        double defaultStamina,
        double defaultStellium,
        boolean passiveSkillNeedsBinding,
        boolean canCreativeCast,
        boolean saveDefaultClassInfo,
        boolean forceClassSelection,
        boolean shareClassExperience,
        boolean shareSkillPoints,
        boolean shareAttributePoints,
        boolean shareSkillReallocationPoints,
        boolean shareAttributeReallocationPoints,
        SkillCasting skillCasting,
        ActionBar actionBar) {

    private static final Set<String> CASTING_MODES = Set.of("SKILL_BAR", "SKILL_SCROLLER", "KEY_COMBOS", "NONE");

    /** Configuration shared by all four original casting modes. */
    public record SkillCasting(String mode, Keybind openKey, boolean ignoreSneak, boolean useLowestKeybinds, int timeoutTicks,
                               PlayerMessage enterMessage, PlayerMessage quitMessage, SkillBarActionBar actionBar,
                               ScrollerCasting scroller, ComboCasting combos) {
        public boolean skillBarMode() { return "SKILL_BAR".equals(mode); }
        public boolean scrollerMode() { return "SKILL_SCROLLER".equals(mode); }
        public boolean comboMode() { return "KEY_COMBOS".equals(mode); }
        public boolean disabled() { return "NONE".equals(mode); }
        public boolean opensWithSwapHands() { return openKey != null && openKey.key() == PlayerKey.SWAP_HANDS; }
    }

    public record PlayerMessage(String message, boolean actionBar, int duration, int priority, String sound) { }
    public record SkillBarActionBar(String split, String ready, String onCooldown, String noMana, String noStamina) { }
    public record ActionBar(boolean enabled, int updateTicks, String format) { }

    public record SoundSpec(String sound, float volume, float pitch) { }

    /** Original SKILL_SCROLLER controls and feedback. */
    public record ScrollerCasting(Keybind enterKey, Keybind castKey, Keybind scrollKey, Keybind scrollBackKey,
                                  boolean quitOnCast, boolean quitOnSwitchEmptyHand, String actionBarFormat,
                                  SoundSpec enterSound, SoundSpec changeSound, SoundSpec changeBackSound, SoundSpec leaveSound) { }

    /** Original KEY_COMBOS controls and feedback. */
    public record ComboCasting(ComboMap globalCombos, Keybind initializerKey, Keybind quitKey, boolean stayIn,
                               ComboActionBar actionBar, SoundSpec beginComboSound, SoundSpec comboKeySound,
                               SoundSpec failComboSound, SoundSpec failSkillSound) { }

    public record ComboActionBar(String prefix, String suffix, String separator, String noKey,
                                 boolean subtitle, Map<PlayerKey, String> keyNames) { }

    public static SVFrameMMOConfig load(Path file) throws IOException {
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String, Object> defaults = map(root.get("default-playerdata"));
        Map<String, Object> autosave = map(root.get("auto-save"));
        Map<String, Object> combat = map(root.get("combat-log"));
        Map<String, Object> shared = map(first(root, "share-across-classes", "share_across_classes"));
        Map<String, Object> casting = map(first(root, "skill-casting", "skill_casting"));
        Map<String, Object> skillBar = map(first(casting, "action-bar", "action_bar"));
        Map<String, Object> messages = map(casting.get("message"));
        Map<String, Object> enter = map(messages.get("enter"));
        Map<String, Object> quit = map(messages.get("quit"));
        Map<String, Object> actionBar = map(first(root, "action-bar", "action_bar"));
        boolean autosaveEnabled = bool(autosave.get("enabled"), true);

        String castingMode = UtilityMethods.enumName(string(casting.get("mode"), "SKILL_BAR"));
        if (!CASTING_MODES.contains(castingMode))
            throw new IOException("Unknown skill-casting.mode '" + castingMode + "'. Expected one of " + CASTING_MODES);
        int timeout = casting.containsKey("time-out") ? integer(casting.get("time-out"), -1) : 0;
        if (casting.containsKey("time-out") && timeout <= 0)
            throw new IOException("skill-casting.time-out must be strictly positive when configured");

        Keybind openKey = null;
        if ("SKILL_BAR".equals(castingMode)) {
            try { openKey = new Keybind(string(first(casting, "open", "open-key"), "SWAP_HANDS")); }
            catch (RuntimeException exception) { throw new IOException("Invalid SKILL_BAR open key", exception); }
        }

        ScrollerCasting scroller = null;
        if ("SKILL_SCROLLER".equals(castingMode)) scroller = parseScroller(casting);
        ComboCasting combos = null;
        if ("KEY_COMBOS".equals(castingMode)) combos = parseCombos(casting);

        SkillCasting skillCasting = new SkillCasting(
                castingMode,
                openKey,
                bool(first(casting, "ignore-sneak", "disable-sneak"), false),
                bool(first(casting, "use-lowest-keybinds", "use_lowest_keybinds"), true),
                Math.max(0, timeout),
                message(enter, "&e&l☄ &a&lSKILL CASTING &e&l☄", true, 20, 31, "BLOCK_END_PORTAL_FRAME_FILL,1,2"),
                message(quit, "&e&l☄ &c&lSKILL CASTING &e&l☄", true, 20, 31, "BLOCK_FIRE_EXTINGUISH,1,2"),
                new SkillBarActionBar(
                        string(skillBar.get("split"), "&7 &7 - &7 "),
                        string(skillBar.get("ready"), "&6[{index}] &a&l{skill}"),
                        string(skillBar.get("on-cooldown"), "&6[{index}] &c&l{skill} &6(&c{cooldown}&6)"),
                        string(skillBar.get("no-mana"), "&6[{index}] &9&l{skill}"),
                        string(skillBar.get("no-stamina"), "&6[{index}] &9&l{skill}")),
                scroller, combos);

        ActionBar defaultActionBar = new ActionBar(
                bool(actionBar.get("enabled"), true),
                Math.max(1, integer(first(actionBar, "ticks-to-update", "ticks_to_update"), 5)),
                string(actionBar.get("format"), "&c❤ {health}/{max_health} &f| {mana_icon} {mana}/{max_mana} &f| &7⛨ {armor}"));

        return new SVFrameMMOConfig(
                Math.max(1, integer(first(root, "player-resource-tick-period", "player_resource_tick_period"), 20)),
                Math.max(0, integer(combat.get("timer"), 10)),
                Math.max(0, integer(first(root, "global-skill-cooldown", "global_skill_cooldown"), 10)),
                bool(first(root, "prevent-spawner-xp", "prevent_spawner_xp"), true),
                bool(first(root, "should-cobblestone-generators-give-exp", "should_cobblestone_generators_give_exp"), false),
                bool(first(root, "enable-global-skill-tree-gui", "enable_global_skill_tree_gui"), true),
                bool(first(root, "enable-specific-skill-tree-gui", "enable_specific_skill_tree_gui"), true),
                integer(first(root, "skill-tree-scroll-step-x", "skill_tree_scroll_step_x"), 1),
                integer(first(root, "skill-tree-scroll-step-y", "skill_tree_scroll_step_y"), 1),
                bool(first(root, "shift-click-player-profile-check", "shift_click_player_profile_check"), false),
                bool(first(root, "display-main-class-exp-holograms", "display_main_class_exp_holograms"), true),
                autosaveEnabled ? Math.max(1, integer(autosave.get("interval"), 1800)) : 0,
                Math.max(1, integer(defaults.get("level"), 1)),
                Math.max(0, integer(defaults.get("class-points"), 0)),
                Math.max(0, integer(defaults.get("skill-points"), 0)),
                Math.max(0, integer(defaults.get("attribute-points"), 0)),
                Math.max(0, integer(defaults.get("reallocation-points"), 0)),
                Math.max(1d, number(defaults.get("health"), 20d)),
                Math.max(0d, number(defaults.get("mana"), 100d)),
                Math.max(0d, number(defaults.get("stamina"), 100d)),
                Math.max(0d, number(defaults.get("stellium"), 100d)),
                bool(first(root, "passive-skill-needs-binding", "passive-skill-need-bound"), true),
                bool(root.get("can-creative-cast"), false),
                bool(first(root, "save-default-class-info", "save_default_class_info"), false),
                bool(first(root, "force-class-selection", "force_class_selection"), true),
                bool(shared.get("experience"), false),
                bool(first(shared, "skill-points", "skill_points"), false),
                bool(first(shared, "attribute-points", "attribute_points"), false),
                bool(first(shared, "skill-reallocation-points", "skill_reallocation_points"), false),
                bool(first(shared, "attribute-reallocation-points", "attribute_reallocation_points"), false),
                skillCasting, defaultActionBar);
    }

    private static ScrollerCasting parseScroller(Map<String, Object> casting) throws IOException {
        try {
            Keybind enter = new Keybind(required(casting, "enter-key"));
            Keybind cast = new Keybind(required(casting, "cast-key"));
            Keybind scroll = Keybind.fromConfig(casting.get("scroll-key"));
            Keybind scrollBack = Keybind.fromConfig(casting.get("scroll-back-key"));
            Map<String, Object> sounds = map(casting.get("sound"));
            return new ScrollerCasting(enter, cast, scroll, scrollBack,
                    bool(casting.get("quit-on-cast"), false),
                    bool(casting.get("quit-on-switch-empty-hand"), false),
                    string(casting.get("action-bar-format"), "CLICK TO CAST: {selected}"),
                    sound(sounds.get("enter")), sound(sounds.get("change")), sound(sounds.get("change-back")), sound(sounds.get("leave")));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid SKILL_SCROLLER configuration: " + exception.getMessage(), exception);
        }
    }

    private static ComboCasting parseCombos(Map<String, Object> casting) throws IOException {
        try {
            ComboMap global = new ComboMap(required(casting, "combos"));
            Keybind initializer = Keybind.fromConfig(casting.get("initializer-key"));
            Keybind quit = Keybind.fromConfig(casting.get("quit-key"));
            ComboActionBar action = casting.containsKey("action-bar") ? comboActionBar(map(casting.get("action-bar"))) : null;
            Map<String, Object> sounds = map(casting.get("sound"));
            return new ComboCasting(global, initializer, quit, bool(casting.get("stay-in"), false), action,
                    sound(sounds.get("begin-combo")), sound(sounds.get("combo-key")),
                    sound(sounds.get("fail-combo")), sound(sounds.get("fail-skill")));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid KEY_COMBOS configuration: " + exception.getMessage(), exception);
        }
    }

    private static ComboActionBar comboActionBar(Map<String, Object> section) {
        Map<String, Object> names = map(first(section, "key-name", "key_name"));
        LinkedHashMap<PlayerKey, String> keyNames = new LinkedHashMap<>();
        for (PlayerKey key : PlayerKey.values())
            keyNames.put(key, string(findIgnoreCase(names, key.name()), key.name()));
        return new ComboActionBar(
                string(section.get("prefix"), ""), string(section.get("suffix"), ""),
                string(section.get("separator"), " "), string(first(section, "no-key", "no_key"), "_"),
                bool(first(section, "is-subtitle", "is_subtitle"), false), Map.copyOf(keyNames));
    }

    private static SoundSpec sound(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String value) {
            String[] split = value.split(",", -1);
            return new SoundSpec(split[0].trim(), floatValue(split, 1, 1f), floatValue(split, 2, 1f));
        }
        Map<String, Object> map = map(raw);
        if (map.isEmpty()) return null;
        String sound = string(first(map, "sound", "name", "id"), "");
        if (sound.isBlank()) return null;
        return new SoundSpec(sound, (float) number(map.get("volume"), 1d), (float) number(map.get("pitch"), 1d));
    }

    private static PlayerMessage message(Map<String, Object> section, String fallback, boolean actionBar,
                                         int duration, int priority, String sound) {
        return new PlayerMessage(
                string(section.get("message"), fallback),
                bool(first(section, "action-bar", "action_bar"), actionBar),
                Math.max(1, integer(section.get("duration"), duration)),
                integer(section.get("priority"), priority),
                string(section.get("sound"), sound));
    }

    private static Object required(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) throw new IllegalArgumentException("Missing '" + key + "'");
        return value;
    }

    private static Object findIgnoreCase(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private static float floatValue(String[] values, int index, float fallback) {
        if (index >= values.length) return fallback;
        try { return Float.parseFloat(values[index].trim()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static Object first(Map<String, Object> map, String... keys) { for (String key : keys) if (map.containsKey(key)) return map.get(key); return null; }
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, element) -> result.put(String.valueOf(key), element));
        return result;
    }
    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private static int integer(Object value, int fallback) { try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(value.toString()); } catch (RuntimeException ignored) { return fallback; } }
    private static double number(Object value, double fallback) { try { return value instanceof Number number ? number.doubleValue() : value == null ? fallback : Double.parseDouble(value.toString()); } catch (RuntimeException ignored) { return fallback; } }
    private static boolean bool(Object value, boolean fallback) { if (value instanceof Boolean flag) return flag; return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }
}
