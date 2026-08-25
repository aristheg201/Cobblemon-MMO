package io.lumine.mythic.lib.profile;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.event.session.SessionUpdateEvent;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.player.cooldown.CooldownMap;
import io.lumine.mythic.lib.player.particle.ParticleEffectMap;
import io.lumine.mythic.lib.player.permission.PermissionMap;
import io.lumine.mythic.lib.player.potion.PermanentPotionEffectMap;
import io.lumine.mythic.lib.player.skill.PassiveSkillMap;
import io.lumine.mythic.lib.player.skillmod.SkillModifierMap;
import io.lumine.mythic.lib.rpg.provided.PlayerResourceData;
import io.lumine.mythic.lib.script.variable.VariableList;
import io.lumine.mythic.lib.script.variable.VariableScope;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Native Fabric profile session state machine matching MythicLib 1.7.1 semantics.
 */
public class ProfileSession {
    private static final long GHOST_THRESHOLD_MILLIS = 10_000L;
    private static final long TIME_OUT_MILLIS = 86_400_000L;
    private static final List<Identifier> GHOST_CHECK_BLACKLIST =
            List.of(Identifier.of("mmocore", "force_class_select"));

    private final MMOPlayerData playerData;
    private final UUID profileId;
    private volatile ProfileSessionState state = ProfileSessionState.CREATED;
    private final Object fsmLock = new Object();
    private List<Identifier> waiting;
    private List<Identifier> loaded;
    private SessionUpdateReason lastUpdateReason;
    private long lastStateUpdateTimestamp;
    private final List<ProfileSessionCallback> callbacks = new ArrayList<>();

    private final StatMap statMap;
    private final SkillModifierMap skillModifierMap;
    private final PermanentPotionEffectMap permEffectMap;
    private final ParticleEffectMap particleEffectMap;
    private final PassiveSkillMap passiveSkillMap;
    private final PermissionMap permissionMap;
    private final CooldownMap cooldownMap;
    private final PlayerResourceData resourceData;
    private final VariableList variableList;
    private long lastActivity;

    public ProfileSession(MMOPlayerData playerData, UUID profileId) {
        this.playerData = playerData;
        this.profileId = profileId;
        this.statMap = new StatMap(playerData);
        this.skillModifierMap = new SkillModifierMap(playerData);
        this.permEffectMap = new PermanentPotionEffectMap(playerData);
        this.particleEffectMap = new ParticleEffectMap(playerData);
        this.passiveSkillMap = new PassiveSkillMap(playerData);
        this.permissionMap = new PermissionMap(playerData);
        this.cooldownMap = new CooldownMap();
        this.resourceData = new PlayerResourceData(playerData);
        this.variableList = new VariableList(VariableScope.PROFILE);
    }

    /** Rebinds an already-loaded profile's data containers to a new player-data owner. */
    public ProfileSession(MMOPlayerData playerData, ProfileSession source) {
        this.playerData = playerData;
        this.profileId = source.profileId;
        this.statMap = source.statMap;
        this.skillModifierMap = source.skillModifierMap;
        this.permEffectMap = source.permEffectMap;
        this.particleEffectMap = source.particleEffectMap;
        this.passiveSkillMap = source.passiveSkillMap;
        this.permissionMap = source.permissionMap;
        this.cooldownMap = source.cooldownMap;
        this.resourceData = source.resourceData;
        this.variableList = source.variableList;
    }

    public boolean hasProfile() { return profileId != null; }

    public UUID getProfileId() {
        return Objects.requireNonNull(profileId, "No profile");
    }

    public boolean isGhost() {
        if (!state.isWaiting()) return false;
        if (System.currentTimeMillis() < lastStateUpdateTimestamp + GHOST_THRESHOLD_MILLIS) return false;
        for (Identifier module : waiting) {
            if (!GHOST_CHECK_BLACKLIST.contains(module)) return true;
        }
        return false;
    }

    private ProfileSessionState getAndSetState(ProfileSessionState newState) {
        ProfileSessionState oldState = state;
        state = Objects.requireNonNull(newState, "New state cannot be null");
        lastStateUpdateTimestamp = System.currentTimeMillis();
        return oldState;
    }

    public ProfileSessionState getState() { return state; }
    public boolean isReady() { return state == ProfileSessionState.OPEN; }
    public boolean isDead() { return state.isDead(); }
    public boolean wasReady() { return state.wasReady(); }

    public void callSessionUpdateEvent(ProfileSessionState oldState, SessionUpdateReason reason) {
        Objects.requireNonNull(reason, "Reason cannot be null");
        new SessionUpdateEvent(playerData, this, reason, oldState, state).call();
    }

    public boolean isReady(Identifier moduleKey) {
        synchronized (fsmLock) {
            if (state == ProfileSessionState.OPEN || state == ProfileSessionState.CLOSING) return true;
            if (state == ProfileSessionState.CREATED) return false;
            return loaded.contains(moduleKey);
        }
    }

    public void initializeSession(SessionUpdateReason reason) {
        synchronized (fsmLock) {
            if (state != ProfileSessionState.CREATED) {
                throw new IllegalArgumentException("Can only initialize new session from state DEAD");
            }
        }
        callSessionUpdateEvent(ProfileSessionState.DEAD, reason);
        initializeOpening(reason);
    }

