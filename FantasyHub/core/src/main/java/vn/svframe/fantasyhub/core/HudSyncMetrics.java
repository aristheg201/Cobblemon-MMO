package vn.svframe.fantasyhub.core;
import java.util.*;
/** Main-thread diagnostic counters; no player data, logging, disk writes or sampling in the tick path. */
final class HudSyncMetrics {
 static long states,shops,previews,encodes,pokemon,hud,stateNanos,hudNanos,maxStateNanos,maxHudNanos,stateBytes,hudBytes;
 static void reset(){states=shops=previews=encodes=pokemon=hud=stateNanos=hudNanos=maxStateNanos=maxHudNanos=stateBytes=hudBytes=0;}
 static Map<String,Object> snapshot(){var m=new LinkedHashMap<String,Object>();m.put("uiSnapshots",states);m.put("shopViews",shops);m.put("previewGenerations",previews);m.put("itemEncodes",encodes);m.put("pokemonCatalogBuilds",pokemon);m.put("hudUpdates",hud);m.put("uiMs",stateNanos/1e6);m.put("maxUiMs",maxStateNanos/1e6);m.put("hudMs",hudNanos/1e6);m.put("maxHudMs",maxHudNanos/1e6);m.put("uiJsonChars",stateBytes);m.put("hudJsonChars",hudBytes);return m;}
}
