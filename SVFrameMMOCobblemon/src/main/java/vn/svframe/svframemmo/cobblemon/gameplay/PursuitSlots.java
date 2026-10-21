package vn.svframe.svframemmo.cobblemon.gameplay;

import java.util.*;

/** Global per-player limit shared by all dimensions. */
public final class PursuitSlots {
  private final Map<UUID, UUID> owners = new HashMap<>();

  public boolean claim(UUID pokemon, UUID player, int maximum) {
    if (player.equals(owners.get(pokemon))) return true;
    if (owners.values().stream().filter(player::equals).count() >= maximum) return false;
    owners.put(pokemon, player);
    return true;
  }

  public void release(UUID pokemon) {
    owners.remove(pokemon);
  }

  public void clear() {
    owners.clear();
  }
}