    private void initializeOpening(SessionUpdateReason reason) {
        Objects.requireNonNull(reason, "Reason cannot be null");
        ProfileSessionState oldState;
        synchronized (fsmLock) {
            if (state != ProfileSessionState.CREATED) {
                throw new IllegalArgumentException("Can only initialize opening from state CREATED");
            }
            lastUpdateReason = reason;
            oldState = getAndSetState(ProfileSessionState.OPENING);
            waiting = MythicLib.plugin.getProfileHandler().collectModules();
            loaded = new ArrayList<>();
        }
        callSessionUpdateEvent(oldState, reason);
        checkReadiness();
    }

    public void markAsReady(Identifier moduleKey) {
        Objects.requireNonNull(moduleKey, "Module key cannot be null");
        synchronized (fsmLock) {
            if (state != ProfileSessionState.OPENING) {
                throw new IllegalArgumentException("Session state is " + state.name() + ", not OPENING");
            }
            boolean removed = waiting.remove(moduleKey);
            if (!removed) throw new IllegalArgumentException(String.format("Module %s already synced", moduleKey));
            loaded.add(moduleKey);
        }
        checkReadiness();
    }

    private void checkReadiness() {
        ProfileSessionState oldState;
        SessionUpdateReason reason;
        synchronized (fsmLock) {
            if (!waiting.isEmpty()) return;
            oldState = getAndSetState(ProfileSessionState.OPEN);
            reason = lastUpdateReason;
        }
        callSessionUpdateEvent(oldState, reason);
        openDataSession();
        lastUpdateReason = null;
    }

    public void shutdown() {
        try {
            initializeClosing(SessionUpdateReason.LOG_OUT);
        } catch (Exception ignored) {
            // 1.7.1 deliberately makes shutdown idempotent/best-effort.
        }
    }

    public void initializeClosing(SessionUpdateReason reason) {
        Objects.requireNonNull(reason, "Reason cannot be null");
        ProfileSessionState oldState;
        synchronized (fsmLock) {
            if (state.isClosing() || state.isDead()) return;

            if (state == ProfileSessionState.CREATED || state == ProfileSessionState.OPENING) {
                oldState = getAndSetState(ProfileSessionState.ABORTING);
            } else if (state == ProfileSessionState.OPEN) {
                oldState = getAndSetState(ProfileSessionState.CLOSING);
                closeDataSession();
            } else {
                throw new IllegalStateException("Cannot close profile session from state " + state.name());
            }

            lastUpdateReason = reason;
            callbacks.clear();
            playerData.clearTemporaryHandlers();
            waiting = loaded;
        }
        callSessionUpdateEvent(oldState, reason);
        checkClosed();
    }

    public void addCloseCallback(ProfileSessionCallback callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        synchronized (fsmLock) {
            if (!state.isClosing()) throw new IllegalArgumentException("Session is not closing");
            callbacks.add(callback);
        }
    }

    public void markAsClosed(Identifier moduleKey) {
        Objects.requireNonNull(moduleKey, "Module key cannot be null");
        final boolean removed;
        synchronized (fsmLock) {
            if (!state.isClosing()) {
                throw new IllegalArgumentException("Session state is " + state.name() + ", not closing");
            }
            removed = waiting.remove(moduleKey);
        }
        if (!removed) throw new IllegalArgumentException(String.format("Module %s already marked as closed", moduleKey));
        checkClosed();
    }

    private void checkClosed() {
        ProfileSessionState oldState;
        SessionUpdateReason reason;
        synchronized (fsmLock) {
            if (!waiting.isEmpty()) return;
            setLastActivity();
            oldState = getAndSetState(state == ProfileSessionState.ABORTING
                    ? ProfileSessionState.DEAD_EARLY
                    : ProfileSessionState.DEAD);
            reason = lastUpdateReason;
        }

        playerData.saveCurrentProfileSession();
        callbacks.forEach(callback -> callback.callback(this));
        callSessionUpdateEvent(oldState, reason);
        lastUpdateReason = null;
        playerData.applyNextSessionBuffer();
    }

    @Override
    public String toString() {
        return "ProfileSession{" + playerData.getUniqueId() + ", profile=" + profileId +
                ", state=" + state + ", waiting=" + waiting + '}';
    }

    public StatMap getStatMap() { return statMap; }
    public SkillModifierMap getSkillModifierMap() { return skillModifierMap; }
    public PermanentPotionEffectMap getPermanentEffectMap() { return permEffectMap; }
    public ParticleEffectMap getParticleEffectMap() { return particleEffectMap; }
    public PassiveSkillMap getPassiveSkillMap() { return passiveSkillMap; }
    public PermissionMap getPermissionMap() { return permissionMap; }
    public CooldownMap getCooldownMap() { return cooldownMap; }
    public PlayerResourceData getResources() { return resourceData; }
    public VariableList getVariableList() { return variableList; }

    private void openDataSession() {
        statMap.openSession();
        skillModifierMap.openSession();
        permEffectMap.openSession();
        particleEffectMap.openSession();
        passiveSkillMap.openSession();
        permissionMap.openSession();
        cooldownMap.openSession();
        resourceData.openSession();
    }

    private void closeDataSession() {
        statMap.closeSession();
        skillModifierMap.closeSession();
        permEffectMap.closeSession();
        particleEffectMap.closeSession();
        passiveSkillMap.closeSession();
        permissionMap.closeSession();
        cooldownMap.closeSession();
        resourceData.closeSession();
    }

    private void setLastActivity() {
        lastActivity = System.currentTimeMillis();
    }

    public boolean isTimedOut() {
        return isDead() && lastActivity + TIME_OUT_MILLIS < System.currentTimeMillis();
    }
}
