package io.lumine.mythic.lib.comp.placeholder.api;

public class PlaceholderMetadata<T> {
    public final T playerData;
    public final int argIndex;
    public final String placeholderInput;

    PlaceholderMetadata(T playerData, String placeholderInput, int argIndex) {
        this.playerData = playerData;
        this.placeholderInput = placeholderInput;
        this.argIndex = argIndex;
    }

    public static <T> PlaceholderMetadata<T> of(T playerData, String placeholderInput, int argIndex) {
        return new PlaceholderMetadata<>(playerData, placeholderInput, argIndex);
    }

    public String params() {
        if (placeholderInput == null || argIndex < 0 || argIndex >= placeholderInput.length()) return "";
        return placeholderInput.substring(argIndex);
    }
}
