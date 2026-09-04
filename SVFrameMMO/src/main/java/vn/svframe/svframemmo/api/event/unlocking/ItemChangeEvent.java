package vn.svframe.svframemmo.api.event.unlocking;

import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Objects;

/** Base API value for persistent skill/slot/item unlock state changes. */
public abstract class ItemChangeEvent {
    private final PlayerData data;
    private final String itemKey;

    protected ItemChangeEvent(PlayerData data, String itemKey) {
        this.data = Objects.requireNonNull(data, "data");
        this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
    }
    public PlayerData getData() { return data; }
    public String getItemKey() { return itemKey; }
    public String getItemTypeId() {
        int split = itemKey.indexOf(':');
        return split < 0 ? itemKey : itemKey.substring(0, split);
    }
    public String getItemId() {
        int split = itemKey.indexOf(':');
        return split < 0 || split + 1 >= itemKey.length() ? "" : itemKey.substring(split + 1);
    }
}
