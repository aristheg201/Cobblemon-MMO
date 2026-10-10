package vn.svframe.svframemmo.api;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.economy.CurrencyKey;
import vn.svframe.svframelib.api.economy.CurrencyService;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.experience.EXPSource;
import vn.svframe.svframemmo.skilltree.NodeIncrementResult;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Stable native entry point for Cobblemon and other optional Fabric integrations. */
public final class SVFrameMMOApi {
    private SVFrameMMOApi() {}

    public static PlayerData player(UUID id) { return SVFrameMMO.playerData().get(Objects.requireNonNull(id, "id")); }
    public static PlayerData player(ServerPlayerEntity player) { return SVFrameMMO.playerData().get(Objects.requireNonNull(player, "player")); }

    public static void giveExperience(ServerPlayerEntity player, double amount) {
        player(player).giveExperience(amount, EXPSource.API);
    }

    public static void giveProfessionExperience(ServerPlayerEntity player, String professionId, double amount) {
        PlayerData data = player(player);
        data.getProfessions().giveExperience(SVFrameMMO.professions().getOrThrow(professionId), amount, EXPSource.API);
    }

    public static boolean setClass(ServerPlayerEntity player, String classId) {
        return player(player).changeClass(SVFrameMMO.classes().getOrThrow(classId), vn.svframe.svframemmo.api.event.PlayerClassChangeEvent.Reason.UNKNOWN);
    }

    /** Adds spendable points to one class skill tree, or to the shared "global" pool. */
    public static void giveSkillTreePoints(ServerPlayerEntity player, String treeId, int amount) {
        PlayerData data = player(player);
        if (!"global".equalsIgnoreCase(treeId)) SVFrameMMO.skillTrees().getOrThrow(treeId);
        data.getSkillTrees().givePoints(treeId, amount);
    }

    /** Attempts one level of a configured skill-tree node and returns the exact refusal/success reason. */
    public static NodeIncrementResult incrementSkillTreeNode(ServerPlayerEntity player, String nodeId) {
        PlayerData data = player(player);
        var node = SVFrameMMO.skillTrees().findNode(nodeId);
        if (node == null) throw new IllegalArgumentException("Unknown skill-tree node '" + nodeId + "'");
        if (!data.getProfess().getSkillTreeIds().contains(node.getTree().getId()))
            throw new IllegalArgumentException("Skill tree '" + node.getTree().getId() + "' does not belong to class '" + data.getClassId() + "'");
        return data.getSkillTrees().increment(node);
    }

    public static SkillResult castSkill(ServerPlayerEntity player, String skillId) {
        PlayerData data = player(player);
        return SVFrameMMO.skillRuntime().cast(data, skillId);
    }

    public static SkillResult castBoundSkill(ServerPlayerEntity player, int slot) {
        PlayerData data = player(player);
        return SVFrameMMO.skillRuntime().castBound(data, slot);
    }

    /** Shared economy service backed by optional native BEconomy/CobbleDollars integrations in SVFrameLib. */
    public static CurrencyService economy() {
        return SVFrameLib.inst().getEconomy();
    }

    /** Currency syntax: beconomy, beconomy:&lt;currency-type&gt;, or cobbledollars. */
    public static BigDecimal balance(ServerPlayerEntity player, String currency) {
        return economy().balance(Objects.requireNonNull(player, "player"), CurrencyKey.parse(currency));
    }

    public static boolean hasCurrency(ServerPlayerEntity player, String currency, BigDecimal amount) {
        return economy().has(Objects.requireNonNull(player, "player"), CurrencyKey.parse(currency), amount);
    }

    public static void setBalance(ServerPlayerEntity player, String currency, BigDecimal amount) {
        economy().setBalance(Objects.requireNonNull(player, "player"), CurrencyKey.parse(currency), amount);
    }

    public static void deposit(ServerPlayerEntity player, String currency, BigDecimal amount) {
        economy().deposit(Objects.requireNonNull(player, "player"), CurrencyKey.parse(currency), amount);
    }

    public static boolean withdraw(ServerPlayerEntity player, String currency, BigDecimal amount) {
        return economy().withdraw(Objects.requireNonNull(player, "player"), CurrencyKey.parse(currency), amount);
    }

    public static boolean transfer(ServerPlayerEntity from, ServerPlayerEntity to, String currency, BigDecimal amount) {
        return economy().transfer(Objects.requireNonNull(from, "from"), Objects.requireNonNull(to, "to"), CurrencyKey.parse(currency), amount);
    }

    public static boolean currencyAvailable(String currency) {
        return economy().isAvailable(CurrencyKey.parse(currency));
    }

    public static String currencySymbol(String currency) {
        return economy().symbol(CurrencyKey.parse(currency));
    }
}
