package io.lumine.mythic.lib.rpg;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.rpg.provided.PlaceholderLevelModule;
import vn.svframe.mythiclibfabric.runtime.RpgProfileRegistry;
public interface LevelModule {
    int getLevel(MMOPlayerData data);
    static LevelModule from(String name){if(name!=null&&name.contains("%"))return new PlaceholderLevelModule(name);return data->RpgProfileRegistry.mergeOrDefault(data.getUniqueId()).level();}
}
