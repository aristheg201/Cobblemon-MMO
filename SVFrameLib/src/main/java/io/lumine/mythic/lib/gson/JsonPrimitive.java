package io.lumine.mythic.lib.gson;

public class JsonPrimitive extends JsonElement {
    public JsonPrimitive(Boolean value) { super(new com.google.gson.JsonPrimitive(value)); }
    public JsonPrimitive(Number value) { super(new com.google.gson.JsonPrimitive(value)); }
    public JsonPrimitive(String value) { super(new com.google.gson.JsonPrimitive(value)); }
    public JsonPrimitive(Character value) { super(new com.google.gson.JsonPrimitive(value)); }
    JsonPrimitive(com.google.gson.JsonPrimitive delegate) { super(delegate); }
    public boolean isBoolean() { return delegate.getAsJsonPrimitive().isBoolean(); }
    public boolean isNumber() { return delegate.getAsJsonPrimitive().isNumber(); }
    public boolean isString() { return delegate.getAsJsonPrimitive().isString(); }
}
