package vn.svframe.svframelib.comp.placeholder.api;
public interface PlaceholderEntry<T> {
    String getPrefix(); String getFallback(); String parse(PlaceholderMetadata<T> metadata);
}
