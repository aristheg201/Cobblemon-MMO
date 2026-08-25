package io.lumine.mythic.lib.rpg;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.rpg.provided.PlaceholderClassModule;
import vn.svframe.mythiclibfabric.runtime.RpgProfileRegistry;
public interface ClassModule {
    String getClass(MMOPlayerData data);
    static ClassModule from(String name){if(name!=null&&name.contains("%"))return new PlaceholderClassModule(name);return data->RpgProfileRegistry.mergeOrDefault(data.getUniqueId()).playerClass();}
}
