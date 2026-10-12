package vn.svframe.fantasyhub.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.api.economy.CurrencyKey;
import vn.svframe.svframelib.api.economy.CurrencyService;
import vn.svframe.svframelib.skill.PlayerAbilityShop;
import vn.svframe.svframemmo.SVFrameMMO;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import static net.minecraft.server.command.CommandManager.*;

/** Admin-only live probes; unavailable unless the existing runtime QA flag is enabled. */
final class EconomyPolicyQa {
    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA")))return;
        dispatcher.register(literal("fantasyhubeconomyqa").requires(s->s.hasPermissionLevel(2))
            .then(argument("scenario",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),ctx.getSource().getPlayerOrThrow(),StringArgumentType.getString(ctx,"scenario")))));
    }
    private static int run(ServerCommandSource source,ServerPlayerEntity player,String scenario) {
        var service=CurrencyService.get();var beast=CurrencyKey.beconomy("secondary");var hunter=CurrencyKey.beconomyPrimary();var dollars=CurrencyKey.cobbleDollars();
        if(scenario.equals("seed")) {
            service.setBalance(player,beast,BigDecimal.valueOf(10000));service.setBalance(player,hunter,BigDecimal.TEN);service.setBalance(player,dollars,BigDecimal.valueOf(2000000));
            SVFrameMMO.playerData().get(player).setClassSwitchAvailableAt(0);
        } else if(scenario.equals("reset_cooldown"))SVFrameMMO.playerData().get(player).setClassSwitchAvailableAt(0);
        else if(scenario.equals("zero")) {
            service.setBalance(player,beast,BigDecimal.ZERO);service.setBalance(player,dollars,BigDecimal.ZERO);
        } else if(scenario.startsWith("buy_")) {
            var shop=PlayerAbilityShop.get();
            var offer=shop.catalog(player).stream().filter(r->!Boolean.TRUE.equals(r.get("owned"))).findFirst().orElseThrow();
            int option=scenario.equals("buy_beast")?1:scenario.equals("buy_dollars")?2:9;
            shop.purchaseUsing(player,String.valueOf(offer.get("id")),option);
            if(option!=9)shop.purchaseUsing(player,String.valueOf(offer.get("id")),option);
        }
        Map<String,Object> report=new LinkedHashMap<>();report.put("scenario",scenario);report.put("class",SVFrameMMO.playerData().get(player).getClassId());
        report.put("beastcoin",service.balance(player,beast).toPlainString());report.put("huntercoin",service.balance(player,hunter).toPlainString());report.put("cobbledollars",service.balance(player,dollars).toPlainString());
        for(String id:new String[]{"PALADIN","DRAGONBORN","DEATH_KNIGHT"})report.put(id,ClassSwitchService.offer(id,player));
        report.put("cooldown",ClassSwitchService.policy(SVFrameMMO.playerData().get(player)).get("remainingSeconds"));
        report.put("ownedMoves",PlayerAbilityShop.get().catalog(player).stream().filter(r->Boolean.TRUE.equals(r.get("owned"))).map(r->r.get("id")).toList());
        source.sendFeedback(()->Text.literal(new com.google.gson.Gson().toJson(report)),false);return 1;
    }
}
