package vn.svframe.svframelib.api.player;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.stat.StatInstance;
import vn.svframe.svframelib.api.stat.StatMap;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.message.actionbar.ActionBarHandler;
import vn.svframe.svframelib.player.PlayerMetadata;
import vn.svframe.svframelib.player.cooldown.CooldownMap;
import vn.svframe.svframelib.player.cooldown.CooldownType;
import vn.svframe.svframelib.player.particle.ParticleEffectMap;
import vn.svframe.svframelib.player.permission.PermissionMap;
import vn.svframe.svframelib.player.potion.PermanentPotionEffectMap;
import vn.svframe.svframelib.player.skill.PassiveSkill;
import vn.svframe.svframelib.player.skill.PassiveSkillMap;
import vn.svframe.svframelib.player.skillmod.SkillModifierMap;
import vn.svframe.svframelib.profile.ProfileSession;
import vn.svframe.svframelib.profile.SessionUpdateReason;
import vn.svframe.svframelib.rpg.provided.PlayerResourceData;
import vn.svframe.svframelib.script.variable.VariableList;
import vn.svframe.svframelib.script.variable.VariableScope;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.trigger.TriggerMetadata;
import vn.svframe.svframelib.skill.trigger.TriggerType;
import vn.svframe.svframelib.util.TemporaryHandler;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Native Fabric player-data implementation preserving SVFrameLib 1.7.1 session,
 * profile, modifier, passive-skill and cooldown semantics.
 */
public class MMOPlayerData {
    public final AtomicInteger damageParticleCount = new AtomicInteger(0);
    private final ActionBarHandler actionBar = new ActionBarHandler(this);
    private final List<TemporaryHandler> tempHandlers = new ArrayList<>();
    private final VariableList variableList = new VariableList(VariableScope.PLAYER);
    private final UUID entityId;
    private ServerPlayerEntity player;
    private String lastPlayerName;
    private final boolean lookup;
    private long lastLogActivity;
    private UUID officialId;

    private ProfileSession profileSession;
    private boolean nextSessionBuffered;
    private SessionUpdateReason nextSessionReasonBuffer;
    private UUID nextSessionProfileBuffer;
    private final Object sessionLock = new Object();
    private final Map<UUID, ProfileSession> savedProfileSessions = new HashMap<>();
    /** External plugin data cache used by SVFrameMMO/SVFrameItems and other MMO modules. */
    private final Map<String, Object> externalData = new HashMap<>();
    private long nextLeftClick;
    private final ProfileSession fallbackProfileSession = new ProfileSession(this, UUID.randomUUID());

    private static final Map<UUID, MMOPlayerData> PLAYER_DATA = new WeakHashMap<>();

    public MMOPlayerData(boolean lookup, UUID entityId) {
        this.lookup = lookup;
        this.entityId = Objects.requireNonNull(entityId, "UUID cannot be null");
        this.officialId = entityId;
    }

    public MMOPlayerData(ServerPlayerEntity player) {
        this(false, Objects.requireNonNull(player, "Player cannot be null").getUuid());
    }

    public MMOPlayerData(UUID uniqueId) {
        this(true, uniqueId);
    }

    public UUID getUniqueId() { return entityId; }
    public boolean isLookup() { return lookup; }
    public long getLastLogActivity() { return lastLogActivity; }
    public String getPlayerName() { return lastPlayerName == null ? entityId.toString() : lastPlayerName; }

    public boolean isTimedOut() {
        savedProfileSessions.values().removeIf(ProfileSession::isTimedOut);
        return !isOnline() && savedProfileSessions.isEmpty();
    }

    public boolean isOnline() { return player != null; }

    public ServerPlayerEntity getPlayer() {
        return Objects.requireNonNull(player, "Player is offline");
    }

    public void updatePlayer(ServerPlayerEntity player) {
        if (this.player == player) return;
        this.player = player;
        lastLogActivity = System.currentTimeMillis();
        if (player != null) lastPlayerName = player.getGameProfile().getName();
    }

    public UUID getOfficialId() { return officialId; }

    public void setOfficialId(UUID officialId) {
        if (SVFrameLib.plugin.getProfileMode() != vn.svframe.svframelib.comp.profile.ProfileMode.PROXY) {
            throw new IllegalArgumentException("Player official IDs can only change in proxy profile mode");
        }
        this.officialId = Objects.requireNonNull(officialId, "Official ID cannot be null");
    }

