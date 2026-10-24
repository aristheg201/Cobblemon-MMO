package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.util.*;

/** Rejects obsolete domain patches before parsing. A reconnect resets the generation fence. */
final class SyncInbox {
  private long generation = -1;
  private boolean resync;
  boolean resyncRequested(){boolean needed=resync;resync=false;return needed;}

  private record Version(long revision, long sequence) {}

  private final Map<String, Version> domains = new HashMap<>();

  boolean apply(
      JsonObject state,
      long session,
      long revision,
      long sequence,
      String domain,
      String json,
      long sentAt,
      long now) {
    if (session < generation) return false;
    var previous = session == generation ? domains.get(domain) : null;
    if (previous != null && (revision < previous.revision || sequence <= previous.sequence))
      return false;
    var patch = JsonParser.parseString(json).getAsJsonObject();
    if (session > generation) {
      generation = session;
      domains.clear();
      state.entrySet().clear();
    }
    if(patch.has("pokemonShopPage")) {
      var page=patch.remove("pokemonShopPage").getAsJsonObject();int start=page.get("start").getAsInt();
      var rows=page.getAsJsonArray("rows");var previousRows=state.getAsJsonArray("pokemonShop");
      if(start!=0&&(previousRows==null||previousRows.size()!=start)){resync=true;return false;}
      var combined=new JsonArray();if(start!=0)combined.addAll(previousRows);combined.addAll(rows);patch.add("pokemonShop",combined);
    }
    domains.put(domain, new Version(revision, sequence));
    state.addProperty("schema", 5);
    state.addProperty("_syncClockOffset", now - sentAt);
    ClientSyncClock.offset = now - sentAt;
    patch.entrySet().forEach(e -> state.add(e.getKey(), e.getValue()));
    if (patch.has("combatState")) {
      var dynamic = patch.getAsJsonObject("combatState");
      var defs = state.getAsJsonObject("skillDefinitions");
      var casting = dynamic.deepCopy();
      var rows = new JsonArray();
      for (var raw : dynamic.getAsJsonArray("slots")) {
        var values = raw.getAsJsonObject();
        String id = values.get("id").getAsString();
        var row =
            defs != null && defs.has(id) ? defs.getAsJsonObject(id).deepCopy() : new JsonObject();
        values.entrySet().forEach(e -> row.add(e.getKey(), e.getValue()));
        row.addProperty("cooldownRemaining", SkillBarPresentation.remaining(row));
        rows.add(row);
      }
      casting.add("slots", rows);
      state.add("casting", casting);
    }
    // A cached menu tree can arrive after the independent combat domain.
    // Overlay only dynamic deadlines, leaving skill definitions/progression intact.
    if((patch.has("combatState")||patch.has("skills")) && state.has("skills") && state.has("combatState")) {
      var cooldowns=new HashMap<String,JsonObject>();
      for(var raw:state.getAsJsonObject("combatState").getAsJsonArray("slots")) {
        var slot=raw.getAsJsonObject();cooldowns.put(slot.get("id").getAsString(),slot);
      }
      for(var raw:state.getAsJsonArray("skills")) {
        var skill=raw.getAsJsonObject();var latest=cooldowns.get(skill.get("id").getAsString());
        if(latest!=null&&latest.has("cooldownEnd"))skill.add("cooldownEnd",latest.get("cooldownEnd"));
      }
    }
    if (state.has("shopStatus")
        && state.has("fantasyShop")
        && (patch.has("shopStatus") || patch.has("fantasyShop"))) {
      var shop = state.getAsJsonObject("fantasyShop");
      state
          .getAsJsonObject("shopStatus")
          .entrySet()
          .forEach(e -> shop.add(e.getKey(), e.getValue()));
    }
    return true;
  }

  void reset() {
    generation = -1;
    resync=false;
    domains.clear();
    ClientSyncClock.offset = 0;
  }
}
