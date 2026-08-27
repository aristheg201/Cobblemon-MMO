package vn.svframe.svframecore.skill.list;

import net.minecraft.block.Blocks;
import vn.svframe.svframecore.api.event.PlayerResourceUpdateEvent;
import vn.svframe.svframelib.player.skill.PassiveSkill;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.BuiltinSkillHandler;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.def.SimpleSkillResult;
import vn.svframe.svframelib.util.configobject.ConfigObject;

@BuiltinSkillHandler(mods = {"extra"}, triggerable = false)
public final class Neptune_Gift extends SkillHandler<SimpleSkillResult> {
    public Neptune_Gift() { super("NEPTUNE_GIFT"); }
    public Neptune_Gift(ConfigObject config) { super("NEPTUNE_GIFT", config); }

    @Override public SimpleSkillResult getResult(SkillMetadata meta) { throw new RuntimeException("Not supported"); }
    @Override public void whenCast(SimpleSkillResult result, SkillMetadata meta) { throw new RuntimeException("Not supported"); }

    public void onResourceUpdate(PlayerResourceUpdateEvent event) {
        if (event.getUpdateReason().isRegeneration()) return;
        if (!event.getData().isOnline()) return;
        var player = event.getData().getPlayer();
        if (!player.getWorld().getBlockState(player.getBlockPos()).isOf(Blocks.WATER)) return;

        PassiveSkill passive = event.getData().getMMOPlayerData().getPassiveSkillMap().getSkill(this);
        if (passive == null) return;
        double regenerated = event.getDifference();
        if (regenerated < 0d) return;
        double extra = event.getData().getMMOPlayerData().getSkillModifierMap().calculateValue(passive.getTriggeredSkill(), "extra");
        event.setNewAmount(event.getNewAmount() + regenerated * extra / 100d);
    }
}
