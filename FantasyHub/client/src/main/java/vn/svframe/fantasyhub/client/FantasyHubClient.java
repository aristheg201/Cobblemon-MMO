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
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import vn.svframe.fantasyhub.protocol.Protocol;
import java.nio.file.*;

public final class FantasyHubClient implements ClientModInitializer {
    private static JsonObject state=new JsonObject();
    private static final CastingState castingState=new CastingState();
    public static boolean hudEnabled=true;
    public static float hudScale=1;
    static PresentationOptions options;
    private long ticks;
    private boolean observed;
    @Override public void onInitializeClient(){
        Protocol.install();
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context->{
            try(var input=FantasyHubClient.class.getResourceAsStream("/assets/fantasyhub/dragonborn-models.json")){
                if(input==null)throw new IllegalStateException("Missing Dragonborn model manifest");
                var models=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
                for(var model:models)context.addModels(net.minecraft.util.Identifier.of(model.getAsString()));
            }catch(java.io.IOException failure){throw new IllegalStateException("Cannot load authored Dragonborn models",failure);}
        });
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type,renderer,helper,context)->{
            if(renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer player)helper.register(new DragonbornHatFeature(player));
        });
        options=PresentationOptions.load();hudEnabled=options.enabled;hudScale=options.scale;
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register((stack,tint)->net.minecraft.component.type.DyedColorComponent.getColor(stack,0xffffff),Protocol.visualBone);
        ClientConfigurationNetworking.registerGlobalReceiver(Protocol.Handshake.ID,(challenge,context)->{
            String diagnostic=AssetIntegrity.diagnose();
            if(challenge.version()!=Protocol.VERSION)diagnostic="server protocol mismatch: "+challenge.version();
            else if(!Protocol.ASSET_REVISION.equals(challenge.assets()))diagnostic="server asset revision mismatch: "+challenge.assets();
            String version=FabricLoader.getInstance().getModContainer("fantasyhubclient").orElseThrow().getMetadata().getVersion().getFriendlyString();
            if(diagnostic.length()>512)diagnostic=diagnostic.substring(0,512);
            org.slf4j.LoggerFactory.getLogger("FantasyHub/Handshake").info("Configuration challenge: client={}, protocol={}, assets={}, result={}",version,challenge.version(),challenge.assets(),diagnostic.isEmpty()?"verified":diagnostic);
            context.responseSender().sendPacket(new Protocol.Handshake(Protocol.VERSION,Protocol.ASSET_REVISION,version,diagnostic));
        });
        ClientPlayNetworking.registerGlobalReceiver(Protocol.State.ID,(payload,context)->{
            JsonObject next=JsonParser.parseString(payload.json()).getAsJsonObject();
            if(next.get("schema").getAsInt()!=Protocol.VERSION)return;
            castingState.mergeInto(next);state=next;observed=false;
            if(context.client().currentScreen instanceof HubScreen screen)screen.refresh();
            if(context.client().currentScreen instanceof PokemonShopScreen screen)screen.refresh();
            if(context.client().currentScreen instanceof ClassSelectionScreen screen)screen.refresh();
        });
        ClientPlayNetworking.registerGlobalReceiver(Protocol.Casting.ID,(payload,context)->{
            castingState.accept(JsonParser.parseString(payload.json()).getAsJsonObject());castingState.mergeInto(state);observed=false;
        });
        ClientPlayNetworking.registerGlobalReceiver(Protocol.Open.ID,(payload,context)->{
            if(payload.screen().equals("close")){var screen=context.client().currentScreen;if(screen instanceof HubScreen||screen instanceof ClassSelectionScreen||screen instanceof PokemonShopScreen)context.client().setScreen(null);}
            else if(payload.screen().equals("classes"))context.client().setScreen(new ClassSelectionScreen());
            else if(payload.screen().equals("pokemon_shop"))context.client().setScreen(new PokemonShopScreen());
            else try{context.client().setScreen(new HubScreen(HubScreen.View.valueOf(payload.screen().toUpperCase(java.util.Locale.ROOT))));}catch(IllegalArgumentException ignored){}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{state=new JsonObject();castingState.reset();});
        var menu=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fantasyhub.menu",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"category.fantasyhub"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            ticks++;
            while(menu.wasPressed())if(client.player!=null&&state.has("schema"))client.setScreen(new HubScreen(HubScreen.View.PROFILE));
            if(ticks%10==0&&client.player!=null&&state.has("schema")&&"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))){
                JsonObject o=new JsonObject();o.addProperty("clientHealth",client.player.getHealth());o.addProperty("clientMaxHealth",client.player.getMaxHealth());
                o.addProperty("hurtTime",client.player.hurtTime);o.addProperty("maxHurtTime",client.player.maxHurtTime);
                o.addProperty("stateHealth",number("health"));o.addProperty("stateMaxHealth",number("healthMax"));o.addProperty("clientTick",ticks);
                com.google.gson.JsonArray players=new com.google.gson.JsonArray();for(var tracked:client.world.getPlayers())players.add(tracked.getName().getString());o.add("trackedPlayers",players);
                if(state.has("casting"))o.add("clientCasting",state.get("casting").deepCopy());
                o.add("displayedSkillSlots",SkillBarPresentation.slots(state).deepCopy());
                o.addProperty("renderedSkillSlots",AbilityHud.lastRenderedSlots);
                o.addProperty("damagePackets",ClientFeedbackQa.damagePackets);o.addProperty("hurtCameraFrames",ClientFeedbackQa.hurtCameraFrames);
                if(ClientPlayNetworking.canSend(Protocol.Observation.ID))ClientPlayNetworking.send(new Protocol.Observation(o.toString()));
                observed=true;
            }
        });
        HudRenderCallback.EVENT.register((context,counter)->{
            var client=MinecraftClient.getInstance();
            AbilityHud.lastRenderedSlots=0;
            if(!hudEnabled||client.player==null||client.options.hudHidden||(client.currentScreen!=null&&!(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen))||!state.has("schema"))return;
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
