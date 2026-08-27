package vn.svframe.svframeitems.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.*;

import static net.minecraft.server.command.CommandManager.*;

public final class SVFrameItemsCommands {
    private SVFrameItemsCommands(){}
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher){
        dispatcher.register(literal("svframeitems").requires(source->source.hasPermissionLevel(2))
                .then(literal("reload").executes(ctx->{boolean ok=SVFrameItems.reload();ctx.getSource().sendFeedback(()->Text.literal(ok?"SVFrameItems reloaded: "+SVFrameItems.registry().summary():"SVFrameItems reload failed; previous definitions kept"),false);return ok?1:0;}))
                .then(literal("give").then(argument("player",EntityArgumentType.player()).then(argument("item",StringArgumentType.word())
                        .executes(ctx->give(ctx.getSource(),EntityArgumentType.getPlayer(ctx,"player"),StringArgumentType.getString(ctx,"item"),1))
                        .then(argument("level",IntegerArgumentType.integer(1)).executes(ctx->give(ctx.getSource(),EntityArgumentType.getPlayer(ctx,"player"),StringArgumentType.getString(ctx,"item"),IntegerArgumentType.getInteger(ctx,"level")))))))
                .then(literal("inspect").executes(ctx->{ServerPlayerEntity player=ctx.getSource().getPlayerOrThrow();return ItemCodec.read(player.getMainHandStack()).map(item->{ctx.getSource().sendFeedback(()->Text.literal("SVFrameItem id="+item.definitionId()+" type="+item.typeId()+" rarity="+item.rarityId()+" level="+item.itemLevel()+" +"+item.upgradeLevel()+" revision="+item.stateRevision()+" stats="+item.stats().size()+" sockets="+item.sockets().size()),false);return 1;}).orElseGet(()->{ctx.getSource().sendError(Text.literal("Main hand is not an SVFrameItem"));return 0;});}))
                .then(literal("upgrade").executes(ctx->{ServerPlayerEntity player=ctx.getSource().getPlayerOrThrow();UpgradeService.Result result=SVFrameItems.upgrades().attempt(player.getMainHandStack());if(result.status()==UpgradeService.Status.SUCCESS||result.status()==UpgradeService.Status.FAILED||result.status()==UpgradeService.Status.DESTROYED)player.setStackInHand(Hand.MAIN_HAND,result.item());ctx.getSource().sendFeedback(()->Text.literal("Upgrade: "+result.status()+" "+result.oldLevel()+" -> "+result.newLevel()+" chance="+Math.round(result.successChance()*10000d)/100d+"%"),false);return result.success()?1:0;}))
                .then(literal("socket").executes(ctx->{ServerPlayerEntity player=ctx.getSource().getPlayerOrThrow();SocketService.InsertResult result=SVFrameItems.sockets().insert(player.getMainHandStack(),player.getOffHandStack());if(result.success()){player.setStackInHand(Hand.MAIN_HAND,result.target());player.setStackInHand(Hand.OFF_HAND,result.gemRemainder());}ctx.getSource().sendFeedback(()->Text.literal("Socket: "+result.status()+(result.success()?" slot="+result.socketIndex():"")),false);return result.success()?1:0;}))
        );
    }
    private static int give(ServerCommandSource source,ServerPlayerEntity player,String item,int level){try{var stack=SVFrameItems.generator().generate(item,level);if(!player.giveItemStack(stack))player.dropItem(stack,false);source.sendFeedback(()->Text.literal("Gave "+item+" level "+level+" to "+player.getName().getString()),true);return 1;}catch(RuntimeException exception){source.sendError(Text.literal(exception.getMessage()));return 0;}}
}
