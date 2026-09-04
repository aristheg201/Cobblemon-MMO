package vn.svframe.svframemmo.api.player;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.fabric.runtime.RpgProfileRegistry;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerAttributeUseEvent;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.api.event.PlayerExperienceGainEvent;
import vn.svframe.svframemmo.api.event.PlayerLevelChangeEvent;
import vn.svframe.svframemmo.api.event.PlayerResourceUpdateEvent;
import vn.svframe.svframemmo.api.player.attribute.PlayerAttributes;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;
import vn.svframe.svframemmo.api.player.profess.SavedClassState;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.experience.EXPSource;
import vn.svframe.svframemmo.experience.PlayerProfessions;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Native persistent player state and progression runtime for SVFrameMMO. */
public final class PlayerData {
    private static final String CLASS_STAT_KEY = "svframemmo_class";

    private final UUID id;
    private transient ServerPlayerEntity player;
    private String playerClass = "HUMAN";
    private int level, classPoints, skillPoints, attributePoints;
    private int attributeReallocationPoints, skillReallocationPoints, skillTreeReallocationPoints;
    private double experience, health, mana, stamina, stellium;
    private long combatUntilTick;
    private final PlayerAttributes attributes = new PlayerAttributes(this);
    private final PlayerProfessions professions = new PlayerProfessions(this);
    private final PlayerSkillTrees skillTrees = new PlayerSkillTrees(this);
    private final Map<String, Integer> skillLevels = new LinkedHashMap<>();
    private final Map<Integer, String> skillBindings = new LinkedHashMap<>();
    private final Set<String> unlockedItems = new LinkedHashSet<>();
    private final Map<String, Integer> claimCounts = new LinkedHashMap<>();
    private final Map<String, SavedClassState> classSlots = new LinkedHashMap<>();

    PlayerData(UUID id) {
        this.id = id;
        var config = SVFrameMMO.config();
        level = config.defaultLevel();
        classPoints = config.defaultClassPoints();
        skillPoints = config.defaultSkillPoints();
        attributePoints = config.defaultAttributePoints();
        attributeReallocationPoints = config.defaultReallocationPoints();
        skillReallocationPoints = config.defaultReallocationPoints();
        skillTreeReallocationPoints = config.defaultReallocationPoints();
        // Zero is an internal "fill from effective MAX_HEALTH on first attach" sentinel.
        health = 0d;
        mana = config.defaultMana();
        stamina = config.defaultStamina();
        stellium = config.defaultStellium();
    }

    public UUID getUniqueId() { return id; }
    public ServerPlayerEntity getPlayer() { return player; }
    public boolean isOnline() { return player != null; }
    public MMOPlayerData getMMOPlayerData() { return player == null ? MMOPlayerData.get(id) : MMOPlayerData.setup(player); }

    public void attach(ServerPlayerEntity player) {
        double requestedHealth = health;
        this.player = Objects.requireNonNull(player, "player");
        MMOPlayerData.setup(player);
        refreshClassStats();
        attributes.reload();
        applyHardBindings();
        skillTrees.resolveClassTrees();
        applyTemporaryProgression();
        skillTrees.applyTemporary();
        clampAll();
        setResource(PlayerResource.HEALTH,
                requestedHealth <= 0d ? getMaxResource(PlayerResource.HEALTH) : requestedHealth,
                ResourceUpdateReason.CHOOSE_CLASS);
        SVFrameMMO.skillRuntime().attach(this);
    }

    public void detach() {
        if (player != null) health = player.getHealth();
        SVFrameMMO.skillRuntime().detach(this);
        player = null;
    }

    public void prepareReload() {
        if (isOnline()) SVFrameMMO.skillRuntime().detach(this);
        removeTemporaryProgression(getProfess());
    }

    public void reloadDefinitions() {
        if (SVFrameMMO.classes().get(playerClass) == null) playerClass = SVFrameMMO.classes().getDefaultClass().getId();
        skillLevels.entrySet().removeIf(entry -> getProfess().getSkill(entry.getKey()) == null);
        skillBindings.entrySet().removeIf(entry -> getProfess().getSkillSlot(entry.getKey()) == null || getProfess().getSkill(entry.getValue()) == null);
        applyHardBindings();
        attributes.reload();
        skillTrees.restore(skillTrees.pointMap(), skillTrees.nodeLevelMap());
        refreshClassStats();
        applyTemporaryProgression();
        skillTrees.applyTemporary();
        clampAll();
        if (isOnline()) SVFrameMMO.skillRuntime().attach(this);
    }

