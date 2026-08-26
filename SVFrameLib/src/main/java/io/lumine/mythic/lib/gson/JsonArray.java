package io.lumine.mythic.lib.gson;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class JsonArray extends JsonElement implements Iterable<JsonElement> {
    public JsonArray() { super(new com.google.gson.JsonArray()); }
    JsonArray(com.google.gson.JsonArray delegate) { super(delegate); }
    private com.google.gson.JsonArray array() { return delegate.getAsJsonArray(); }
    public void add(JsonElement element) { array().add(element == null ? com.google.gson.JsonNull.INSTANCE : element.unwrap()); }
    public void add(String value) { array().add(value); }
    public void add(Number value) { array().add(value); }
    public void add(Boolean value) { array().add(value); }
    public void add(Character value) { array().add(value); }
    public JsonElement get(int index) { return wrap(array().get(index)); }
    public JsonElement set(int index, JsonElement value) { return wrap(array().set(index, value.unwrap())); }
    public JsonElement remove(int index) { return wrap(array().remove(index)); }
    public boolean remove(JsonElement value) { return value != null && array().remove(value.unwrap()); }
    public int size() { return array().size(); }
    public boolean isEmpty() { return array().isEmpty(); }
    @Override public Iterator<JsonElement> iterator() {
        Iterator<com.google.gson.JsonElement> iterator = array().iterator();
        return new Iterator<>() {
            public boolean hasNext() { return iterator.hasNext(); }
            public JsonElement next() { return wrap(iterator.next()); }
            public void remove() { iterator.remove(); }
        };
    }
    @Override public void forEach(Consumer<? super JsonElement> action) { Iterable.super.forEach(action); }
    @Override public Spliterator<JsonElement> spliterator() { return Iterable.super.spliterator(); }
}
