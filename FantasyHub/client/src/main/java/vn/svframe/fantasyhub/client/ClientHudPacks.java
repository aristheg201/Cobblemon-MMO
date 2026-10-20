package vn.svframe.fantasyhub.client;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Resource data only. Xaero and the animation loaders remain separate client mods. */
final class ClientHudPacks {
    private ClientHudPacks() {}
    static void register() {
        var container=FabricLoader.getInstance().getModContainer("fantasyhubclient").orElseThrow();
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES).registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener(){
            public Identifier getFabricId(){return Identifier.of("fantasyhub","animation_status");}
            public void reload(net.minecraft.resource.ResourceManager manager){
                boolean emf=FabricLoader.getInstance().isModLoaded("entity_model_features"),etf=FabricLoader.getInstance().isModLoaded("entity_texture_features");
                boolean pack=manager.getResource(Identifier.ofVanilla("emf/cem/player.jem")).isPresent();
                org.slf4j.LoggerFactory.getLogger("FantasyHub/Animations").info("Detailed Animations: EMF={}, ETF={}, player model pack={}; {}",emf,etf,pack,emf&&etf&&pack?"ready":"install separate EMF/ETF client mods and enable SVFrame Detailed Animations");
            }
        });
        for(String id:java.util.List.of("detailed_animations","e19_minimap_icons")) {
            if(!ResourceManagerHelper.registerBuiltinResourcePack(
                    Identifier.of("fantasyhubclient",id),container,
                    Text.literal(id.equals("detailed_animations")
                            ? "SVFrame · Detailed Animations" : "SVFrame · E19 Cobblemon Minimap Icons"),
                    ResourcePackActivationType.DEFAULT_ENABLED))
                throw new IllegalStateException("Missing packaged client resource pack: "+id);
        }
    }
    static com.google.gson.JsonObject observation() {
        var result=new com.google.gson.JsonObject();
        var client=net.minecraft.client.MinecraftClient.getInstance();
        var enabled=new com.google.gson.JsonArray();
        for(var profile:client.getResourcePackManager().getEnabledProfiles())if(profile.getId().startsWith("fantasyhubclient:")||profile.getId().equals("vanilla")||profile.getId().equals("fabric"))enabled.add(profile.getId());
        result.add("enabled",enabled);
        for(String mod:java.util.List.of("entity_model_features","entity_texture_features","xaerominimap"))
            result.addProperty(mod,FabricLoader.getInstance().isModLoaded(mod));
        result.addProperty("playerAnimation",client.getResourceManager().getResource(Identifier.of("minecraft","emf/cem/player.jem")).map(r->r.getPackId()).orElse("missing"));
        result.addProperty("pokemonIcons",client.getResourceManager().getResource(Identifier.of("xaerominimap","entity/icon/definition/cobblemon/pokemon.json")).map(r->r.getPackId()).orElse("missing"));
        if(client.player!=null && client.getEntityRenderDispatcher().getRenderer(client.player) instanceof net.minecraft.client.render.entity.PlayerEntityRenderer renderer) {
            var model=renderer.getModel();result.addProperty("playerModel",model.getClass().getName());
            result.addProperty("rightArmPart",model.rightArm.getClass().getName());
            result.addProperty("rightLegDetailedPose",detailedPose(model.rightLeg));
            result.addProperty("rightArmPitch",model.rightArm.pitch);result.addProperty("rightLegPitch",model.rightLeg.pitch);
        }
        return result;
    }
    private static String detailedPose(net.minecraft.client.model.ModelPart part){
        try{
            Object[] children=(Object[])part.getClass().getMethod("getAllEMFCustomChildren").invoke(part);long hash=1;
            for(Object child:children){var bone=(net.minecraft.client.model.ModelPart)child;for(float value:new float[]{bone.pitch,bone.yaw,bone.roll,bone.pivotX,bone.pivotY,bone.pivotZ,bone.xScale,bone.yScale,bone.zScale})hash=31*hash+Float.floatToIntBits(value);}
            return children.length+":"+Long.toHexString(hash);
        }catch(ReflectiveOperationException error){return "unavailable";}
    }

}