    public UUID getProfileId() { return getProfileSession().getProfileId(); }

    public boolean hasProfile() {
        ProfileSession session;
        synchronized (sessionLock) { session = profileSession; }
        return session != null && session.hasProfile();
    }

    public boolean isPlaying() {
        ProfileSession session;
        synchronized (sessionLock) { session = profileSession; }
        return session != null && session.isReady();
    }

    public void shutdownSession() {
        synchronized (sessionLock) {
            if (profileSession != null) {
                profileSession.shutdown();
                profileSession = null;
            }
        }
    }

    public void clearNextSessionBuffer() {
        synchronized (sessionLock) { nextSessionBuffered = false; }
    }

    public void applyNextSessionBuffer() {
        synchronized (sessionLock) {
            if (nextSessionBuffered) {
                nextSessionBuffered = false;
                chooseProfile(nextSessionProfileBuffer, nextSessionReasonBuffer);
            }
        }
    }

    public void saveCurrentProfileSession() {
        synchronized (sessionLock) {
            Objects.requireNonNull(profileSession, "No profile session to save");
            if (!profileSession.isDead()) throw new IllegalArgumentException("Current profile session is still alive");
            UUID key = profileSession.hasProfile() ? profileSession.getProfileId() : null;
            savedProfileSessions.put(key, profileSession);
            profileSession = null;
        }
    }

    public void chooseProfile(UUID profileId, SessionUpdateReason reason) {
        if (lookup) throw new IllegalArgumentException("Cannot choose a profile in lookup mode");
        synchronized (sessionLock) {
            if (profileSession != null) {
                nextSessionBuffered = true;
                nextSessionReasonBuffer = reason;
                nextSessionProfileBuffer = profileId;
                return;
            }

            ProfileSession previous = savedProfileSessions.remove(profileId);
            if (previous != null) {
                if (profileSession != null) throw new IllegalArgumentException("Previous profile session is not dead");
                ProfileSession next = new ProfileSession(this, previous);
                profileSession = next;
                next.initializeSession(reason);
            } else {
                if (profileSession != null) throw new IllegalArgumentException("Previous profile session is not dead");
                ProfileSession next = new ProfileSession(this, profileId);
                profileSession = next;
                next.initializeSession(reason);
            }
        }
    }

    public boolean hasProfileSession() {
        synchronized (sessionLock) { return profileSession != null; }
    }

    public ProfileSession getProfileSession() {
        synchronized (sessionLock) {
            return Objects.requireNonNull(profileSession, "No profile chosen");
        }
    }

    public void addTemporaryHandler(TemporaryHandler handler) {
        tempHandlers.add(Objects.requireNonNull(handler, "Handler cannot be null"));
    }

    public void removeTemporaryHandler(TemporaryHandler handler) {
        tempHandlers.remove(handler);
    }

    public void clearTemporaryHandlers() {
        for (TemporaryHandler handler : tempHandlers) handler.closeNow(true);
        tempHandlers.clear();
    }

    public void blockLeftClicks(long durationMillis) {
        nextLeftClick = System.currentTimeMillis() + durationMillis;
    }

    public boolean canLeftClick() {
        return System.currentTimeMillis() > nextLeftClick;
    }

    public void clearModifiers(String key) {
        for (StatInstance instance : getStatMap().getInstances()) instance.removeIf(key::equals);
        getSkillModifierMap().removeModifiers(key);
        getPermanentEffectMap().removeModifiers(key);
        getParticleEffectMap().removeModifiers(key);
        getPassiveSkillMap().removeModifiers(key);
        getPermissionMap().removeModifiers(key);
    }

    protected ProfileSession safePlayerSession() {
        ProfileSession session;
        synchronized (sessionLock) { session = profileSession; }
        return Objects.requireNonNullElse(session, fallbackProfileSession);
    }

