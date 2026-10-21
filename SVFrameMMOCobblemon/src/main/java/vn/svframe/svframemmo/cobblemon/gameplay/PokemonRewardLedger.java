package vn.svframe.svframemmo.cobblemon.gameplay;

import java.util.*;

/**
 * Event UUIDs are never derived from party EXP callbacks. Server-thread only; bounded replay cache.
 */
public final class PokemonRewardLedger {
  private final Map<String, Boolean> events = new LinkedHashMap<>();

  private boolean claim(String key) {
    if (events.containsKey(key)) return false;
    events.put(key, true);
    if (events.size() > 100_000) events.remove(events.keySet().iterator().next());
    return true;
  }

  public boolean claimVictory(UUID battle, UUID trainer) {
    return claim("battle:" + battle + ":" + trainer);
  }

  public boolean claimDefeat(UUID pokemon) {
    return claim("pokemon:" + pokemon);
  }

  public void markBattlePokemon(UUID pokemon) {
    claimDefeat(pokemon);
  }

  public void clear() {
    events.clear();
  }
}
