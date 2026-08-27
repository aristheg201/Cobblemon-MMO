package vn.svframe.svframelib.rpg;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.rpg.provided.PlaceholderLevelModule;
import vn.svframe.svframelib.fabric.runtime.RpgProfileRegistry;
public interface LevelModule {
    int getLevel(MMOPlayerData data);
    static LevelModule from(String name){if(name!=null&&name.contains("%"))return new PlaceholderLevelModule(name);return data->RpgProfileRegistry.mergeOrDefault(data.getUniqueId()).level();}
}
