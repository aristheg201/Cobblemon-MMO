package vn.svframe.fantasyhub.client;

import com.google.gson.JsonObject;

/** The casting channel owns its state once received; catalog arrivals cannot overwrite newer F updates. */
final class CastingState {
    private JsonObject latest;
    void accept(JsonObject casting){latest=casting.deepCopy();}
    void mergeInto(JsonObject state){if(latest!=null)latest.entrySet().forEach(entry->state.add(entry.getKey(),entry.getValue().deepCopy()));}
    void reset(){latest=null;}
}
