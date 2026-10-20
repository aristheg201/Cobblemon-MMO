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
        for(var profile:client.getResourcePackManager().getEnabledProfiles())enabled.add(profile.getId());
        result.add("enabled",enabled);
        for(String mod:java.util.List.of("entity_model_features","entity_texture_features","xaerominimap"))
            result.addProperty(mod,FabricLoader.getInstance().isModLoaded(mod));
        result.addProperty("playerAnimation",client.getResourceManager().getResource(Identifier.of("minecraft","emf/cem/player.jem")).map(r->r.getPackId()).orElse("missing"));
        result.addProperty("pokemonIcons",client.getResourceManager().getResource(Identifier.of("xaerominimap","entity/icon/definition/cobblemon/pokemon.json")).map(r->r.getPackId()).orElse("missing"));
        if(client.player!=null && client.getEntityRenderDispatcher().getRenderer(client.player) instanceof net.minecraft.client.render.entity.PlayerEntityRenderer renderer) {
            var model=renderer.getModel();result.addProperty("playerModel",model.getClass().getName());
            result.addProperty("rightArmPart",model.rightArm.getClass().getName());
            result.addProperty("rightArmPitch",model.rightArm.pitch);result.addProperty("rightLegPitch",model.rightLeg.pitch);
        }
        return result;
    }
}
