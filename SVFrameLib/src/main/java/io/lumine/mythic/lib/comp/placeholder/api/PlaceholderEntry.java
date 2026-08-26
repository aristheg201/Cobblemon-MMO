package io.lumine.mythic.lib.comp.placeholder.api;

public interface PlaceholderEntry<T> {
    String getPrefix();
    String getFallback();
    String parse(PlaceholderMetadata<T> metadata);
}
