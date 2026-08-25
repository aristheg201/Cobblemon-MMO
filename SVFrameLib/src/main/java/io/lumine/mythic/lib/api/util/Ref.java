package io.lumine.mythic.lib.api.util;

public class Ref<T> {
    private T value;
    public Ref() {}
    public Ref(T value) { this.value = value; }
    public T get() { return value; }
    public T getValue() { return value; }
    public void set(T value) { this.value = value; }
    public void setValue(T value) { this.value = value; }
    public boolean isPresent() { return value != null; }
}