    public String getClassId() { return playerClass; }
    public PlayerClass getProfess() {
        PlayerClass found = SVFrameMMO.classes().get(playerClass);
        return found == null ? SVFrameMMO.classes().getDefaultClass() : found;
    }

    public boolean changeClass(PlayerClass target, PlayerClassChangeEvent.Reason reason) {
        if (target == null) target = SVFrameMMO.classes().getDefaultClass();
        PlayerClass oldClass = getProfess();
        if (oldClass.equals(target)) return false;
        PlayerClassChangeEvent event = new PlayerClassChangeEvent(this, oldClass, target, reason).call();
        if (event.isCancelled()) return false;

        SavedClassState oldState = captureClassState();
        if (SVFrameMMO.config().saveDefaultClassInfo() || !oldClass.equals(SVFrameMMO.classes().getDefaultClass()))
            classSlots.put(oldClass.getId(), oldState);
        removeTemporaryProgression(oldClass);
        SVFrameMMO.skillRuntime().detach(this);

        SavedClassState targetState = classSlots.getOrDefault(target.getId(), defaultClassState());
        var cfg = SVFrameMMO.config();
        if (cfg.shareClassExperience())
            targetState = copyState(targetState, oldState.level(), oldState.experience(), targetState.skillPoints(), targetState.attributePoints(),
                    targetState.attributeReallocationPoints(), targetState.skillReallocationPoints());
        if (cfg.shareSkillPoints()) {
            int total = oldState.skillPoints() + oldState.spentSkillPoints();
            targetState = copyState(targetState, targetState.level(), targetState.experience(), Math.max(0, total - targetState.spentSkillPoints()),
                    targetState.attributePoints(), targetState.attributeReallocationPoints(), targetState.skillReallocationPoints());
        }
        if (cfg.shareAttributePoints()) {
            int total = oldState.attributePoints() + oldState.spentAttributePoints();
            targetState = copyState(targetState, targetState.level(), targetState.experience(), targetState.skillPoints(),
                    Math.max(0, total - targetState.spentAttributePoints()), targetState.attributeReallocationPoints(), targetState.skillReallocationPoints());
        }
        if (cfg.shareAttributeReallocationPoints())
            targetState = copyState(targetState, targetState.level(), targetState.experience(), targetState.skillPoints(), targetState.attributePoints(),
                    oldState.attributeReallocationPoints(), targetState.skillReallocationPoints());
        if (cfg.shareSkillReallocationPoints())
            targetState = copyState(targetState, targetState.level(), targetState.experience(), targetState.skillPoints(), targetState.attributePoints(),
                    targetState.attributeReallocationPoints(), oldState.skillReallocationPoints());

        playerClass = target.getId();
        restoreClassState(targetState);
        double requestedHealth = health;
        refreshClassStats();
        attributes.reload();
        applyHardBindings();
        skillTrees.resolveClassTrees();
        applyTemporaryProgression();
        skillTrees.applyTemporary();
        clampAll();
        if (isOnline()) {
            setResource(PlayerResource.HEALTH,
                    requestedHealth <= 0d ? getMaxResource(PlayerResource.HEALTH) : requestedHealth,
                    ResourceUpdateReason.CHOOSE_CLASS);
            SVFrameMMO.skillRuntime().attach(this);
        }
        return true;
    }

    public void setClassId(String value) {
        PlayerClass target = value == null ? SVFrameMMO.classes().getDefaultClass() : SVFrameMMO.classes().getOrThrow(value);
        changeClass(target, PlayerClassChangeEvent.Reason.UNKNOWN);
    }

