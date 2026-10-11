package vn.svframe.fantasyhub.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ArmorItem;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.entity.EquipmentSlot;

/** An isolated player mannequin renders the real source armor with the normal player renderers. */
final class ClassModelPreview {
    private OtherClientPlayerEntity mannequin;
    private String previous="";
    void render(DrawContext context,String classId,int left,int top,int right,int bottom,int mouseX,int mouseY){
        var client=MinecraftClient.getInstance();if(client.world==null||client.player==null)return;
        var all=FantasyHubClient.state().getAsJsonObject("classPreviews");if(all==null||!all.has(classId))return;
        String data=all.get(classId).toString();
        if(mannequin==null||mannequin.getWorld()!=client.world||!data.equals(previous)){
            mannequin=new OtherClientPlayerEntity(client.world,client.player.getGameProfile());previous=data;
            for(var raw:all.getAsJsonArray(classId)){
                try{var stack=ItemStack.fromNbt(client.world.getRegistryManager(),StringNbtReader.parse(raw.getAsString())).orElse(ItemStack.EMPTY);
                    if(stack.getItem() instanceof ArmorItem armor)mannequin.equipStack(armor.getSlotType(),stack);
                }catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new IllegalStateException("Invalid server armor preview",error);}
            }
        }
        InventoryScreen.drawEntity(context,left,top,right,bottom,Math.max(20,Math.min(55,(bottom-top)/2)),.0625f,mouseX,mouseY,mannequin);
    }
}
