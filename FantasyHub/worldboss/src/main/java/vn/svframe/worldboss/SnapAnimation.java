package vn.svframe.worldboss;
import net.minecraft.inventory.*;import net.minecraft.screen.*;import net.minecraft.screen.slot.SlotActionType;import net.minecraft.entity.player.*;import net.minecraft.server.network.ServerPlayerEntity;import net.minecraft.item.*;import net.minecraft.component.DataComponentTypes;import net.minecraft.component.type.LoreComponent;import net.minecraft.text.Text;import net.minecraft.util.Formatting;import net.minecraft.sound.*;import net.minecraft.particle.ParticleTypes;import java.util.*;
/** Self-contained port of the supplied ShadowCrates CSGO carousel. No ShadowCrates/CustomGUI/Kotlin dependency. */
final class SnapAnimation {
 static final String[] LABELS={"Cosmic Fortune","Type Surge","Legendary Arrival","Erasure","Pokédex Fracture","Party & PC Faint","Rival Party & PC Faint","Wild Dust","Sacrifice","Blank Snap"};
 private static final Item[] ICONS={Items.GOLD_BLOCK,Items.FIRE_CHARGE,Items.DRAGON_EGG,Items.WITHER_SKELETON_SKULL,Items.WRITABLE_BOOK,Items.RED_BED,Items.PURPLE_BED,Items.GUNPOWDER,Items.TOTEM_OF_UNDYING,Items.GLASS};
 static void start(ServerPlayerEntity player,int outcome,Runnable resolve){
  var inventory=new SimpleInventory(27);int[] reel=new int[9];for(int i=0;i<9;i++)reel[i]=player.getRandom().nextInt(10);
  for(int i=0;i<27;i++)inventory.setStack(i,named(i==4||i==22?Items.YELLOW_STAINED_GLASS_PANE:Items.BLACK_STAINED_GLASS_PANE,i==4?"▼ COSMIC FATE ▼":i==22?"▲ RECORDED OUTCOME ▲":" ",Formatting.DARK_PURPLE));
  for(int i=0;i<6;i++)inventory.setStack(i<3?i:i+3,new ItemStack(vn.svframe.fantasyhub.protocol.Protocol.infinityItems.get(List.of("space","power","reality","mind","soul","time").get(i))));
  final ScreenHandler[] handler={null};player.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync,inv,p)->{
   var h=new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3,sync,inv,inventory,3){@Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity p){}@Override public ItemStack quickMove(PlayerEntity p,int slot){return ItemStack.EMPTY;}@Override public boolean canUse(PlayerEntity p){return p==player;}};handler[0]=h;return h;
  },Text.literal("✦ INFINITY SNAP ✦").formatted(Formatting.DARK_PURPLE,Formatting.BOLD)));
  player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE,SoundCategory.PLAYERS,1,.55f);
  int at=0;for(int i=0;i<60;i++){final int step=i;at+=(int)SnapSpin.delay(i);InfinityRuntime.later(at,()->{if(player.isDisconnected())return;SnapSpin.advance(reel,step,outcome,player.getRandom().nextInt(10));for(int col=0;col<9;col++){int n=reel[col];var icon=named(ICONS[n],LABELS[n],col==4?Formatting.LIGHT_PURPLE:Formatting.GRAY);icon.set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal("Fate is sealed when the cosmic reel begins.").formatted(Formatting.DARK_GRAY),Text.literal("30-minute cooldown • One chance").formatted(Formatting.DARK_PURPLE))));inventory.setStack(9+col,icon);}if(handler[0]!=null)handler[0].sendContentUpdates();player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),SoundCategory.PLAYERS,.5f,(float)(1.6-step*.012));player.getServerWorld().spawnParticles(ParticleTypes.PORTAL,player.getX(),player.getY()+1,player.getZ(),8,.6,.8,.6,.1);});}
  InfinityRuntime.later(at+20,()->{if(player.isDisconnected())return;player.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,SoundCategory.PLAYERS,.35f,1.3f);player.sendMessage(Text.literal("✦ "+LABELS[outcome]+" ✦").formatted(Formatting.LIGHT_PURPLE,Formatting.BOLD),false);resolve.run();});
  InfinityRuntime.later(at+50,()->{if(!player.isDisconnected()&&player.currentScreenHandler==handler[0])player.closeHandledScreen();});
 }
 private static ItemStack named(Item item,String name,Formatting color){var s=new ItemStack(item);s.set(DataComponentTypes.CUSTOM_NAME,Text.literal(name).formatted(color));return s;}
}
