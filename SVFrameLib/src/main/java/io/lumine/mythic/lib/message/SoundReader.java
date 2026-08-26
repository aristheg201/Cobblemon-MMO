package io.lumine.mythic.lib.message;
import io.lumine.mythic.lib.util.configobject.ConfigObject; import net.minecraft.registry.Registries; import net.minecraft.server.network.ServerPlayerEntity; import net.minecraft.sound.*; import net.minecraft.util.Identifier;
import java.util.*;
public class SoundReader {
    private final String assetName; private final SoundEvent sound; private final float vol,pitch;
    public SoundReader(String input){this.assetName=input==null?"":input;this.sound=parse(assetName);this.vol=1f;this.pitch=1f;}
    public SoundReader(ConfigObject config){this.assetName=config==null?"":config.stringFb("", "sound","name");this.sound=parse(assetName);this.vol=config==null?1f:config.flpt(1f,"volume","vol");this.pitch=config==null?1f:config.flpt(1f,"pitch");}
    public SoundReader(Map<String,?> config){this(String.valueOf(config==null?"":(config.containsKey("sound")?config.get("sound"):"")));}
    public void play(ServerPlayerEntity player){if(player!=null&&sound!=null)player.playSoundToPlayer(sound,SoundCategory.MASTER,vol,pitch);}
    public static SoundReader fromConfig(Object value){if(value==null)return null;if(value instanceof SoundReader r)return r;if(value instanceof ConfigObject c)return new SoundReader(c);if(value instanceof Map<?,?> m){Map<String,Object>x=new LinkedHashMap<>();m.forEach((k,v)->x.put(String.valueOf(k),v));return new SoundReader(x);}return new SoundReader(String.valueOf(value));}
    private static SoundEvent parse(String raw){if(raw==null||raw.isBlank())return null;String s=raw.trim().toLowerCase(Locale.ROOT).replace('_','.');Identifier id=Identifier.tryParse(s.contains(":")?s:"minecraft:"+s);return id==null?null:Registries.SOUND_EVENT.get(id);}
}
