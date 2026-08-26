package vn.svframe.svframelib.version.api;

import java.util.UUID;

public interface GameProfile {
    String getTextureValue();
    UUID getUniqueId();
    String getName();

    static GameProfile of(UUID uniqueId, String textureValue) {
        return new GameProfile() {
            @Override public String getTextureValue() { return textureValue; }
            @Override public UUID getUniqueId() { return uniqueId; }
            @Override public String getName() { return ""; }
            @Override public String toString() { return "GameProfile{" + uniqueId + "}"; }
        };
    }
}
