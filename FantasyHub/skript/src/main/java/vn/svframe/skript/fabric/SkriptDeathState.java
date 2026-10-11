package vn.svframe.skript.fabric;
/** Per-player event decisions, persisted only until their corresponding respawn. */
public interface SkriptDeathState {
 boolean svframe$keepInventory();void svframe$keepInventory(boolean keep);
 boolean svframe$cancelDrops();void svframe$cancelDrops(boolean cancel);
 String svframe$deathMessage();void svframe$deathMessage(String message);
}
