package vn.svframe.fantasyhub.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.Set;

/** Read-only loadout and cooldown presentation from server snapshots. */
final class AbilityHud {
    private static final Set<String> ICONS=Set.of("death_sentence","death_strike","phantom_charge","cursed_seal","necrotic_whirlwind","wraithbound_chains");
    private AbilityHud(){}
    static Identifier icon(String id){
        String prefix="CLS_DEATH_KNIGHT_";
        if(!id.startsWith(prefix))return null;
        String suffix=id.substring(prefix.length()).toLowerCase(java.util.Locale.ROOT);
        return ICONS.contains(suffix)?Identifier.of("death_knight","textures/death_knight_texture/icon_"+suffix+".png"):null;
    }
    static void render(DrawContext context){
        var state=FantasyHubClient.state();var loadout=state.getAsJsonObject("loadout");var skills=state.getAsJsonArray("skills");
        if(loadout==null||loadout.isEmpty()||skills==null)return;
        var slots=state.getAsJsonArray("loadoutSlots");int count=slots==null?6:slots.size();if(count==0)return;
        var client=MinecraftClient.getInstance();int size=22,gap=3,width=count*(size+gap)-gap;
        int x=(context.getScaledWindowWidth()-width)/2,y=context.getScaledWindowHeight()-91;
        for(int index=0;index<count;index++){
            int slot=slots==null?index+1:slots.get(index).getAsInt();
            int left=x+index*(size+gap);FantasyPanel.frame(context,left,y,size,size);
            String key=String.valueOf(slot),id=loadout.has(key)?loadout.get(key).getAsString():"";
            if(!id.isEmpty())for(var raw:skills){var skill=raw.getAsJsonObject();if(!id.equals(skill.get("id").getAsString()))continue;
                var icon=icon(id);
                if(icon!=null)context.drawTexture(icon,left+3,y+3,16,16,0f,0f,64,64,64,64);
                else {String name=FantasyHubClient.name(skill.get("name").getAsString()).getString();context.drawCenteredTextWithShadow(client.textRenderer,Text.literal(client.textRenderer.trimToWidth(name,16)),left+11,y+7,0xffe8d39a);}
                double remaining=skill.get("cooldownRemaining").getAsDouble();
                if(remaining>0){context.fill(left+2,y+2,left+size-2,y+size-2,0xb5000000);context.drawCenteredTextWithShadow(client.textRenderer,Text.literal(String.format(java.util.Locale.ROOT,"%.0f",Math.ceil(remaining))),left+11,y+7,0xfff2ddbd);}
                else {var parameters=skill.getAsJsonObject("parameters");boolean ready=(parameters==null||(!parameters.has("mana")||FantasyHubClient.number("mana")>=parameters.get("mana").getAsDouble())&&(!parameters.has("stamina")||FantasyHubClient.number("stamina")>=parameters.get("stamina").getAsDouble()));context.fill(left+3,y+size-3,left+size-3,y+size-2,ready?0xffc9aa58:0xffa13239);}
                break;
            }
            context.drawText(client.textRenderer,Text.literal(key),left+1,y+1,0xffdfc389,true);
        }
    }
}
