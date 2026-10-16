package vn.svframe.fantasyhub.core;

import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.profession.mining.CustomMiningRuntime;
import static vn.svframe.fantasyhub.core.EquipmentCompatibilityQa.*;

/** Real installed third-party engines; all destructive storage fixtures are confined to this QA server. */
final class ThirdPartyCompatibilityQa {
  private static final Set<BlockPos> WATCHED=new HashSet<>(),VETO=new HashSet<>();
  private static int breakEvents;
  private static boolean hooks;

  static void quests(ServerPlayerEntity player) throws Exception {
    var report=new LinkedHashMap<String,Object>();
    Class<?> engine=Class.forName("com.cobblemon.mdks.cobblepass.patch.QuestEngine");
    Method state=method(engine,"state",UUID.class),flush=method(engine,"flush"),required=method(engine,"saveRequired",UUID.class);
    Field states=field(engine,"STATES");UUID fixture=UUID.randomUUID();
    Path root=Path.of("config/cobblepass/quests"),backup=root.resolveSibling("quests.qa-original-"+fixture);
    Path retry=root.resolveSibling("quests.qa-retry-"+fixture);
    flush.invoke(null);Files.move(root,backup);
    Object snapshot=null;
    try {
      snapshot=state.invoke(null,fixture);
      flush.invoke(null);
      check(report,"actual_flush_recreates_missing_directory",Files.isRegularFile(root.resolve(fixture+".json")));
      Field dirty=field(snapshot.getClass(),"dirty");
      check(report,"successful_flush_clears_dirty",!dirty.getBoolean(snapshot));
      String lastGood=Files.readString(root.resolve(fixture+".json"));
      Files.move(root,retry);Files.writeString(root,"QA write failure fixture");dirty.setBoolean(snapshot,true);
      flush.invoke(null);
      check(report,"actual_flush_does_not_crash_on_io_error",dirty.getBoolean(snapshot));
      boolean withheld=false;
      try {required.invoke(null,fixture);}catch(InvocationTargetException failure){withheld=failure.getCause() instanceof IllegalStateException;}
      check(report,"reward_transaction_requires_durable_save",withheld);
      check(report,"last_good_quest_json_preserved",Files.readString(retry.resolve(fixture+".json")).equals(lastGood));
      Files.delete(root);Files.move(retry,root);flush.invoke(null);
      check(report,"actual_flush_retries_dirty_state",!dirty.getBoolean(snapshot));
      report.put("verdict","PASS");
    } finally {
      ((Map<?,?>)states.get(null)).remove(fixture);
      if(Files.isRegularFile(root))Files.delete(root);
      if(Files.isDirectory(root)) {
        Files.deleteIfExists(root.resolve(fixture+".json"));
        try(var paths=Files.list(root)){for(Path p:paths.toList())Files.move(p,backup.resolve(p.getFileName()),StandardCopyOption.REPLACE_EXISTING);}
        Files.delete(root);
      }
      if(Files.exists(retry)) {
        Files.deleteIfExists(retry.resolve(fixture+".json"));Files.delete(retry);
      }
      Files.move(backup,root);
      save("cobblepass",report);
    }
  }

