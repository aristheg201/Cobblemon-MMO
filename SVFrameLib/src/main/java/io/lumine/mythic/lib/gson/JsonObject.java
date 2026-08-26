package io.lumine.mythic.lib.gson;

import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class JsonObject extends JsonElement {
    public JsonObject() { super(new com.google.gson.JsonObject()); }
    JsonObject(com.google.gson.JsonObject delegate) { super(delegate); }
    private com.google.gson.JsonObject object() { return delegate.getAsJsonObject(); }
    public void add(String property, JsonElement value) { object().add(property, value == null ? com.google.gson.JsonNull.INSTANCE : value.unwrap()); }
    public JsonElement remove(String property) { com.google.gson.JsonElement value = object().remove(property); return value == null ? null : wrap(value); }
    public void addProperty(String property, String value) { object().addProperty(property, value); }
    public void addProperty(String property, Number value) { object().addProperty(property, value); }
    public void addProperty(String property, Boolean value) { object().addProperty(property, value); }
    public void addProperty(String property, Character value) { object().addProperty(property, value); }
    public boolean has(String memberName) { return object().has(memberName); }
    public JsonElement get(String memberName) { com.google.gson.JsonElement value = object().get(memberName); return value == null ? null : wrap(value); }
    public JsonObject getAsJsonObject(String memberName) { com.google.gson.JsonObject value = object().getAsJsonObject(memberName); return value == null ? null : new JsonObject(value); }
    public JsonArray getAsJsonArray(String memberName) { com.google.gson.JsonArray value = object().getAsJsonArray(memberName); return value == null ? null : new JsonArray(value); }
    public JsonPrimitive getAsJsonPrimitive(String memberName) { com.google.gson.JsonPrimitive value = object().getAsJsonPrimitive(memberName); return value == null ? null : new JsonPrimitive(value); }
    public Set<String> keySet() { return object().keySet(); }
    public int size() { return object().size(); }
    public Set<java.util.Map.Entry<String, JsonElement>> entrySet() {
        Set<java.util.Map.Entry<String, JsonElement>> result = new LinkedHashSet<>();
        object().entrySet().forEach(entry -> result.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), wrap(entry.getValue()))));
        return result;
    }
}