    public int getLevel() { return Math.max(1, level); }
    public void setLevel(int value) { setLevel(value, PlayerLevelChangeEvent.Reason.UNKNOWN); }
    public void setLevel(int value, PlayerLevelChangeEvent.Reason reason) {
        int old = getLevel();
        int next = Math.max(1, value);
        if (getProfess().hasMaxLevel()) next = Math.min(getProfess().getMaxLevel(), next);
        if (next == old) return;
        level = next;
        new PlayerLevelChangeEvent(this, null, old, next, reason).call();
        refreshClassStats();
        clampAll();
        if (isOnline()) SVFrameMMO.skillRuntime().refresh(this);
    }

    public boolean hasReachedMaxLevel() { return getProfess().hasMaxLevel() && getLevel() >= getProfess().getMaxLevel(); }
    public long getLevelUpExperience() { return getProfess().getExpCurve().getExperience(this, getLevel()); }
    public double getExperience() { return experience; }
    public void setExperience(double value) { experience = Math.max(0d, value); }

    public void giveLevels(int amount, EXPSource source) {
        if (amount <= 0) { setLevel(getLevel() + amount, PlayerLevelChangeEvent.Reason.COMMAND); return; }
        long equivalent = 0;
        while (amount-- > 0) equivalent += getProfess().getExpCurve().getExperience(this, getLevel() + amount);
        giveExperience(equivalent, source);
    }

    public void giveExperience(double value, EXPSource source) {
        if (value <= 0d) { setExperience(experience + value); return; }


        if (hasReachedMaxLevel()) { setExperience(0d); return; }
        value *= (1d + getMMOPlayerData().getStatMap().getStat("ADDITIONAL_EXPERIENCE") / 100d) * SVFrameMMO.boosters().multiplier(null);
        PlayerExperienceGainEvent event = new PlayerExperienceGainEvent(this, null, value, source).call();
        if (event.isCancelled()) return;
        experience = Math.max(0d, experience + event.getExperience());

        int oldLevel = getLevel();
        int newLevel = oldLevel;
        int maxLevel = getProfess().getMaxLevel();
        while (true) {
            long needed = getProfess().getExpCurve().getExperience(this, newLevel);
            if (needed <= 0) throw new IllegalStateException("Class '" + getClassId() + "' experience curve returned " + needed + " at level " + newLevel);
            if (experience < needed) break;
            if (maxLevel > 0 && newLevel >= maxLevel) { experience = 0d; break; }
            experience -= needed;
            newLevel++;
            if (getProfess().hasExperienceTable())
                SVFrameMMO.experienceTables().claim(getProfess().getExperienceTableId(), getProfess().getKey(), this, newLevel);
        }
        if (newLevel > oldLevel) setLevel(newLevel, PlayerLevelChangeEvent.Reason.LEVEL_UP);
    }

    public int getClassPoints() { return classPoints; }
    public void setClassPoints(int value) { classPoints = Math.max(0, value); }
    public void giveClassPoints(int value) { setClassPoints(classPoints + value); }
    public int getSkillPoints() { return skillPoints; }
    public void setSkillPoints(int value) { skillPoints = Math.max(0, value); }
    public void giveSkillPoints(int value) { setSkillPoints(skillPoints + value); }
    public int getAttributePoints() { return attributePoints; }
    public void setAttributePoints(int value) { attributePoints = Math.max(0, value); }
    public void giveAttributePoints(int value) { setAttributePoints(attributePoints + value); }
    public int getAttributeReallocationPoints() { return attributeReallocationPoints; }
    public void setAttributeReallocationPoints(int value) { attributeReallocationPoints = Math.max(0, value); }
    public void giveAttributeReallocationPoints(int value) { setAttributeReallocationPoints(attributeReallocationPoints + value); }
    /** Generic trigger compatibility maps to attribute reallocation, the original primary reallocation pool. */
    public void giveReallocationPoints(int value) { giveAttributeReallocationPoints(value); }
    public int getSkillReallocationPoints() { return skillReallocationPoints; }
    public void setSkillReallocationPoints(int value) { skillReallocationPoints = Math.max(0, value); }
    public void giveSkillReallocationPoints(int value) { setSkillReallocationPoints(skillReallocationPoints + value); }
    public int getSkillTreeReallocationPoints() { return skillTreeReallocationPoints; }
    public void setSkillTreeReallocationPoints(int value) { skillTreeReallocationPoints = Math.max(0, value); }
    public void giveSkillTreeReallocationPoints(int value) { setSkillTreeReallocationPoints(skillTreeReallocationPoints + value); }

