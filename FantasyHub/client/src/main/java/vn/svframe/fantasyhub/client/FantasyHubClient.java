package vn.svframe.fantasyhub.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import io.netty.buffer.Unpooled;
import org.lwjgl.glfw.GLFW;
import vn.svframe.fantasyhub.protocol.Protocol;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

public final class FantasyHubClient implements ClientModInitializer {
    private static JsonObject state=new JsonObject();
    public static boolean hudEnabled=true;
    public static float hudScale=1;
    static PresentationOptions options;
    private long ticks;
    private boolean observed;
    @Override public void onInitializeClient(){
        Protocol.install();
        options=PresentationOptions.load();hudEnabled=options.enabled;hudScale=options.scale;
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register((stack,tint)->net.minecraft.component.type.DyedColorComponent.getColor(stack,0xffffff),Protocol.visualBone);
        ClientLoginNetworking.registerGlobalReceiver(Protocol.LOGIN,(client,handler,buf,callbacks)->{
            int version=buf.readVarInt();String assets=buf.readString(128);
            PacketByteBuf response=new PacketByteBuf(Unpooled.buffer());
            boolean present=false;
            try(var asset=FantasyHubClient.class.getResourceAsStream("/assets/fantasyhub/asset-revision.txt")){
                present=asset!=null&&Protocol.ASSET_REVISION.equals(new String(asset.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim())&&AssetIntegrity.verified();
            }catch(java.io.IOException ignored){}
            response.writeVarInt(present&&version==Protocol.VERSION?Protocol.VERSION:0);
            response.writeString(present&&Protocol.ASSET_REVISION.equals(assets)?assets:"missing-assets",128);
            return CompletableFuture.completedFuture(response);
        });
        ClientPlayNetworking.registerGlobalReceiver(Protocol.State.ID,(payload,context)->{
            JsonObject next=JsonParser.parseString(payload.json()).getAsJsonObject();
            if(next.get("schema").getAsInt()!=Protocol.VERSION)return;
            state=next;observed=false;
            if(context.client().currentScreen instanceof HubScreen screen)screen.refresh();
            if(context.client().currentScreen instanceof PokemonShopScreen screen)screen.refresh();
        });
        ClientPlayNetworking.registerGlobalReceiver(Protocol.Open.ID,(payload,context)->{
            if(payload.screen().equals("pokemon_shop"))context.client().setScreen(new PokemonShopScreen());
            else try{context.client().setScreen(new HubScreen(HubScreen.View.valueOf(payload.screen().toUpperCase(java.util.Locale.ROOT))));}catch(IllegalArgumentException ignored){}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->state=new JsonObject());
        var menu=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fantasyhub.menu",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"category.fantasyhub"));
        KeyBinding[] abilities=new KeyBinding[9];
        for(int i=0;i<abilities.length;i++)abilities[i]=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fantasyhub.slot."+(i+1),InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"category.fantasyhub"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            ticks++;
            while(menu.wasPressed())if(client.player!=null&&state.has("schema"))client.setScreen(new HubScreen(HubScreen.View.PROFILE));
            for(int i=0;i<abilities.length;i++)while(abilities[i].wasPressed())if(client.currentScreen==null)send("cast","",i+1);
            if(!observed&&ticks%10==0&&client.player!=null&&state.has("schema")&&"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))){
                JsonObject o=new JsonObject();o.addProperty("clientHealth",client.player.getHealth());o.addProperty("clientMaxHealth",client.player.getMaxHealth());
                o.addProperty("hurtTime",client.player.hurtTime);o.addProperty("maxHurtTime",client.player.maxHurtTime);
                o.addProperty("stateHealth",number("health"));o.addProperty("stateMaxHealth",number("healthMax"));o.addProperty("clientTick",ticks);
                o.addProperty("damagePackets",ClientFeedbackQa.damagePackets);o.addProperty("hurtCameraFrames",ClientFeedbackQa.hurtCameraFrames);
                if(ClientPlayNetworking.canSend(Protocol.Observation.ID))ClientPlayNetworking.send(new Protocol.Observation(o.toString()));
                observed=true;
            }
        });
        HudRenderCallback.EVENT.register((context,counter)->{
            var client=MinecraftClient.getInstance();
            if(!hudEnabled||client.player==null||client.options.hudHidden||client.currentScreen!=null||!state.has("schema"))return;
            context.getMatrices().push();context.getMatrices().scale(hudScale,hudScale,1);
            int visible=0;for(String resource:new String[]{"health","mana","stamina","magic"})if(options.visible(resource))visible++;
            int panelWidth=options.compact?160:190,rowHeight=options.compact?12:15;
            // Reserve the left edge for Cobblemon's party column.
            int x=options.right?Math.max(0,(int)(context.getScaledWindowWidth()/hudScale)-panelWidth-12):(FabricLoader.getInstance().isModLoaded("cobblemon")?72:12),y=12;
            FantasyPanel.frame(context,x,y,panelWidth,35+visible*rowHeight);
            context.drawText(client.textRenderer,Text.translatable("fantasyhub.hud.level",(int)number("level")),x+9,y+7,0xffebd49e,false);
            int row=0;String[] resources={"health","mana","stamina","magic"};int[] colors={0xffa53138,0xff3578b6,0xffa79035,0xff8858a6};
            for(int i=0;i<resources.length;i++)if(options.visible(resources[i])){String r=resources[i];FantasyPanel.bar(context,x+9,y+24+row++*rowHeight,panelWidth-18,12,number(r),number(r+"Max"),colors[i],r);}
            int experienceY=y+26+visible*rowHeight;
            context.fill(x+9,experienceY,x+panelWidth-9,experienceY+3,0xff302720);
            int experienceWidth=(int)((panelWidth-18)*Math.max(0,Math.min(1,number("nextLevel")<=0?0:number("experience")/number("nextLevel"))));
            context.fill(x+9,experienceY,x+9+experienceWidth,experienceY+3,0xffe6bb55);
            context.getMatrices().pop();
            AbilityHud.render(context);
        });
    }
    public static JsonObject state(){return state;}
    public static double number(String name){return state.has(name)?state.get(name).getAsDouble():0;}
    public static String string(String name){return state.has(name)?state.get(name).getAsString():"";}
    public static void send(String action,String id,int slot){if(ClientPlayNetworking.canSend(Protocol.Action.ID))ClientPlayNetworking.send(new Protocol.Action(action,id,slot));}
    public static Text name(String raw){return Text.literal(raw.replaceAll("[&§][0-9a-fk-orA-FK-OR]",""));}
    static void saveOptions(){options.enabled=hudEnabled;options.scale=hudScale;options.save();}
    public static Text skillName(String id){
        var skills=state.getAsJsonArray("skills");if(skills!=null)for(var raw:skills){var row=raw.getAsJsonObject();if(id.equals(row.get("id").getAsString()))return name(row.get("name").getAsString());}
        return Text.translatable("fantasyhub.skill.unavailable");
    }
}