    public StatMap getStatMap() { return safePlayerSession().getStatMap(); }
    public CooldownMap getCooldownMap() { return safePlayerSession().getCooldownMap(); }
    public PlayerResourceData getResources() { return safePlayerSession().getResources(); }
    public SkillModifierMap getSkillModifierMap() { return safePlayerSession().getSkillModifierMap(); }
    public PermanentPotionEffectMap getPermanentEffectMap() { return safePlayerSession().getPermanentEffectMap(); }
    public ParticleEffectMap getParticleEffectMap() { return safePlayerSession().getParticleEffectMap(); }
    public PassiveSkillMap getPassiveSkillMap() { return safePlayerSession().getPassiveSkillMap(); }
    public PermissionMap getPermissionMap() { return safePlayerSession().getPermissionMap(); }
    public VariableList getVariableList() { return variableList; }
    public ActionBarHandler getActionBar() { return actionBar; }

    public Collection<PassiveSkill> isolateSkills(TriggerMetadata metadata) {
        return metadata.getTriggerType().isActionHandSpecific()
                ? getPassiveSkillMap().isolateModifiers(metadata.getActionHand())
                : getPassiveSkillMap().getModifiers();
    }

    public void triggerSkills(TriggerType type) {
        triggerSkills(new TriggerMetadata(this, type));
    }

    public void triggerSkills(TriggerMetadata metadata) {
        triggerSkills(metadata, isolateSkills(metadata));
    }

    public void triggerSkills(TriggerMetadata metadata, Iterable<PassiveSkill> passives) {
        triggerSkills(metadata, passives, true);
    }

    public void triggerSkills(TriggerMetadata metadata, Iterable<PassiveSkill> passives, boolean checkFlags) {
        if (getPlayer().isSpectator()) return;
        for (PassiveSkill passive : passives) {
            SkillHandler<?> handler = passive.getTriggeredSkill().getHandler();
            if (handler.isTriggerable() && passive.getTrigger().equals(metadata.getTriggerType())) {
                passive.getTriggeredSkill().cast(metadata.toSkillMetadata(passive.getTriggeredSkill()));
            }
        }
    }

    public void triggerSkills(TriggerType type, SkillMetadata metadata) {
        if (getPlayer().isSpectator()) return;
        for (PassiveSkill passive : getPassiveSkillMap().getModifiers()) {
            SkillHandler<?> handler = passive.getTriggeredSkill().getHandler();
            if (handler.isTriggerable() && passive.getTrigger().equals(type)) {
                passive.getTriggeredSkill().cast(metadata);
            }
        }
    }

    public void tickPlaying() {
        getPermanentEffectMap().applyPermanentPotionEffects();
    }

    public void tickOnline() {
        if (hasProfileSession() && getProfileSession().isGhost()) {
            SVFrameLib.plugin.getLogger().severe("Ghost profile session for " + getPlayerName() + " (" +
                    getUniqueId() + "): " + getProfileSession());
        }
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof MMOPlayerData other && getUniqueId().equals(other.getUniqueId());
    }

    @Override
    public int hashCode() { return getUniqueId().hashCode(); }

    public static MMOPlayerData setup(ServerPlayerEntity player) {
        MMOPlayerData data = setup(player.getUuid());
        data.updatePlayer(player);
        return data;
    }

    public static MMOPlayerData setup(UUID uniqueId) {
        return PLAYER_DATA.computeIfAbsent(uniqueId, id -> new MMOPlayerData(false, id));
    }

    public static MMOPlayerData get(ServerPlayerEntity player) { return get(player.getUuid()); }

    public static MMOPlayerData get(UUID uniqueId) {
        return Objects.requireNonNull(PLAYER_DATA.get(uniqueId), "Player data not loaded");
    }

    public static MMOPlayerData online(ServerPlayerEntity player) {
        if (!player.networkHandler.isConnectionOpen()) return null;
        MMOPlayerData data = PLAYER_DATA.get(player.getUuid());
        return data != null && data.isOnline() ? data : null;
    }

    public static MMOPlayerData getOrNull(Entity entity) {
        return entity instanceof ServerPlayerEntity player ? getOrNull(player.getUuid()) : null;
    }

    public static MMOPlayerData getOrNull(UUID uniqueId) { return PLAYER_DATA.get(uniqueId); }
    public static boolean has(ServerPlayerEntity player) { return has(player.getUuid()); }
    public static boolean has(UUID uniqueId) { return PLAYER_DATA.containsKey(uniqueId); }
    public static Collection<MMOPlayerData> getLoaded() { return PLAYER_DATA.values(); }

    public static void forEachPlaying(Consumer<MMOPlayerData> consumer) {
        for (MMOPlayerData data : PLAYER_DATA.values()) if (data.isPlaying()) consumer.accept(data);
    }

