package io.lumine.mythic.lib.rpg.provided;
import io.lumine.mythic.lib.api.player.MMOPlayerData;import io.lumine.mythic.lib.rpg.ClassModule;import vn.svframe.mythiclibfabric.runtime.NativePlaceholderRegistry;
public class PlaceholderClassModule implements ClassModule {private final String placeholder;public PlaceholderClassModule(String placeholder){this.placeholder=placeholder;}public String getClass(MMOPlayerData data){String p=placeholder;if(p.startsWith("%")&&p.endsWith("%")&&p.length()>2)p=p.substring(1,p.length()-1);return NativePlaceholderRegistry.resolve(data.getUniqueId(),p);}}
