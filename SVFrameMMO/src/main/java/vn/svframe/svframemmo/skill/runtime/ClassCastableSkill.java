package vn.svframe.svframemmo.skill.runtime;

import vn.svframe.svframelib.api.event.skill.PlayerCastSkillEvent;
import vn.svframe.svframelib.api.event.skill.SkillCastEvent;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.skill.Skill;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.skill.result.def.SimpleSkillResult;
import vn.svframe.svframelib.skill.trigger.TriggerType;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.util.Objects;

/** Executes a shared SVFrameLib skill handler with the correct class/external progression level and cost semantics. */
public final class ClassCastableSkill extends Skill {
    private final ClassSkill classSkill;
    private final PlayerData caster;
    private final boolean requireClassProgression;

    public ClassCastableSkill(ClassSkill classSkill, PlayerData caster) {
        this(classSkill, caster, true);
    }

    ClassCastableSkill(ClassSkill classSkill, PlayerData caster, boolean requireClassProgression) {
        super(Objects.requireNonNull(classSkill, "classSkill").getSkill());
        this.classSkill = classSkill;
        this.caster = Objects.requireNonNull(caster, "caster");
        this.requireClassProgression = requireClassProgression;
    }

    public ClassSkill getClassSkill() { return classSkill; }
    public boolean requiresClassProgression() { return requireClassProgression; }

    @Override
    public TriggerType getTrigger() { return classSkill.getTrigger(); }

    @Override
    public boolean getResult(SkillMetadata metadata) {
        if (!validate(metadata)) return false;
        SkillResult result = handlerResult(metadata);
        return result != null && result.isSuccessful(metadata);
    }

    @Override
    public void whenCast(SkillMetadata metadata) {
        SkillResult result = handlerResult(metadata);
        if (result != null && result.isSuccessful(metadata)) {
            applyCosts(metadata);
            handlerCast(result, metadata);
        }
    }

    /**
     * Overrides the generic shared lifecycle so the handler result is evaluated exactly once.
     * This matters for target/random/result-producing handlers.
     */
    @Override
    public <T extends SkillResult> SkillResult cast(SkillMetadata metadata) {
        SkillResult result = validate(metadata) ? handlerResult(metadata) : new SimpleSkillResult(false);
        if (result == null) result = new SimpleSkillResult(false);
        PlayerCastSkillEvent before = new PlayerCastSkillEvent(this, metadata, result).call();
        if (!before.isCancelled() && result.isSuccessful(metadata)) {
            applyCosts(metadata);
            handlerCast(result, metadata);
        }
        new SkillCastEvent(this, metadata, result).call();
        return result;
    }

    private boolean validate(SkillMetadata metadata) {
        if (!caster.isOnline()) return false;
        if (requireClassProgression && !caster.canUseSkill(classSkill)) return false;
        MMOPlayerData mmo = caster.getMMOPlayerData();
        if (!getTrigger().isPassive() && mmo.getCooldownMap().isOnCooldown(this)) return false;
        double mana = Math.max(0d, metadata.getParameter("mana"));
        double stamina = Math.max(0d, metadata.getParameter("stamina"));
        return caster.getMana() >= mana && caster.getStamina() >= stamina;
    }

    private void applyCosts(SkillMetadata metadata) {
        if (getTrigger().isPassive()) return;
        MMOPlayerData mmo = caster.getMMOPlayerData();
        double cooldown = Math.max(0d, metadata.getParameter("cooldown"));
        if (cooldown > 0d) {
            double reduction = Math.max(0d, Math.min(1d, mmo.getStatMap().getStat("COOLDOWN_REDUCTION") / 100d));
            mmo.getCooldownMap().applyCooldown(this, cooldown).reduceInitialCooldown(reduction);
        }
        double mana = Math.max(0d, metadata.getParameter("mana"));
        double stamina = Math.max(0d, metadata.getParameter("stamina"));
        if (mana != 0d) caster.giveMana(-mana, ResourceUpdateReason.SKILL);
        if (stamina != 0d) caster.giveStamina(-stamina, ResourceUpdateReason.SKILL);
        caster.markCombat();
    }

    @Override
    public double getParameter(String parameter) {
        int level;
        if (requireClassProgression) {
            level = caster.getSkillLevel(classSkill.getSkill());
        } else {
            int externalLevel = SVFrameMMO.externalProgression().level(caster.getUniqueId(), classSkill.getSkill().getId());
            level = externalLevel > 0 ? externalLevel : 1;
        }
        return classSkill.getParameter(parameter, level, caster);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private SkillResult handlerResult(SkillMetadata metadata) {
        SkillHandler handler = classSkill.getSkill();
        return (SkillResult) handler.getResult(metadata);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void handlerCast(SkillResult result, SkillMetadata metadata) {
        SkillHandler handler = classSkill.getSkill();
        handler.whenCast(result, metadata);
    }
}