    public static void forEach(Consumer<MMOPlayerData> consumer) {
        for (MMOPlayerData data : PLAYER_DATA.values()) consumer.accept(data);
    }

    public static void flushOfflinePlayerData() {
        PLAYER_DATA.values().removeIf(MMOPlayerData::isTimedOut);
    }

    @SuppressWarnings("unchecked")
    public <T> T getExternalData(String id, Class<T> type) {
        Objects.requireNonNull(id, "External data key cannot be null");
        Objects.requireNonNull(type, "External data type cannot be null");
        Object found = externalData.get(id);
        if (found == null) return null;
        if (!type.isInstance(found)) {
            throw new ClassCastException("External data '" + id + "' is " + found.getClass().getName() + ", not " + type.getName());
        }
        return (T) found;
    }

    public void setExternalData(String id, Object value) {
        externalData.put(Objects.requireNonNull(id, "External data key cannot be null"), value);
    }

    public boolean hasExternalData(String id) {
        return externalData.containsKey(Objects.requireNonNull(id, "External data key cannot be null"));
    }

    public static void forEachOnline(Consumer<MMOPlayerData> consumer) {
        for (MMOPlayerData data : PLAYER_DATA.values()) if (data.isOnline()) consumer.accept(data);
    }

    public boolean hasStartedPlaying() { return isPlaying(); }
    public boolean hasOfficialId() { return true; }
    public void setProfileId(UUID profileId) { throw new IllegalStateException("Cannot change profile ID"); }
    public boolean hasFullySynchronized() { return isPlaying(); }
    public long getLastLogin() { return getLastLogActivity(); }
    public static boolean isLoaded(UUID uniqueId) { return has(uniqueId); }

    public void triggerSkills(TriggerType type, Entity target) {
        if (type.isActionHandSpecific()) throw new IllegalArgumentException("You must provide an action hand");
        triggerSkills(new TriggerMetadata(this, type, target));
    }

    public void triggerSkills(TriggerType type, EquipmentSlot actionHand, Entity target) {
        Objects.requireNonNull(actionHand, "Action hand cannot be null");
        triggerSkills(new TriggerMetadata(this, type, actionHand, null, target, null, null, null));
    }

    public void triggerSkills(TriggerType type, PlayerMetadata metadata, AttackMetadata attack, Entity target) {
        triggerSkills(new TriggerMetadata(this, type, null, null, target, null, attack, metadata));
    }

    public void triggerSkills(TriggerType type, PlayerMetadata metadata, Entity target, AttackMetadata attack) {
        Iterable<PassiveSkill> passives = type.isActionHandSpecific()
                ? getPassiveSkillMap().isolateModifiers(metadata == null ? EquipmentSlot.MAIN_HAND : metadata.getActionHand())
                : getPassiveSkillMap().getModifiers();
        triggerSkills(new TriggerMetadata(this, type, null, null, target, null, attack, metadata), passives);
    }

    public void triggerSkills(TriggerType type, PlayerMetadata metadata, Entity target) {
        Iterable<PassiveSkill> passives = type.isActionHandSpecific()
                ? getPassiveSkillMap().isolateModifiers(metadata == null ? EquipmentSlot.MAIN_HAND : metadata.getActionHand())
                : getPassiveSkillMap().getModifiers();
        triggerSkills(new TriggerMetadata(this, type, null, null, target, null, null, metadata), passives);
    }

    public void triggerSkills(TriggerType type, PlayerMetadata metadata, Iterable<PassiveSkill> passives, Entity target) {
        triggerSkills(new TriggerMetadata(this, type, null, null, target, null, null, metadata), passives);
    }

    public void triggerSkills(TriggerType type, PlayerMetadata metadata, Iterable<PassiveSkill> passives,
                              Entity target, AttackMetadata attack) {
        EquipmentSlot actionHand = metadata == null ? EquipmentSlot.MAIN_HAND : metadata.getActionHand();
        triggerSkills(new TriggerMetadata(this, type, actionHand, null, target, null, attack, metadata), passives);
    }

    public void applyCooldown(CooldownType type, double duration) {
        getCooldownMap().applyCooldown(type.name(), duration);
    }

    public boolean isOnCooldown(CooldownType type) {
        return getCooldownMap().isOnCooldown(type.name());
    }
}