    public PlayerAttributes getAttributes() { return attributes; }
    public Map<String, Integer> mapAttributeLevels() { return attributes.mapPoints(); }
    public boolean spendAttributePoints(String attributeId, int requested) {
        if (requested <= 0) throw new IllegalArgumentException("requested must be positive");
        var attribute = SVFrameMMO.attributes().get(attributeId);
        if (attribute == null) throw new IllegalArgumentException("Unknown attribute '" + attributeId + "'");
        var instance = attributes.getInstance(attribute);
        int amount = Math.min(requested, attributePoints);
        if (attribute.hasMax()) amount = Math.min(amount, Math.max(0, attribute.getMax() - instance.getBase()));
        if (amount <= 0) return false;
        instance.addBase(amount);
        giveAttributePoints(-amount);
        if (attribute.hasExperienceTable()) {
            for (int i = 0; i < amount; i++)
                SVFrameMMO.experienceTables().claim(attribute.getExperienceTableId(), attribute.getKey(), this, instance.getBase() - amount + i + 1);
        }
        new PlayerAttributeUseEvent(this, attribute, amount).call();
        return true;
    }

    public boolean reallocateAttributes() {
        int spent = attributes.countPoints();
        if (spent < 1 || attributeReallocationPoints < 1) return false;
        for (var instance : attributes.getInstances()) instance.setBase(0);
        giveAttributePoints(spent);
        giveAttributeReallocationPoints(-1);
        return true;
    }

    public void resetAttributes(boolean giveSpentPointsBack) {
        int spent = attributes.countPoints();
        if (!giveSpentPointsBack) {
            for (var attribute : SVFrameMMO.attributes().getAll())
                if (attribute.hasExperienceTable())
                    SVFrameMMO.experienceTables().unclaim(attribute.getExperienceTableId(), attribute.getKey(), this, true);
        }
        for (var instance : attributes.getInstances()) instance.setBase(0);
        if (giveSpentPointsBack) giveAttributePoints(spent);
    }

    public PlayerProfessions getProfessions() { return professions; }
    public PlayerSkillTrees getSkillTrees() { return skillTrees; }
    public PlayerProfessions getCollectionSkills() { return professions; }

    public int getSkillLevel(SkillHandler<?> skill) { return getSkillLevel(skill.getId()); }
    public int getSkillLevel(String skill) { return skillLevels.getOrDefault(normalizeEnum(skill), 1); }
    public void setSkillLevel(SkillHandler<?> skill, int value) { setSkillLevel(skill.getId(), value); }
    public void setSkillLevel(String skill, int value) {
        String skillId = normalizeEnum(skill);
        ClassSkill definition = getProfess().getSkill(skillId);
        if (definition == null) throw new IllegalArgumentException("Skill '" + skillId + "' does not belong to class '" + getClassId() + "'");
        int next = Math.max(1, value);
        if (definition.hasMaxLevel()) next = Math.min(next, definition.getMaxLevel());
        if (next <= 1) skillLevels.remove(skillId); else skillLevels.put(skillId, next);
        if (isOnline()) SVFrameMMO.skillRuntime().refresh(this);
    }
    public void resetSkills() { skillLevels.clear(); skillBindings.clear(); applyHardBindings(); if (isOnline()) SVFrameMMO.skillRuntime().refresh(this); }
    public Map<String, Integer> getSkillLevels() { return Collections.unmodifiableMap(skillLevels); }
    public boolean hasUnlockedLevel(ClassSkill skill) { return skill != null && getLevel() >= skill.getUnlockLevel(); }
    public boolean canUseSkill(ClassSkill skill) { return skill != null && hasUnlockedLevel(skill) && hasUnlocked(skill.getUnlockNamespacedKey()); }

