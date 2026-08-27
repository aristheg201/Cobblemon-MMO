package vn.svframe.svframelib.rpg;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.rpg.provided.PlaceholderClassModule;
import vn.svframe.svframelib.fabric.runtime.RpgProfileRegistry;
public interface ClassModule {
    String getClass(MMOPlayerData data);
    static ClassModule from(String name){if(name!=null&&name.contains("%"))return new PlaceholderClassModule(name);return data->RpgProfileRegistry.mergeOrDefault(data.getUniqueId()).playerClass();}
}
