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
            for(String manifest:java.util.List.of("dragonborn-models.json","hunter-models.json","additions-models.json","riders-models.json")) {
                try(var input=FantasyHubClient.class.getResourceAsStream("/assets/fantasyhub/"+manifest)){
                    if(input==null)throw new IllegalStateException("Missing class model manifest "+manifest);
                    var models=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
                    for(var model:models)context.addModels(net.minecraft.util.Identifier.of(model.getAsString()));
                }catch(java.io.IOException failure){throw new IllegalStateException("Cannot load authored class models",failure);}
            }
        });
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type,renderer,helper,context)->{
            if(renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer player){helper.register(new DragonbornHatFeature(player));helper.register(new HunterArmorFeature(player));helper.register(new KabutoEquipmentFeature(player));helper.register(new RiderArmorFeature(player));helper.register(new RiderWeaponFeature(player));}
        });
        if(FabricLoader.getInstance().isModLoaded("accessories"))try{for(var belt:java.util.List.of(Protocol.zecterBelt,Protocol.agitoBelt,Protocol.zoldaBelt,Protocol.ryukiBelt,Protocol.knightBelt,Protocol.decadeBelt))Class.forName("io.wispforest.accessories.api.client.AccessoriesRendererRegistry").getMethod("registerNoRenderer",net.minecraft.item.Item.class).invoke(null,belt);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot register the native Zecter belt renderer",failure);}
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(Protocol.kabutoHat,(stack,mode,matrices,vertices,light,overlay)->{if(mode==net.minecraft.client.render.model.json.ModelTransformationMode.GUI){var client=MinecraftClient.getInstance();var icon=client.getBakedModelManager().getModel(net.minecraft.util.Identifier.of("fantasyhub","item/additions/kabuto_helmet_icon"));matrices.push();matrices.translate(.5,.5,.5);client.getItemRenderer().renderItem(stack,mode,false,matrices,vertices,light,overlay,icon);matrices.pop();}else KabutoHatRenderer.render(matrices,vertices,light,overlay);});
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(Protocol.visualBone,(stack,mode,matrices,vertices,light,overlay)->{
            var data=stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
            if(data==null)return;var model=data.copyNbt().getString("SVFRAME_ITEM_MODEL");if(!java.util.Set.of("fantasyhub:item/riders/magnugiga","fantasyhub:item/riders/dragreder","fantasyhub:item/riders/dragranzer","fantasyhub:item/riders/darkwing").contains(model))return;
            matrices.push();matrices.translate(.5,.5,.5);matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180));RiderModels.display(model.substring("fantasyhub:item/riders/".length()),matrices,vertices,light,false);matrices.pop();
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register((entity,world)->{KabutoVisuals.remove(entity.getId());RiderVisuals.remove(entity.getId());});
        RiderEffects.install();
        ClientPlayNetworking.registerGlobalReceiver(Protocol.RiderFx.ID,(payload,context)->RiderEffects.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(Protocol.Rider.ID,(payload,context)->RiderVisuals.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(Protocol.KabutoFx.ID,(payload,context)->KabutoEffects.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(Protocol.KabutoCue.ID,(payload,context)->KabutoVisuals.cue(payload));
        ClientPlayNetworking.registerGlobalReceiver(Protocol.Kabuto.ID,(payload,context)->KabutoVisuals.accept(payload));
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player,world,hand)->{var stack=player.getStackInHand(hand);if(!world.isClient||!stack.isOf(Protocol.kunaiGun))return net.minecraft.util.TypedActionResult.pass(stack);var state=KabutoVisuals.state(player.getId());if(state==null||(state.form()==0&&!player.isSneaking()&&player.getOffHandStack().isOf(net.minecraft.item.Items.SHIELD)))return net.minecraft.util.TypedActionResult.pass(stack);return net.minecraft.util.TypedActionResult.success(stack);});
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{state=new JsonObject();castingState.reset();KabutoVisuals.clear();RiderVisuals.clear();KabutoEffects.clear();RiderEffects.clear();});
        var menu=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fantasyhub.menu",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"category.fantasyhub"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            ticks++;KabutoEffects.tick();RiderEffects.tick();
            while(menu.wasPressed())if(client.player!=null&&state.has("schema"))client.setScreen(new HubScreen(HubScreen.View.PROFILE));
            if(ticks%10==0&&client.player!=null&&state.has("schema")&&"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))){
                JsonObject o=new JsonObject();o.addProperty("clientHealth",client.player.getHealth());o.add("kabutoEffects",KabutoEffects.observation());o.add("riderEffects",RiderEffects.observation());var rider=RiderVisuals.state(client.player.getId());if(rider!=null){var rs=new JsonObject();rs.addProperty("form",rider.form());rs.addProperty("force",rider.force());rs.addProperty("remaining",rider.remaining());o.add("rider",rs);}
                JsonObject equipment=new JsonObject();for(var slot:java.util.List.of(net.minecraft.entity.EquipmentSlot.HEAD,net.minecraft.entity.EquipmentSlot.CHEST,net.minecraft.entity.EquipmentSlot.LEGS,net.minecraft.entity.EquipmentSlot.FEET)){var stack=client.player.getEquippedStack(slot);var data=stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);equipment.addProperty(slot.name(),data==null?"":data.copyNbt().getString("SVFRAME_ITEM_MODEL"));}o.add("equippedModels",equipment);
                var kb=KabutoVisuals.state(client.player.getId());if(kb!=null){var visual=new JsonObject();visual.addProperty("form",kb.form());visual.addProperty("clock",kb.clock());visual.addProperty("hyper",kb.hyper());visual.addProperty("energy",kb.energy());visual.addProperty("gauge",kb.gauge());o.add("kabuto",visual);}o.addProperty("clientMaxHealth",client.player.getMaxHealth());
                o.addProperty("hurtTime",client.player.hurtTime);o.addProperty("maxHurtTime",client.player.maxHurtTime);
                o.addProperty("stateHealth",number("health"));o.addProperty("stateMaxHealth",number("healthMax"));o.addProperty("clientTick",ticks);
                o.addProperty("clientVanillaLevel",client.player.experienceLevel);o.addProperty("clientVanillaProgress",client.player.experienceProgress);o.addProperty("clientVanillaTotal",client.player.totalExperience);
                o.addProperty("rpgExperienceRendered",CompactHud.rpgExperience());
                if(state.has("experienceDisplay"))o.add("experienceDisplay",state.get("experienceDisplay").deepCopy());
                com.google.gson.JsonArray players=new com.google.gson.JsonArray();for(var tracked:client.world.getPlayers())players.add(tracked.getName().getString());o.add("trackedPlayers",players);
                if(state.has("casting"))o.add("clientCasting",QaPresentationSnapshot.casting(state.getAsJsonObject("casting")));
                o.add("displayedSkillSlots",QaPresentationSnapshot.slots(SkillBarPresentation.slots(state)));
                o.addProperty("renderedSkillSlots",AbilityHud.lastRenderedSlots);
                o.addProperty("damagePackets",ClientFeedbackQa.damagePackets);o.addProperty("hurtCameraFrames",ClientFeedbackQa.hurtCameraFrames);
                o.addProperty("riderSounds",ClientFeedbackQa.riderSounds);o.addProperty("gunSounds",ClientFeedbackQa.gunSounds);o.addProperty("kunaiSounds",ClientFeedbackQa.kunaiSounds);
                if(o.toString().length()>4096&&o.has("clientCasting"))o.getAsJsonObject("clientCasting").remove("slots");
                if(o.toString().length()>4096)o.getAsJsonObject("riderEffects").remove("phases");
                if(o.toString().length()<=4096&&ClientPlayNetworking.canSend(Protocol.Observation.ID))ClientPlayNetworking.send(new Protocol.Observation(o.toString()));
                observed=true;
            }
        });
        HudRenderCallback.EVENT.register((context,counter)->{
            var client=MinecraftClient.getInstance();
            AbilityHud.lastRenderedSlots=0;
            if(!hudEnabled||client.player==null||client.options.hudHidden||(client.currentScreen!=null&&!(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen))||!state.has("schema"))return;
            AbilityHud.render(context);
            KabutoVisuals.hud(context);
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
