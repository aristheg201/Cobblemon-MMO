package vn.svframe.svframelib.rpg.provided;
import vn.svframe.svframelib.api.player.MMOPlayerData;import vn.svframe.svframelib.rpg.ClassModule;import vn.svframe.svframelib.fabric.runtime.NativePlaceholderRegistry;
public class PlaceholderClassModule implements ClassModule {private final String placeholder;public PlaceholderClassModule(String placeholder){this.placeholder=placeholder;}public String getClass(MMOPlayerData data){String p=placeholder;if(p.startsWith("%")&&p.endsWith("%")&&p.length()>2)p=p.substring(1,p.length()-1);return NativePlaceholderRegistry.resolve(data.getUniqueId(),p);}}
