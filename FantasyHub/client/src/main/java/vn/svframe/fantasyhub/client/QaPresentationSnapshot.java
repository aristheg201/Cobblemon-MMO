package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.util.Set;

/** Bounded diagnostics never echo the full skill catalog back to the server. */
final class QaPresentationSnapshot {
    private static final Set<String> SLOT_FIELDS=Set.of("id","inputSlot","slot","level","cooldown","cooldownRemaining","remaining","ready","castable","binding");
    static JsonArray slots(JsonArray source) {
        JsonArray result=new JsonArray();if(source==null)return result;
        for(var raw:source){if(result.size()>=9)break;if(!raw.isJsonObject())continue;JsonObject out=new JsonObject();
            for(var field:raw.getAsJsonObject().entrySet())if(SLOT_FIELDS.contains(field.getKey())&&field.getValue().isJsonPrimitive()&&field.getValue().toString().length()<=160)out.add(field.getKey(),field.getValue().deepCopy());
            result.add(out);
        }
        return result;
    }
    static JsonObject casting(JsonObject source) {
        JsonObject result=new JsonObject();
        for(String field:Set.of("active","mode","selectedSlot","selectedInputSlot"))if(source.has(field)&&source.get(field).isJsonPrimitive()&&source.get(field).toString().length()<=160)result.add(field,source.get(field).deepCopy());
        result.add("slots",slots(source.getAsJsonArray("slots")));return result;
    }
}
