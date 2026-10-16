package vn.svframe.fantasyhub.core;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.ItemGenerator;
import java.util.*;

/** Authoritative item presentation snapshots; no client item generation or gameplay mutations. */
final class ClassEquipmentPreviews {
    private static long revision=-1;
    private static Map<String,List<String>> cache=Map.of();
    static Map<String,List<String>> snapshots(ServerPlayerEntity player){
        if(revision!=SVFrameItems.registry().revision()){
            List<String> items=new ArrayList<>();
            for(String suffix:List.of("HAT","CHESTPLATE","LEGGINGS","BOOTS")){
                String id="SVFRAME_DRAGONBORN_ARMOR_"+suffix;
                if(SVFrameItems.registry().item(id)==null)continue;
                var stack=SVFrameItems.generator().generate(id,new ItemGenerator.GenerationContext(1,0));
                items.add(stack.encode(player.getRegistryManager()).toString());
            }
            List<String> hunter=new ArrayList<>();
            for(String suffix:List.of("HELMET","CHESTPLATE","LEGGINGS","BOOTS")) {
                String id="SVFRAME_HUNTER_"+suffix;
                if(SVFrameItems.registry().item(id)!=null)hunter.add(SVFrameItems.generator().generate(id,new ItemGenerator.GenerationContext(1,0)).encode(player.getRegistryManager()).toString());
            }
            List<String> kabuto=new ArrayList<>();for(String suffix:List.of("HELMET","CHESTPLATE","LEGGINGS","BOOTS")){String id="KABUTO_"+suffix;if(SVFrameItems.registry().item(id)!=null)kabuto.add(SVFrameItems.generator().generate(id,new ItemGenerator.GenerationContext(1,0)).encode(player.getRegistryManager()).toString());}
            cache=Map.of("DRAGONBORN",List.copyOf(items),"HUNTER",List.copyOf(hunter),"KABUTO",List.copyOf(kabuto));revision=SVFrameItems.registry().revision();
        }
        return cache;
    }
}
