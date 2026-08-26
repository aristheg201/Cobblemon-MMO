package io.lumine.mythic.lib.version;
import io.lumine.mythic.lib.module.MMOPlugin; import java.util.*;
/** Legacy-named update descriptor retained for source compatibility; no Spigot runtime dependency. */
public class SpigotPlugin {
    private final MMOPlugin plugin; private final int id; private String version;
    public SpigotPlugin(int id,MMOPlugin plugin){this.id=id;this.plugin=Objects.requireNonNull(plugin);this.version="1.7.1-fabric";}
    public void checkForUpdate(){plugin.debug("Update check skipped for legacy Spigot resource "+id+" on Fabric");}
    static boolean isOutdated(String local,String remote){int[]a=parseVersion(local),b=parseVersion(remote);for(int i=0;i<Math.max(a.length,b.length);i++){int x=i<a.length?a[i]:0,y=i<b.length?b[i]:0;if(x!=y)return x<y;}return false;}
    static int[] parseVersion(String v){return Arrays.stream((v==null?"":v).replaceAll("[^0-9.]","").split("\\.")).filter(s->!s.isEmpty()).mapToInt(s->{try{return Integer.parseInt(s);}catch(Exception e){return 0;}}).toArray();}
    public String getResourceUrl(){return "https://www.spigotmc.org/resources/"+id+"/";}
}
