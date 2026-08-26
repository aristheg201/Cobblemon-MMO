package vn.svframe.svframelib.gson;

import java.util.Objects;

public class JsonElement {
    final com.google.gson.JsonElement delegate;

    JsonElement(com.google.gson.JsonElement delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    static JsonElement wrap(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonNull()) return new JsonElement(com.google.gson.JsonNull.INSTANCE);
        if (element.isJsonObject()) return new JsonObject(element.getAsJsonObject());
        if (element.isJsonArray()) return new JsonArray(element.getAsJsonArray());
        if (element.isJsonPrimitive()) return new JsonPrimitive(element.getAsJsonPrimitive());
        return new JsonElement(element);
    }

    public boolean isJsonArray() { return delegate.isJsonArray(); }
    public boolean isJsonObject() { return delegate.isJsonObject(); }
    public boolean isJsonPrimitive() { return delegate.isJsonPrimitive(); }
    public boolean isJsonNull() { return delegate.isJsonNull(); }
    public JsonArray getAsJsonArray() { return new JsonArray(delegate.getAsJsonArray()); }
    public JsonObject getAsJsonObject() { return new JsonObject(delegate.getAsJsonObject()); }
    public JsonPrimitive getAsJsonPrimitive() { return new JsonPrimitive(delegate.getAsJsonPrimitive()); }
    public String getAsString() { return delegate.getAsString(); }
    public boolean getAsBoolean() { return delegate.getAsBoolean(); }
    public double getAsDouble() { return delegate.getAsDouble(); }
    public int getAsInt() { return delegate.getAsInt(); }
    public long getAsLong() { return delegate.getAsLong(); }
    public Number getAsNumber() { return delegate.getAsNumber(); }
    public JsonElement deepCopy() { return wrap(delegate.deepCopy()); }
    com.google.gson.JsonElement unwrap() { return delegate; }
    @Override public String toString() { return delegate.toString(); }
    @Override public boolean equals(Object other) { return other instanceof JsonElement element && delegate.equals(element.delegate); }
    @Override public int hashCode() { return delegate.hashCode(); }
}