    public boolean upgradeSkill(String skillId) {
        ClassSkill skill = requireClassSkill(skillId);
        if (!canUseSkill(skill) || !skill.isUpgradable()) return false;
        int current = getSkillLevel(skill.getSkill());
        if (skill.hasMaxLevel() && current >= skill.getMaxLevel()) return false;
        if (skillPoints <= 0) return false;
        setSkillLevel(skill.getSkill(), current + 1);
        setSkillPoints(skillPoints - 1);
        return true;
    }

    public boolean downgradeSkill(String skillId) {
        ClassSkill skill = requireClassSkill(skillId);
        int current = getSkillLevel(skill.getSkill());
        if (current <= 1) return false;
        setSkillLevel(skill.getSkill(), current - 1);
        giveSkillPoints(1);
        return true;
    }

    public void bindSkill(int slot, String skillId) {
        if (slot <= 0) throw new IllegalArgumentException("Skill slot must be positive");
        var slotDefinition = getProfess().getSkillSlot(slot);
        if (slotDefinition == null) throw new IllegalArgumentException("Class '" + getClassId() + "' has no skill slot " + slot);
        ClassSkill skill = requireClassSkill(skillId);
        if (skill.isPermanent()) throw new IllegalArgumentException("Permanent skill cannot be bound: " + skill.getSkill().getId());
        if (!canUseSkill(skill)) throw new IllegalStateException("Skill is locked: " + skill.getSkill().getId());
        if (!hasUnlocked("slot:" + slot)) throw new IllegalStateException("Skill slot is locked: " + slot);
        if (!slotDefinition.canManuallyBind() && slotDefinition.hardset() == null) throw new IllegalStateException("Skill slot cannot be manually bound: " + slot);
        if (slotDefinition.hardset() != null && !slotDefinition.hardset().equals(skill.getSkill().getId()))
            throw new IllegalStateException("Skill slot " + slot + " is hard-bound to " + slotDefinition.hardset());
        skillBindings.put(slot, skill.getSkill().getId());
        if (isOnline()) SVFrameMMO.skillRuntime().refresh(this);
    }
    public void bindSkill(int slot, ClassSkill skill) { bindSkill(slot, skill.getSkill().getId()); }
    public String unbindSkill(int slot) { String removed = skillBindings.remove(slot); if (removed != null && isOnline()) SVFrameMMO.skillRuntime().refresh(this); return removed; }
    public boolean hasSkillBound(int slot) { return skillBindings.containsKey(slot); }
    public ClassSkill getBoundSkill(int slot) {
        String id = skillBindings.get(slot);
        return id == null ? null : getProfess().getSkill(id);
    }
    public Map<Integer, String> getSkillBindings() { return Collections.unmodifiableMap(skillBindings); }

    public boolean hasUnlocked(String key) {
        if (key == null) return false;
        String normalized = normalizeUnlockKey(key);
        if (normalized.startsWith("skill:")) {
            ClassSkill skill = getProfess().getSkill(normalized.substring(6));
            if (skill != null && skill.isUnlockedByDefault()) return true;
        } else if (normalized.startsWith("slot:")) {
            try {
                var slot = getProfess().getSkillSlot(Integer.parseInt(normalized.substring(5)));
                if (slot != null && slot.unlockedByDefault()) return true;
            } catch (NumberFormatException ignored) { }
        }
        return unlockedItems.contains(normalized);
    }
    public boolean unlock(String key) { boolean changed = unlockedItems.add(normalizeUnlockKey(key)); if (changed && isOnline()) SVFrameMMO.skillRuntime().refresh(this); return changed; }
    public boolean lock(String key) {
        String normalized = normalizeUnlockKey(key);
        if (normalized.startsWith("slot:")) {
            try { unbindSkill(Integer.parseInt(normalized.substring(5))); } catch (NumberFormatException ignored) { }
        }
        boolean changed = unlockedItems.remove(normalized);
        if (changed && isOnline()) SVFrameMMO.skillRuntime().refresh(this);
        return changed;
    }
    public Set<String> getUnlockedItems() { return Set.copyOf(unlockedItems); }

    public int getClaimCount(String key) { return claimCounts.getOrDefault(key, 0); }
    public void setClaimCount(String key, int value) { if (value <= 0) claimCounts.remove(key); else claimCounts.put(key, value); }
    public Map<String, Integer> getClaimCounts() { return Map.copyOf(claimCounts); }

