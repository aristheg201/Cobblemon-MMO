package vn.svframe.fantasyhub.core;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.persistence.PlayerDataSnapshot;

/** Opt-in real vanilla EXP, enchantment, anvil and orb/Mending regression on a QA player. */
final class VanillaExperienceQa {
    private static final Set<UUID> running=new HashSet<>();
    static int run(ServerPlayerEntity player) {
        if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))||!running.add(player.getUuid()))
            throw new IllegalStateException("Vanilla EXP QA unavailable or already running");
        var data=SVFrameMMO.playerData().get(player);
        var profile=PlayerDataSnapshot.capture(data);float health=player.getHealth();
        int level=player.experienceLevel,total=player.totalExperience;float progress=player.experienceProgress;
        var equipment=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);
        for(var slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))equipment.put(slot,player.getEquippedStack(slot).copy());
        var orbs=new ArrayList<ExperienceOrbEntity>();
        var report=new LinkedHashMap<String,Object>();
        report.put("mode",vn.svframe.svframemmo.experience.vanilla.VanillaProgressionRuntime.instance().experienceDisplayMode());
        Runnable restore=()->{
            orbs.forEach(ExperienceOrbEntity::discard);
            equipment.forEach(player::equipStack);data.prepareReload();profile.apply(data);data.reloadDefinitions();
            player.setHealth(Math.min(health,player.getMaxHealth()));SVFrameMMO.playerData().savePlayer(data);
            player.experienceLevel=level;player.experienceProgress=progress;player.totalExperience=total;
            player.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(progress,total,level));
            running.remove(player.getUuid());
        };
        try {
            data.setLevel(50);data.setExperience(0);
            for(var slot:equipment.keySet())player.equipStack(slot,ItemStack.EMPTY);
            seed(player,10,160);player.addExperience(7);
            check(report,"vanillaGain",player.experienceLevel==10&&player.totalExperience==167&&Math.abs(player.experienceProgress-7f/27)<1e-6);
            report.put("mirroredRpgExperience",data.getExperience());
            var orb=new ExperienceOrbEntity(player.getWorld(),player.getX(),player.getY(),player.getZ(),5);
            orbs.add(orb);
            player.getServerWorld().spawnEntity(orb);player.experiencePickUpDelay=0;orb.onPlayerCollision(player);
            check(report,"realOrbPickup",orb.isRemoved()&&player.totalExperience==172&&Math.abs(player.experienceProgress-12f/27)<1e-6);
            var sword=new ItemStack(Items.DIAMOND_SWORD);sword.setDamage(50);
            sword.addEnchantment(player.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.MENDING),1);
            player.equipStack(EquipmentSlot.MAINHAND,sword);
            var mendingOrb=new ExperienceOrbEntity(player.getWorld(),player.getX(),player.getY(),player.getZ(),10);
            orbs.add(mendingOrb);
            player.getServerWorld().spawnEntity(mendingOrb);player.experiencePickUpDelay=0;mendingOrb.onPlayerCollision(player);
            check(report,"mendingConsumesOrbForDurability",mendingOrb.isRemoved()&&sword.getDamage()==30&&player.totalExperience==172);
            var context=ScreenHandlerContext.create(player.getServerWorld(),player.getBlockPos());
            seed(player,30,1395);
            var enchanting=new EnchantmentScreenHandler(0,player.getInventory(),context);
            enchanting.getSlot(0).setStack(new ItemStack(Items.DIAMOND_SWORD));enchanting.getSlot(1).setStack(new ItemStack(Items.LAPIS_LAZULI,3));
            enchanting.onContentChanged(enchanting.getSlot(0).inventory);
            check(report,"nativeEnchantButton",enchanting.onButtonClick(player,0)&&enchanting.getSlot(0).getStack().hasEnchantments()&&player.experienceLevel==29&&enchanting.getSlot(1).getStack().getCount()==2);
            var anvil=new AnvilScreenHandler(0,player.getInventory(),context);
            anvil.getSlot(0).setStack(new ItemStack(Items.DIAMOND_SWORD));anvil.setNewItemName("SVFrame XP QA");
            int cost=anvil.getLevelCost(),before=player.experienceLevel;
            var output=anvil.getSlot(2);
            check(report,"anvilOutputAvailable",cost>0&&output.canTakeItems(player)&&!output.getStack().isEmpty());
            ItemStack taken=output.takeStack(1);output.onTakeItem(player,taken);
            check(report,"nativeAnvilCost",player.experienceLevel==before-cost);
            int expectedLevel=player.experienceLevel,expectedTotal=player.totalExperience;float expectedProgress=player.experienceProgress;
            SVFrameLibFabricMod.schedule(40,()->{
                try {
                    check(report,"costsNotOverwrittenByRpgTicks",player.experienceLevel==expectedLevel&&player.totalExperience==expectedTotal&&player.experienceProgress==expectedProgress);
                    report.put("actualVanillaLevel",player.experienceLevel);report.put("runtimeVerdict","PASS");
                } catch(RuntimeException error){report.put("runtimeVerdict","FAIL");report.put("failure",error.toString());}
                finally {restore.run();save(report);}
            });
            return 1;
        } catch(RuntimeException error){report.put("runtimeVerdict","FAIL");report.put("failure",error.toString());restore.run();save(report);throw error;}
    }
    private static void seed(ServerPlayerEntity player,int level,int total){player.experienceLevel=level;player.experienceProgress=0;player.totalExperience=total;}
    private static void check(Map<String,Object> report,String name,boolean result){report.put(name,result);if(!result)throw new IllegalStateException(name);}
    private static void save(Map<String,Object> report){try{Path root=Path.of("qa/vanilla-xp");Files.createDirectories(root);Files.writeString(root.resolve(report.get("mode")+"-runtime.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException error){throw new IllegalStateException(error);}}
}
