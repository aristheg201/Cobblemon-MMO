package vn.svframe.svframemmo.skill.gui;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.SVFrameMMO;

/** Backwards-compatible entrypoint for callers; the actual UI is the config-driven unified MMOCore SkillList. */
public final class SkillListGui {
    private SkillListGui() { }
    public static void open(ServerPlayerEntity player) {
        SVFrameMMO.gui().openSkills(SVFrameMMO.playerData().get(player));
    }
}
