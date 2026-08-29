package vn.svframe.svframemmo.api.player.profess;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.gui.util.IconOptions;
import vn.svframe.svframelib.manager.SkillManager;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.particle.ParticleInformation;
import vn.svframe.svframelib.player.skill.PassiveSkill;
import vn.svframe.svframelib.script.Script;
import vn.svframe.svframelib.skill.SimpleSkill;
import vn.svframe.svframelib.skill.handler.SVFrameLibSkillHandler;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.parameter.value.FormulaFailsafeException;
import vn.svframe.svframelib.skill.parameter.value.ScalingFormula;
import vn.svframe.svframelib.skill.trigger.TriggerType;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.api.player.profess.resource.ResourceRegeneration;
import vn.svframe.svframemmo.experience.curve.ExperienceCurve;
import vn.svframe.svframemmo.experience.curve.ExperienceCurveRegistry;
import vn.svframe.svframemmo.skill.ClassSkill;
import vn.svframe.svframemmo.skill.cast.ComboMap;
import vn.svframe.svframemmo.trigger.NativeTriggerRegistry;
import vn.svframe.svframemmo.trigger.Trigger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native player-class definition backed by the class configuration corpus. */
public final class PlayerClass {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-PlayerClass");

    public record SkillSlotDefinition(int slot, String name, String formula, List<String> lore,
                                      boolean unlockedByDefault, boolean canManuallyBind,
                                      String hardset, List<String> skillBuffs) { }

    private final String id;
    private final String name;
    private final String actionBarFormat;
    private final List<String> description;
    private final List<String> attributeDescription;
    private final IconOptions icon;
    private final Map<ClassOption, Boolean> options = new EnumMap<>(ClassOption.class);
    private final int maxLevel;
    private final int displayOrder;
    private final ExperienceCurve expCurve;
    private final String experienceTableId;
    private final List<String> skillTreeIds;
    private final List<String> mainExperienceSources;
    private final Map<String, ScalingFormula> defaultStats;
    private final Map<String, ScalingFormula> stats = new LinkedHashMap<>();
    private final Map<String, ClassSkill> skills = new LinkedHashMap<>();
    private final List<PassiveSkill> classScripts = new ArrayList<>();
    private final Map<String, List<Trigger>> eventTriggers = new LinkedHashMap<>();
    private final List<Subclass> subclasses = new ArrayList<>();
    private final Map<String, Integer> unresolvedSubclasses = new LinkedHashMap<>();
    private final Map<PlayerResource, ResourceRegeneration> resourceHandlers = new EnumMap<>(PlayerResource.class);
    private final List<SkillSlotDefinition> skillSlots;
    private final ParticleInformation castParticle;
    private final Map<String, Object> keyCombos;
    private final ComboMap comboMap;
    private final Map<String, Object> raw;