    public boolean isInCombat() { return SVFrameMMO.currentTick() < combatUntilTick; }
    public void markCombat() { combatUntilTick = SVFrameMMO.currentTick() + SVFrameMMO.config().combatTimerSeconds() * 20L; }

    public double getHealth() { return player == null ? health : player.getHealth(); }
    public double getMana() { return mana; }
    public double getStamina() { return stamina; }
    public double getStellium() { return stellium; }
    public boolean setMana(double amount, ResourceUpdateReason reason) { return setResource(PlayerResource.MANA, amount, reason); }
    public boolean giveMana(double amount, ResourceUpdateReason reason) { return setMana(mana + amount, reason); }
    public boolean setStamina(double amount, ResourceUpdateReason reason) { return setResource(PlayerResource.STAMINA, amount, reason); }
    public boolean giveStamina(double amount, ResourceUpdateReason reason) { return setStamina(stamina + amount, reason); }
    public boolean setStellium(double amount, ResourceUpdateReason reason) { return setResource(PlayerResource.STELLIUM, amount, reason); }
    public boolean giveStellium(double amount, ResourceUpdateReason reason) { return setStellium(stellium + amount, reason); }

    public double getResource(PlayerResource resource) {
        return switch (resource) {
            case HEALTH -> getHealth();
            case MANA -> mana;
            case STAMINA -> stamina;
            case STELLIUM -> stellium;
        };
    }

    public double getMaxResource(PlayerResource resource) {
        if (resource == PlayerResource.HEALTH) return player == null ? Math.max(SVFrameMMO.config().defaultHealth(), health) : player.getMaxHealth();
        return Math.max(0, getMMOPlayerData().getStatMap().getStat(resource.getMaxStat()));
    }

    public boolean setResource(PlayerResource resource, double amount, ResourceUpdateReason reason) {
        double max = getMaxResource(resource);
        double old = getResource(resource);
        double next = Math.max(0, Math.min(amount, max));
        if (Double.compare(old, next) == 0) return false;
        if (reason != ResourceUpdateReason.CHOOSE_CLASS) {
            var event = new PlayerResourceUpdateEvent(this, resource, old, next, reason).call();
            if (event.isCancelled()) return false;
            next = Math.max(0, Math.min(event.getNewAmount(), max));
        }
        switch (resource) {
            case HEALTH -> { health = next; if (player != null) player.setHealth((float) next); }
            case MANA -> mana = next;
            case STAMINA -> stamina = next;
            case STELLIUM -> stellium = next;
        }
        return true;
    }

    public void refreshClassStats() {
        if (player == null) return;
        MMOPlayerData mmo = getMMOPlayerData();
        var statMap = mmo.getStatMap();
        PlayerClass profess = getProfess();
        statMap.bufferUpdates(() -> {
            for (var instance : statMap.getInstances()) instance.remove(CLASS_STAT_KEY);
            for (String stat : profess.getEffectiveStats()) {
                var instance = statMap.getInstance(stat);
                double value = profess.calculateBaseStat(stat, getLevel(), this) - instance.getDefaultBase();
                if (value == 0d) continue;
                UUID modifierId = UUID.nameUUIDFromBytes((id + ":class:" + stat).getBytes(StandardCharsets.UTF_8));
                instance.registerModifier(new StatModifier(modifierId, CLASS_STAT_KEY, stat, value,
                        ModifierType.FLAT, EquipmentSlot.OTHER, ModifierSource.OTHER));
            }
        });
    }

    public void clampAll() {
        for (PlayerResource resource : PlayerResource.values())
            setResource(resource, getResource(resource), ResourceUpdateReason.CLAMPING);
    }

    public RpgProfileRegistry.Snapshot snapshot() {
        Map<String, Double> mapped = new LinkedHashMap<>();
        attributes.mapPoints().forEach((key, value) -> mapped.put(key, value.doubleValue()));
        return new RpgProfileRegistry.Snapshot(getLevel(), playerClass, mapped);
    }

    public static PlayerData blank(UUID id) { return new PlayerData(id); }