  static void mining(ServerPlayerEntity player) throws Exception {
    if(!hooks) {
      hooks=true;
      PlayerBlockBreakEvents.BEFORE.register((world,p,pos,state,entity)->!VETO.contains(pos));
      PlayerBlockBreakEvents.AFTER.register((world,p,pos,state,entity)->{if(WATCHED.contains(pos))breakEvents++;});
    }
    Class<?> runtime=Class.forName("vn.guild.runtime.GuildRuntime");Object engine=runtime.getField("engine").get(null);
    Class<?> type=engine.getClass();var uuid=player.getUuid();
    Object original=type.getMethod("state",UUID.class).invoke(engine,uuid);
    Object originalCopy=new Gson().fromJson(new Gson().toJson(original),original.getClass());
    ItemStack held=player.getMainHandStack().copy();
    var results=new ArrayList<Map<String,Object>>();var minedPositions=new ArrayList<BlockPos>();
    Map<String,Object> report=new LinkedHashMap<>();
    try {
      type.getMethod("setRank",UUID.class,String.class).invoke(engine,uuid,"S");
      Class<?> delivery=Class.forName("vn.guild.core.GuildEngine$Delivery");
      var accept=type.getMethod("accept",UUID.class,String.class,delivery,boolean.class);
      player.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.NETHERITE_PICKAXE));
      var config=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("SVFrameMMO/config.yml");
      String originalConfig=Files.readString(config);
      try {
        int index=0;
        for(boolean custom:new boolean[]{false,true}) {
          String modified=originalConfig.replaceFirst("(?m)(^custom-mining:\\s*\\n\\s*enable:)\\s*(true|false)","$1 "+custom);
          if(modified.equals(originalConfig)&&!originalConfig.contains("enable: "+custom))throw new IllegalStateException("Cannot prepare actual mining config");
          Files.writeString(config,modified);
          if(!SVFrameMMO.reload())throw new IllegalStateException("Mining config reload failed");
          for(var example:List.of(
              new Ore("resource_stone",Blocks.STONE,Blocks.DEEPSLATE),
              new Ore("resource_coal",Blocks.COAL_ORE,Blocks.DEEPSLATE_COAL_ORE),
              new Ore("resource_iron",Blocks.IRON_ORE,Blocks.DEEPSLATE_IRON_ORE),
              new Ore("resource_gold",Blocks.GOLD_ORE,Blocks.DEEPSLATE_GOLD_ORE),
              new Ore("resource_redstone",Blocks.REDSTONE_ORE,Blocks.DEEPSLATE_REDSTONE_ORE),
              new Ore("resource_diamond",Blocks.DIAMOND_ORE,Blocks.DEEPSLATE_DIAMOND_ORE))) {
            Object state=type.getMethod("state",UUID.class).invoke(engine,uuid);
            ((Map<?,?>)state.getClass().getField("active").get(state)).clear();
            Object contract=accept.invoke(engine,uuid,example.id,runtime.getField("INSTANCE").get(null),true);
            long[] progress=(long[])contract.getClass().getField("progress").get(contract);
            Map<String,Object> row=new LinkedHashMap<>();row.put("template",example.id);row.put("custom_mining",custom);
            for(Block block:List.of(example.normal,example.deep)) {
              BlockPos pos=new BlockPos(220+index++,65,0);player.getServerWorld().setBlockState(pos,block.getDefaultState());
              WATCHED.add(pos);int before=breakEvents;
              check(row,block.getTranslationKey()+"_broken",player.interactionManager.tryBreakBlock(pos));
              check(row,block.getTranslationKey()+"_one_after_event",breakEvents==before+1);
              check(row,block.getTranslationKey()+"_is_air",player.getServerWorld().getBlockState(pos).isAir());
              minedPositions.add(pos);
            }
            check(row,"normal_and_deepslate_credited",progress[0]==2);
            BlockPos placed=new BlockPos(220+index++,65,0);
            player.getServerWorld().setBlockState(placed,example.normal.getDefaultState());
            CustomMiningRuntime.instance().markPlaced(player.getServerWorld(),placed);
            SVFrameMMO.nativeExperience().onBlockPlaced(player,player.getServerWorld(),placed,example.normal.getDefaultState());
            Object ledger=runtime.getField("resources").get(null);
            ledger.getClass().getMethod("place",String.class,long.class).invoke(ledger,player.getServerWorld().getRegistryKey().getValue().toString(),placed.asLong());
            check(row,"placed_fixture_broken",player.interactionManager.tryBreakBlock(placed));
            check(row,"placed_block_not_credited",progress[0]==2);
            BlockPos denied=new BlockPos(220+index++,65,0);player.getServerWorld().setBlockState(denied,example.normal.getDefaultState());
            VETO.add(denied);WATCHED.add(denied);int before=breakEvents;
            check(row,"protection_veto_preserved",!player.interactionManager.tryBreakBlock(denied));
            check(row,"cancelled_break_has_no_after_event",breakEvents==before);
            check(row,"cancelled_break_no_quest_credit",progress[0]==2);
            VETO.remove(denied);player.getServerWorld().setBlockState(denied,Blocks.AIR.getDefaultState());
            results.add(row);
          }
        }
      } finally {Files.writeString(config,originalConfig);SVFrameMMO.reload();}
      report.put("native_break_cases",results);report.put("verdict","WAITING_FOR_REGEN_REGRESSION");save("mining",report);
      long started=SVFrameMMO.currentTick();
      vn.svframe.svframelib.fabric.SVFrameLibFabricMod.schedule(2100,()->{
        try {
          check(report,"more_than_original_2000_tick_regen_delay",SVFrameMMO.currentTick()-started>=2100);
          check(report,"natural_ores_stay_mined",minedPositions.stream().allMatch(p->player.getServerWorld().getBlockState(p).isAir()));
          report.put("verdict","PASS");
        } catch(Exception failure){report.put("verdict","FAIL");report.put("failure",failure.toString());}
        save("mining",report);
      });
    } finally {
      WATCHED.clear();VETO.clear();player.equipStack(EquipmentSlot.MAINHAND,held);
      Field players=field(type,"players");
      @SuppressWarnings("unchecked") Map<UUID,Object> states=(Map<UUID,Object>)players.get(engine);
      states.put(uuid,originalCopy);type.getMethod("save",UUID.class).invoke(engine,uuid);
    }
  }
  private record Ore(String id,Block normal,Block deep) {}
  private static Method method(Class<?> c,String name,Class<?>...types)throws Exception {var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
  private static Field field(Class<?> c,String name)throws Exception {var f=c.getDeclaredField(name);f.setAccessible(true);return f;}
}