    public PlayerClass(String id, Map<String, Object> config,
                       Map<String, ScalingFormula> defaultStats,
                       ExperienceCurveRegistry curves,
                       SkillManager skillManager,
                       boolean passiveSkillsNeedBinding) {
        this.id = UtilityMethods.enumName(Objects.requireNonNull(id, "id"));
        this.raw = Map.copyOf(Objects.requireNonNull(config, "config"));
        this.defaultStats = Map.copyOf(defaultStats);

        Map<String, Object> display = map(config.get("display"));
        this.name = SVFrameLib.inst().parseColors(String.valueOf(display.getOrDefault("name", "INVALID DISPLAY NAME")));
        this.icon = IconOptions.from(display.getOrDefault("item", "GRASS_BLOCK"));
        this.description = coloredList(display.get("lore"));
        this.attributeDescription = coloredList(display.get("attribute-lore"));
        this.maxLevel = integer(config.get("max-level"), 0);
        this.displayOrder = integer(display.get("order"), 0);
        this.actionBarFormat = config.containsKey("action-bar") ? String.valueOf(config.get("action-bar")) : null;
        this.expCurve = curves.fromConfig(config.get("exp-curve") == null ? null : String.valueOf(config.get("exp-curve")));
        this.experienceTableId = config.get("exp-table") == null ? null : String.valueOf(config.get("exp-table"));
        this.skillTreeIds = stringList(config.get("skill-trees"));
        this.mainExperienceSources = stringList(config.get("main-exp-sources"));

        Map<String, Object> configuredScripts = map(config.get("scripts"));
        for (Map.Entry<String, Object> entry : configuredScripts.entrySet()) {
            try {
                TriggerType trigger = TriggerType.valueOf(UtilityMethods.enumName(entry.getKey()));
                Script script = skillManager.loadScript(this.id + "_CLASS_" + UtilityMethods.enumName(entry.getKey()), entry.getValue());
                SimpleSkill castSkill = new SimpleSkill(new SVFrameLibSkillHandler(script));
                classScripts.add(new PassiveSkill("svframemmo_class_script_" + this.id.toLowerCase(Locale.ROOT) + "_" + trigger.getLowerCaseId(),
                        trigger, castSkill, EquipmentSlot.OTHER, ModifierSource.OTHER));
            } catch (RuntimeException exception) {
                LOG.log(Level.WARNING, "Could not load class script '" + entry.getKey() + "' from class '" + this.id + "': " + exception.getMessage());
            }
        }

        this.keyCombos = Map.copyOf(map(config.get("key-combos")));
        ComboMap parsedCombos = null;
        if (!keyCombos.isEmpty()) {
            try { parsedCombos = new ComboMap(keyCombos); }
            catch (RuntimeException exception) {
                LOG.log(Level.WARNING, "Could not load combo map from class '" + this.id + "': " + exception.getMessage());
            }
        }
        this.comboMap = parsedCombos;

        Map<String, Object> configuredTriggers = map(config.get("triggers"));
        for (Map.Entry<String, Object> entry : configuredTriggers.entrySet()) {
            String triggerId = eventKey(entry.getKey());
            try {
                List<Trigger> parsed = NativeTriggerRegistry.parseAll(entry.getValue());
                if (!parsed.isEmpty()) eventTriggers.put(triggerId, parsed);
            } catch (RuntimeException exception) {
                LOG.log(Level.WARNING, "Could not load event trigger '" + triggerId + "' from class '" + this.id + "': " + exception.getMessage());
            }
        }

        ParticleInformation particle = null;
        if (config.containsKey("cast-particle")) {
            try { particle = ParticleInformation.fromConfig(config.get("cast-particle")); }
            catch (RuntimeException exception) {
                LOG.log(Level.WARNING, "Could not load cast particle from class '" + this.id + "': " + exception.getMessage());
            }
        }
        this.castParticle = particle;

        Map<String, Object> configuredStats = map(config.get("attributes"));
        for (Map.Entry<String, Object> entry : configuredStats.entrySet())
            stats.put(UtilityMethods.enumName(entry.getKey()), ScalingFormula.fromConfig(entry.getValue()));

        Map<String, Object> configuredSkills = normalizedMap(config.get("skills"));
        for (SkillHandler<?> handler : skillManager.getHandlers()) {
            Object rawSkill = configuredSkills.get(handler.getId());
            ClassSkill classSkill = rawSkill instanceof Map<?, ?> skillMap
                    ? new ClassSkill(handler, stringMap(skillMap), passiveSkillsNeedBinding)
                    : new ClassSkill(handler, 1, 1, false, passiveSkillsNeedBinding);
            skills.put(handler.getId(), classSkill);
        }

        Map<String, Object> configuredOptions = map(config.get("options"));
        for (Map.Entry<String, Object> entry : configuredOptions.entrySet()) {
            try { options.put(ClassOption.fromPath(entry.getKey()), bool(entry.getValue(), false)); }
            catch (IllegalArgumentException ignored) { }
        }

        Map<String, Object> resource = map(config.get("resource"));
        for (PlayerResource playerResource : PlayerResource.values()) {
            Object configured = findIgnoreCase(resource, playerResource.name());
            if (configured instanceof Map<?, ?> section) {
                try { resourceHandlers.put(playerResource, new ResourceRegeneration(playerResource, stringMap(section))); }
                catch (IllegalArgumentException exception) { resourceHandlers.put(playerResource, new ResourceRegeneration(playerResource)); }
            } else resourceHandlers.put(playerResource, new ResourceRegeneration(playerResource));
        }

        Map<String, Object> subclasses = map(config.get("subclasses"));
        for (Map.Entry<String, Object> entry : subclasses.entrySet())
            unresolvedSubclasses.put(UtilityMethods.enumName(entry.getKey()), integer(entry.getValue(), 0));

        this.skillSlots = parseSkillSlots(config.get("skill-slots"));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getKey() { return "class_" + id; }
    public int getMaxLevel() { return maxLevel; }
    public boolean hasMaxLevel() { return maxLevel > 0; }
    public int getDisplayOrder() { return displayOrder; }
    public ExperienceCurve getExpCurve() { return expCurve; }
    public String getExperienceTableId() { return experienceTableId; }
    public boolean hasExperienceTable() { return experienceTableId != null && !experienceTableId.isBlank(); }
    public IconOptions getRawIcon() { return icon; }
    public List<String> getDescription() { return description; }
    public List<String> getAttributeDescription() { return attributeDescription; }
    public List<String> getSkillTreeIds() { return skillTreeIds; }
    public List<String> getMainExperienceSources() { return mainExperienceSources; }
    public String getActionBar() { return actionBarFormat; }
    public boolean hasActionBar() { return actionBarFormat != null; }
    public Map<String, Object> getRawConfig() { return raw; }
    public ParticleInformation getCastParticle() { return castParticle; }
    public List<PassiveSkill> getScripts() { return List.copyOf(classScripts); }
    public Map<String, Object> getKeyCombos() { return keyCombos; }
    public boolean hasKeyCombos() { return comboMap != null && !comboMap.isEmpty(); }
    public ComboMap getComboMap() { return comboMap; }
    public Set<String> getEventTriggers() { return Set.copyOf(eventTriggers.keySet()); }
    public boolean hasEventTriggers(String name) { return eventTriggers.containsKey(eventKey(name)); }
    public List<Trigger> getEventTriggers(String name) { return eventTriggers.getOrDefault(eventKey(name), List.of()); }
    public void fireEventTriggers(String name, PlayerData player) {
        for (Trigger trigger : getEventTriggers(name)) trigger.schedule(player);
    }

    public void setOption(ClassOption option, boolean value) { options.put(option, value); }
    public boolean hasOption(ClassOption option) { return options.getOrDefault(option, option.getDefault()); }

    public ResourceRegeneration getHandler(PlayerResource resource) {
        return Objects.requireNonNull(resourceHandlers.get(resource), "Missing resource handler for " + resource);
    }

    public Collection<ClassSkill> getSkills() { return List.copyOf(skills.values()); }
    public ClassSkill getSkill(SkillHandler<?> skill) { return skill == null ? null : getSkill(skill.getId()); }
    public ClassSkill getSkill(String skillId) { return skillId == null ? null : skills.get(UtilityMethods.enumName(skillId)); }
    public Set<String> getStats() { return Set.copyOf(stats.keySet()); }
    public Set<String> getEffectiveStats() {
        LinkedHashSet<String> result = new LinkedHashSet<>(defaultStats.keySet());
        result.addAll(stats.keySet());
        return Set.copyOf(result);
    }

    public double calculateBaseStat(String stat, int level, PlayerData player) {
        String normalized = UtilityMethods.enumName(stat);
        ScalingFormula formula = stats.getOrDefault(normalized, defaultStats.getOrDefault(normalized, ScalingFormula.ZERO));
        try { return formula.evaluate(level, player == null ? null : player.getPlayer()); }
        catch (FormulaFailsafeException exception) {
            exception.log("Could not evaluate base stat %s for class %s", normalized, id);
            return exception.getFailsafe();
        }
    }

    public List<Subclass> getSubclasses() { return List.copyOf(subclasses); }
    public void resolveSubclasses(Function<String, PlayerClass> resolver) {
        subclasses.clear();
        unresolvedSubclasses.forEach((classId, level) -> {
            PlayerClass target = resolver.apply(classId);
            if (target != null && target != this) subclasses.add(new Subclass(target, level));
        });
    }

    public boolean hasSubclass(PlayerClass profess) {
        return hasSubclass(profess, new LinkedHashSet<>());
    }

    private boolean hasSubclass(PlayerClass profess, Set<PlayerClass> visited) {
        if (profess == null || !visited.add(this)) return false;
        for (Subclass subclass : subclasses)
            if (subclass.getProfess().equals(profess) || subclass.getProfess().hasSubclass(profess, visited)) return true;
        return false;
    }

    public boolean hasSlot(int slot) { return slot >= 1 && slot <= skillSlots.size(); }
    public SkillSlotDefinition getSkillSlot(int slot) { return hasSlot(slot) ? skillSlots.get(slot - 1) : null; }
    public List<SkillSlotDefinition> getSlots() { return skillSlots; }

    @Override public boolean equals(Object object) { return object instanceof PlayerClass other && id.equals(other.id); }
    @Override public int hashCode() { return id.hashCode(); }

    private static List<SkillSlotDefinition> parseSkillSlots(Object value) {
        Map<String, Object> section = map(value);
        ArrayList<SkillSlotDefinition> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : section.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> rawSlot)) continue;
            int slot = integer(entry.getKey(), result.size() + 1);
            Map<String, Object> config = stringMap(rawSlot);
            result.add(new SkillSlotDefinition(slot,
                    String.valueOf(config.getOrDefault("name", "Skill Slot " + slot)),
                    String.valueOf(config.getOrDefault("formula", "true")),
                    stringList(config.get("lore")),
                    bool(config.get("unlocked-by-default"), true),
                    bool(config.get("can-manually-bind"), true),
                    config.get("hardset") == null ? null : UtilityMethods.enumName(String.valueOf(config.get("hardset"))),
                    stringList(config.get("skill-buffs"))));
        }
        result.sort(Comparator.comparingInt(SkillSlotDefinition::slot));
        return List.copyOf(result);
    }

    private static String eventKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private static Object findIgnoreCase(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private static List<String> coloredList(Object value) {
        List<String> lines = stringList(value);
        ArrayList<String> result = new ArrayList<>(lines.size());
        for (String line : lines) result.add("\u00a77" + SVFrameLib.inst().parseColors(line));
        return List.copyOf(result);
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Collection<?> collection) {
            ArrayList<String> result = new ArrayList<>(collection.size());
            for (Object element : collection) result.add(String.valueOf(element));
            return List.copyOf(result);
        }
        if (value == null) return List.of();
        return List.of(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? stringMap(raw) : Map.of();
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, Object> normalizedMap(Object value) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map(value).forEach((key, raw) -> result.put(UtilityMethods.enumName(key), raw));
        return result;
    }

    private static int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