    public void restore(String clazz, int level, double exp, int cp, int sp, int ap,
                        int arp, int srp, int strp,
                        double health, double mana, double stamina, double stellium,
                        Map<String, ? extends Number> attrs,
                        Map<String, ? extends Number> skills,
                        Map<Integer, String> bindings,
                        Set<String> unlocked,
                        Map<String, ? extends Number> claims,
                        Map<String, ? extends Number> professionLevels,
                        Map<String, ? extends Number> professionExperience,
                        Map<String, ? extends Number> skillTreePoints,
                        Map<String, ? extends Number> skillTreeNodeLevels,
                        Map<String, SavedClassState> restoredClassSlots) {
        playerClass = clazz == null ? SVFrameMMO.classes().getDefaultClass().getId() : normalizeEnum(clazz);
        this.level = Math.max(1, level);
        experience = Math.max(0, exp);
        classPoints = Math.max(0, cp);
        skillPoints = Math.max(0, sp);
        attributePoints = Math.max(0, ap);
        attributeReallocationPoints = Math.max(0, arp);
        skillReallocationPoints = Math.max(0, srp);
        skillTreeReallocationPoints = Math.max(0, strp);
        this.health = Math.max(0d, health); this.mana = mana; this.stamina = stamina; this.stellium = stellium;
        attributes.load(attrs);
        skillLevels.clear();
        if (skills != null) skills.forEach((key, value) -> {
            if (key != null && value != null && getProfess().getSkill(key) != null) {
                int effective = Math.max(1, value.intValue());
                ClassSkill definition = getProfess().getSkill(key);
                if (definition.hasMaxLevel()) effective = Math.min(effective, definition.getMaxLevel());
                if (effective > 1) skillLevels.put(normalizeEnum(key), effective);
            }
        });
        skillBindings.clear();
        if (bindings != null) bindings.forEach((slot, skill) -> {
            if (slot != null && skill != null && slot > 0 && getProfess().getSkillSlot(slot) != null && getProfess().getSkill(skill) != null)
                skillBindings.put(slot, normalizeEnum(skill));
        });
        unlockedItems.clear(); if (unlocked != null) unlocked.forEach(key -> unlockedItems.add(normalizeUnlockKey(key)));
        claimCounts.clear(); if (claims != null) claims.forEach((key, value) -> { if (key != null && value != null && value.intValue() > 0) claimCounts.put(key, value.intValue()); });
        professions.restore(professionLevels, professionExperience);
        skillTrees.restore(skillTreePoints, skillTreeNodeLevels);
        classSlots.clear();
        if (restoredClassSlots != null) restoredClassSlots.forEach((key, value) -> {
            if (key != null && value != null && SVFrameMMO.classes().get(key) != null) classSlots.put(normalizeEnum(key), value);
        });
        applyHardBindings();
    }

    public Map<String, SavedClassState> getClassSlots() { return Map.copyOf(classSlots); }

    public SavedClassState captureClassState() {
        Map<String, Integer> progressionClaims = new LinkedHashMap<>();
        claimCounts.forEach((key, value) -> {
            if (isClassScopedClaim(key)) progressionClaims.put(key, value);
        });
        return new SavedClassState(getLevel(), experience, skillPoints, attributePoints,
                attributeReallocationPoints, skillReallocationPoints, skillTreeReallocationPoints,
                getHealth(), mana, stamina, stellium, attributes.mapPoints(), skillLevels, skillBindings,
                unlockedItems, skillTrees.pointMap(), skillTrees.nodeLevelMap(), progressionClaims);
    }

    private SavedClassState defaultClassState() {
        var cfg = SVFrameMMO.config();
        return new SavedClassState(cfg.defaultLevel(), 0d, cfg.defaultSkillPoints(), cfg.defaultAttributePoints(),
                cfg.defaultReallocationPoints(), cfg.defaultReallocationPoints(), cfg.defaultReallocationPoints(),
                0d, cfg.defaultMana(), cfg.defaultStamina(), cfg.defaultStellium(),
                Map.of(), Map.of(), Map.of(), Set.of(), Map.of(), Map.of(), Map.of());
    }

