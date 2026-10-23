package vn.svframe.fantasyhub.core;
import java.util.*;import java.math.BigDecimal;import net.minecraft.text.Text;import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;import vn.svframe.svframemmo.SVFrameMMO;import vn.svframe.svframelib.api.economy.*;import static net.minecraft.server.command.CommandManager.*;import static com.mojang.brigadier.arguments.StringArgumentType.*;
/** Operator probes on disposable runtime copies only. Uses the production transaction paths. */
final class CommerceQa {
 static void install(){if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA")))return;CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("commerceqa").requires(s->s.hasPermissionLevel(4)).then(argument("action",word()).then(argument("id",com.mojang.brigadier.arguments.StringArgumentType.string()).then(argument("method",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,2)).executes(c->{var p=c.getSource().getPlayerOrThrow();var d=SVFrameMMO.playerData().get(p);String action=getString(c,"action"),id=getString(c,"id");int method=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"method");boolean result=true;String token="";
  switch(action){
   case "seed"->{CurrencyService.get().setBalance(p,CurrencyKey.parse("beconomy:secondary"),new BigDecimal("200000"));CurrencyService.get().setBalance(p,CurrencyKey.parse("beconomy:primary"),new BigDecimal("100"));}
   case "zero"->{CurrencyService.get().setBalance(p,CurrencyKey.parse(CommercePolicy.currency(method)),BigDecimal.ZERO);}
   case "cooldown"->{d.setClassSwitchAvailableAt(System.currentTimeMillis()+3600000);SVFrameMMO.playerData().savePlayerDurably(d).join();}
   case "ready"->{d.setClassSwitchAvailableAt(0);SVFrameMMO.playerData().savePlayerDurably(d).join();}
   case "starter"->ClassProgression.resetStarter(d,true);
   case "unown"->ClassProgression.changeOwnership(d,id,false);
   case "own"->ClassProgression.changeOwnership(d,id,true);
   case "switch"->result=ClassSwitchService.request(p,id,()->{});
   case "bypass"->result=ClassSwitchService.request(p,id,true,()->{});
   case "claim"->CommerceRuntime.claim(p);
   case "buy","duplicate"->{var view=CommerceRuntime.view(p);@SuppressWarnings("unchecked")var offers=(List<Map<String,Object>>)view.get("offers");var offer=offers.stream().filter(o->id.equals(o.get("product"))).findFirst().orElseThrow();token=String.valueOf(offer.get("id"));result=CommerceRuntime.purchase(p,token,method);if(action.equals("duplicate"))result&=!CommerceRuntime.purchase(p,token,method);}
   case "full"->{while(p.getInventory().getEmptySlot()>=0)p.getInventory().setStack(p.getInventory().getEmptySlot(),new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE,64));}
   case "clearfill"->{for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(net.minecraft.item.Items.STONE))p.getInventory().setStack(i,net.minecraft.item.ItemStack.EMPTY);}
   case "status"->{}default->throw new IllegalArgumentException("Unknown QA action");
  }
  var view=CommerceRuntime.view(p);view.remove("offers");view.put("result",result);view.put("transaction",token);view.put("class",d.getClassId());view.put("level",d.getLevel());view.put("owned",ClassProgression.owns(d,id));view.put("cooldown",ClassSwitchService.policy(d).get("remainingSeconds"));view.put("inventory",java.util.stream.IntStream.range(0,p.getInventory().size()).mapToObj(i->p.getInventory().getStack(i)).filter(s->!s.isEmpty()).map(s->s.encode(p.getRegistryManager()).toString()).toList());c.getSource().sendFeedback(()->Text.literal(new com.google.gson.Gson().toJson(view)),false);return result?1:0;
 }))))));}
}
