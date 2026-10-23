package vn.svframe.svframemmo.cobblemon.gameplay;
import com.cobblemon.mod.common.pokemon.Pokemon;import com.cobblemon.mod.common.Cobblemon;import java.util.*;import net.minecraft.server.network.ServerPlayerEntity;import net.minecraft.util.Identifier;
public final class DexWipeGuard {
 private static DexRescanExclusions exclusions;
 private static synchronized DexRescanExclusions policy(){if(exclusions==null)try{exclusions=new DexRescanExclusions(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("svframeworldboss/pokedex-snap-exclusions.json"));}catch(Exception e){throw new IllegalStateException("Cannot read SNAP dex rescan exclusions",e);}return exclusions;}
 public static void record(ServerPlayerEntity player,Set<Identifier> wiped){
  var ids=new HashSet<UUID>();var storage=Cobblemon.INSTANCE.getStorage();for(var pokemon:storage.getParty(player))if(wiped.contains(pokemon.getSpecies().getResourceIdentifier()))ids.add(pokemon.getUuid());for(var pokemon:storage.getPC(player))if(wiped.contains(pokemon.getSpecies().getResourceIdentifier()))ids.add(pokemon.getUuid());
  try{policy().exclude(player.getUuid(),ids);}catch(Exception e){throw new IllegalStateException("Cannot persist dex rescan exclusions; wipe refused",e);}
 }
 public static Iterable<? extends Pokemon> filter(UUID player,Iterable<? extends Pokemon> pokemon){var out=new ArrayList<Pokemon>();var rule=policy();for(var p:pokemon)if(rule.keeps(player,p.getUuid()))out.add(p);return out;}
}