    private static SavedClassState copyState(SavedClassState base, int level, double experience, int skillPoints,
                                               int attributePoints, int attributeReallocationPoints, int skillReallocationPoints) {
        return new SavedClassState(level, experience, skillPoints, attributePoints, attributeReallocationPoints,
                skillReallocationPoints, base.skillTreeReallocationPoints(), base.health(), base.mana(), base.stamina(),
                base.stellium(), base.attributes(), base.skills(), base.bindings(), base.unlockedItems(),
                base.skillTreePoints(), base.skillTreeNodeLevels(), base.progressionClaims());
    }

    private void restoreClassState(SavedClassState state) {
        level = Math.max(1, state.level());
        if (getProfess().hasMaxLevel()) level = Math.min(level, getProfess().getMaxLevel());
        experience = state.experience();
        skillPoints = state.skillPoints();
        attributePoints = state.attributePoints();
        attributeReallocationPoints = state.attributeReallocationPoints();
        skillReallocationPoints = state.skillReallocationPoints();
        skillTreeReallocationPoints = state.skillTreeReallocationPoints();
        health = state.health(); mana = state.mana(); stamina = state.stamina(); stellium = state.stellium();
        attributes.load(state.attributes());
        skillLevels.clear();
        state.skills().forEach((key, value) -> {
            ClassSkill definition = getProfess().getSkill(key);
            if (definition == null) return;
            int effective = Math.max(1, value);
            if (definition.hasMaxLevel()) effective = Math.min(effective, definition.getMaxLevel());
            if (effective > 1) skillLevels.put(normalizeEnum(key), effective);
        });
        skillBindings.clear();
        state.bindings().forEach((slot, skill) -> {
            if (slot > 0 && getProfess().getSkillSlot(slot) != null && getProfess().getSkill(skill) != null)
                skillBindings.put(slot, normalizeEnum(skill));
        });
        unlockedItems.clear(); unlockedItems.addAll(state.unlockedItems());
        claimCounts.keySet().removeIf(PlayerData::isClassScopedClaim);
        claimCounts.putAll(state.progressionClaims());
        skillTrees.restore(state.skillTreePoints(), state.skillTreeNodeLevels());
    }

    private void removeTemporaryProgression(PlayerClass clazz) {
        if (clazz.hasExperienceTable()) SVFrameMMO.experienceTables().unclaim(clazz.getExperienceTableId(), clazz.getKey(), this, false);
        for (var attribute : SVFrameMMO.attributes().getAll())
            if (attribute.hasExperienceTable()) SVFrameMMO.experienceTables().unclaim(attribute.getExperienceTableId(), attribute.getKey(), this, false);
        for (String treeId : clazz.getSkillTreeIds()) {
            var tree = SVFrameMMO.skillTrees().get(treeId);
            if (tree == null) continue;
            for (var node : tree.getNodes()) SVFrameMMO.experienceTables().unclaim(node.getExperienceTable(), node.getKey(), this, false);
        }
    }

    private static boolean isClassScopedClaim(String key) {
        return key != null && (key.startsWith("class_") || key.startsWith("attribute:") || key.startsWith("node:"));
    }

    private void applyHardBindings() {
        for (var slot : getProfess().getSlots()) if (slot.hardset() != null && getProfess().getSkill(slot.hardset()) != null) {
            if (hasUnlocked("slot:" + slot.slot()) && canUseSkill(getProfess().getSkill(slot.hardset()))) skillBindings.put(slot.slot(), slot.hardset());
        }
    }

    private void applyTemporaryProgression() {
        if (getProfess().hasExperienceTable())
            SVFrameMMO.experienceTables().applyTemporary(getProfess().getExperienceTableId(), getProfess().getKey(), this);
        for (var profession : SVFrameMMO.professions().getAll()) if (profession.hasExperienceTable())
            SVFrameMMO.experienceTables().applyTemporary(profession.getExperienceTableId(), profession.getKey(), this);
    }

    private ClassSkill requireClassSkill(String id) {
        ClassSkill skill = getProfess().getSkill(id);
        if (skill == null) throw new IllegalArgumentException("Skill '" + id + "' does not belong to class '" + getClassId() + "'");
        return skill;
    }
    private static String normalizeEnum(String value) { return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
    private static String normalizeUnlockKey(String key) { return key.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-'); }
}
