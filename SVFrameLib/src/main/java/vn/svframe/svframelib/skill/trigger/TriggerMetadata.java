package vn.svframe.svframelib.skill.trigger;

import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.player.PlayerMetadata;
import vn.svframe.svframelib.skill.Skill;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.Lazy;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

/**
 * Immutable trigger context used to lazily create a fresh SkillMetadata for every
 * triggered passive. server-plugin platform Entity/Location values from SVFrameLib 1.7.1 are
 * represented directly by Fabric Entity and Vec3d values.
 */
public class TriggerMetadata {
    private final MMOPlayerData playerData;
    private final TriggerType triggerType;
    private final EquipmentSlot actionHand;
    private final Lazy<SkillMetadata> skillMetaGenerator;

    public TriggerMetadata(MMOPlayerData playerData, TriggerType triggerType) {
        this(playerData, triggerType, (Entity) null);
    }

    public TriggerMetadata(MMOPlayerData playerData, TriggerType triggerType, Entity targetEntity) {
        this(playerData, triggerType, EquipmentSlot.MAIN_HAND, null, targetEntity, null, null, null);
    }

    public TriggerMetadata(MMOPlayerData playerData, TriggerType triggerType, Vec3d targetLocation) {
        this(playerData, triggerType, EquipmentSlot.MAIN_HAND, null, null, targetLocation, null, null);
    }

    public TriggerMetadata(MMOPlayerData playerData, TriggerType triggerType, Vec3d sourceLocation, Vec3d targetLocation) {
        this(playerData, triggerType, EquipmentSlot.MAIN_HAND, sourceLocation, null, targetLocation, null, null);
    }

    public TriggerMetadata(PlayerAttackEvent event, TriggerType triggerType) {
        this(event.getAttacker(), triggerType, event.getEntity(), event.getAttack());
    }

    public TriggerMetadata(PlayerMetadata playerMetadata, TriggerType triggerType, Entity targetEntity, AttackMetadata attack) {
        this(playerMetadata.getData(), triggerType, playerMetadata.getActionHand(), null, targetEntity, null, attack, playerMetadata);
    }

    public TriggerMetadata(MMOPlayerData playerData,
                           TriggerType triggerType,
                           EquipmentSlot actionHand,
                           Vec3d sourceLocation,
                           Entity targetEntity,
                           Vec3d targetLocation,
                           AttackMetadata attack,
                           PlayerMetadata playerMetadata) {
        this(playerData, triggerType, actionHand,
                Lazy.of(() -> SkillMetadata.of(playerData, actionHand, sourceLocation, targetEntity,
                        targetLocation, attack, playerMetadata, null)));
    }

    public TriggerMetadata(MMOPlayerData playerData,
                           TriggerType triggerType,
                           EquipmentSlot actionHand,
                           Lazy<SkillMetadata> skillMetaGenerator) {
        this.playerData = Objects.requireNonNull(playerData);
        this.triggerType = Objects.requireNonNull(triggerType);
        this.actionHand = Objects.requireNonNullElse(actionHand, EquipmentSlot.MAIN_HAND);
        this.skillMetaGenerator = skillMetaGenerator;
    }

    public MMOPlayerData getPlayerData() {
        return playerData;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public EquipmentSlot getActionHand() {
        return actionHand;
    }

    public SkillMetadata toSkillMetadata(Skill skill) {
        return skillMetaGenerator.get().clone(skill);
    }

    @Deprecated
    public TriggerMetadata(PlayerAttackEvent event) {
        this(event, TriggerType.API);
    }

    @Deprecated
    public TriggerMetadata(PlayerMetadata playerMetadata, Entity targetEntity, AttackMetadata attack) {
        this(playerMetadata, TriggerType.API, targetEntity, attack);
    }

    @Deprecated
    public TriggerMetadata(AttackMetadata attack, Entity targetEntity) {
        this((PlayerMetadata) Objects.requireNonNull(attack.getAttacker()), TriggerType.API, targetEntity, attack);
    }

    @Deprecated
    public TriggerMetadata(PlayerMetadata playerMetadata) {
        this(playerMetadata.getData(), TriggerType.API, EquipmentSlot.MAIN_HAND,
                null, null, null, null, playerMetadata);
    }
}
