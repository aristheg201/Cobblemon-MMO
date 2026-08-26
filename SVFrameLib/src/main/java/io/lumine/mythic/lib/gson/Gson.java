package io.lumine.mythic.lib.gson;

public class Gson {
    private final com.google.gson.Gson delegate;
    public Gson() { this.delegate = new com.google.gson.Gson(); }
    public String toJson(Object src) {
        if (src instanceof JsonElement element) return delegate.toJson(element.unwrap());
        return delegate.toJson(src);
    }
    public String toJson(JsonElement src) { return delegate.toJson(src == null ? null : src.unwrap()); }
    public <T> T fromJson(String json, Class<T> type) {
        try {
            if (type == JsonElement.class) return type.cast(JsonParser.parseString(json));
            if (type == JsonObject.class) return type.cast(JsonParser.parseString(json).getAsJsonObject());
            if (type == JsonArray.class) return type.cast(JsonParser.parseString(json).getAsJsonArray());
            if (type == JsonPrimitive.class) return type.cast(JsonParser.parseString(json).getAsJsonPrimitive());
            return delegate.fromJson(json, type);
        } catch (com.google.gson.JsonSyntaxException exception) { throw new JsonSyntaxException(exception.getMessage(), exception); }
    }
    public <T> T fromJson(JsonElement json, Class<T> type) {
        if (json == null) return null;
        return delegate.fromJson(json.unwrap(), type);
    }
    public JsonElement toJsonTree(Object src) { return JsonElement.wrap(delegate.toJsonTree(src)); }
}
