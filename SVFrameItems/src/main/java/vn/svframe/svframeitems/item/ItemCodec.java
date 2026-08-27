package vn.svframe.svframeitems.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import vn.svframe.svframeitems.model.*;

import java.util.*;

public final class ItemCodec {
    private static final String ROOT = "SVFrameItems";
    private ItemCodec() {}

    public static boolean isSVFrameItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        return custom != null && custom.copyNbt().contains(ROOT, NbtElement.COMPOUND_TYPE);
    }

    public static void write(ItemStack stack, ItemInstance instance) {
        Objects.requireNonNull(stack, "stack"); Objects.requireNonNull(instance, "instance");
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, root -> {
            NbtCompound data = new NbtCompound();
            data.putString("instance-id", instance.instanceId().toString());
            data.putString("definition-id", instance.definitionId());
            data.putString("type-id", instance.typeId());
            data.putString("rarity-id", instance.rarityId());
            data.putInt("item-level", instance.itemLevel());
            data.putInt("upgrade-level", instance.upgradeLevel());
            data.putInt("definition-revision", instance.definitionRevision());
            data.putLong("seed", instance.seed());
            data.putLong("state-revision", instance.stateRevision());
            NbtList stats = new NbtList();
            for (ItemStat stat : instance.stats()) stats.add(stat(stat));
            data.put("stats", stats);
            NbtList sockets = new NbtList();
            for (SocketState socket : instance.sockets()) sockets.add(socket(socket));
            data.put("sockets", sockets);
            if (!instance.metadata().isEmpty()) data.put("metadata", metadata(instance.metadata()));
            root.put(ROOT, data);
            root.putString("SVFRAMEITEMS_ITEM_TYPE", instance.typeId());
            root.putString("SVFRAMEITEMS_ITEM_ID", instance.definitionId());
            root.putInt("SVFRAMEITEMS_ITEM_LEVEL", instance.itemLevel());
            root.putInt("SVFRAMEITEMS_UPGRADE_LEVEL", instance.upgradeLevel());
        });
    }

    public static Optional<ItemInstance> read(ItemStack stack) {
        if (!isSVFrameItem(stack)) return Optional.empty();
        try {
            NbtCompound root = Objects.requireNonNull(stack.get(DataComponentTypes.CUSTOM_DATA)).copyNbt();
            NbtCompound data = root.getCompound(ROOT);
            UUID instance = UUID.fromString(data.getString("instance-id"));
            List<ItemStat> stats = new ArrayList<>();
            NbtList statList = data.getList("stats", NbtElement.COMPOUND_TYPE);
            for (int i=0;i<statList.size();i++) stats.add(readStat(statList.getCompound(i)));
            List<SocketState> sockets = new ArrayList<>();
            NbtList socketList = data.getList("sockets", NbtElement.COMPOUND_TYPE);
            for (int i=0;i<socketList.size();i++) sockets.add(readSocket(socketList.getCompound(i)));
            Map<String,String> metadata = data.contains("metadata", NbtElement.COMPOUND_TYPE) ? readMetadata(data.getCompound("metadata")) : Map.of();
            return Optional.of(new ItemInstance(instance, data.getString("definition-id"), data.getString("type-id"), data.getString("rarity-id"),
                    data.getInt("item-level"), data.getInt("upgrade-level"), data.getInt("definition-revision"), data.getLong("seed"), data.getLong("state-revision"), stats, sockets, metadata));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<String> metadata(ItemStack stack, String key) {
        String normalized = ItemInstance.normalizeMetadataKey(key);
        return read(stack).map(ItemInstance::metadata).map(value -> value.get(normalized));
    }

    public static boolean setMetadata(ItemStack stack, String key, String value) {
        Optional<ItemInstance> current = read(stack);
        if (current.isEmpty()) return false;
        write(stack, current.get().withMetadata(key, value));
        return true;
    }

    private static NbtCompound metadata(Map<String,String> values) {
        NbtCompound nbt = new NbtCompound();
        values.forEach(nbt::putString);
        return nbt;
    }
    private static Map<String,String> readMetadata(NbtCompound nbt) {
        Map<String,String> values = new LinkedHashMap<>();
        for (String key : nbt.getKeys()) values.put(key, nbt.getString(key));
        return Map.copyOf(values);
    }
    private static NbtCompound stat(ItemStat stat) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", stat.stat()); nbt.putDouble("value", stat.value()); nbt.putString("type", stat.type().name()); return nbt;
    }
    private static ItemStat readStat(NbtCompound nbt) {
        return new ItemStat(nbt.getString("id"), nbt.getDouble("value"), NativeStatEngine.ModifierType.valueOf(nbt.getString("type")));
    }
    private static NbtCompound socket(SocketState socket) {
        NbtCompound nbt = new NbtCompound(); nbt.putString("color", socket.color());
        if (socket.gem() != null) {
            EmbeddedGem gem = socket.gem(); NbtCompound gemNbt = new NbtCompound();
            gemNbt.putString("instance-id", gem.instanceId().toString()); gemNbt.putString("definition-id", gem.definitionId()); gemNbt.putString("type-id", gem.typeId()); gemNbt.putString("rarity-id", gem.rarityId());
            gemNbt.putInt("item-level", gem.itemLevel()); gemNbt.putInt("upgrade-level", gem.upgradeLevel()); gemNbt.putInt("definition-revision", gem.definitionRevision()); gemNbt.putLong("seed", gem.seed()); gemNbt.putLong("state-revision", gem.stateRevision()); gemNbt.putString("color", gem.color());
            NbtList stats = new NbtList(); for (ItemStat stat : gem.stats()) stats.add(stat(stat)); gemNbt.put("stats", stats);
            if (!gem.metadata().isEmpty()) gemNbt.put("metadata", metadata(gem.metadata()));
            nbt.put("gem", gemNbt);
        }
        return nbt;
    }
    private static SocketState readSocket(NbtCompound nbt) {
        if (!nbt.contains("gem", NbtElement.COMPOUND_TYPE)) return new SocketState(nbt.getString("color"), null);
        NbtCompound gemNbt = nbt.getCompound("gem"); List<ItemStat> stats = new ArrayList<>(); NbtList list = gemNbt.getList("stats", NbtElement.COMPOUND_TYPE);
        for (int i=0;i<list.size();i++) stats.add(readStat(list.getCompound(i)));
        Map<String,String> metadata = gemNbt.contains("metadata", NbtElement.COMPOUND_TYPE) ? readMetadata(gemNbt.getCompound("metadata")) : Map.of();
        EmbeddedGem gem = new EmbeddedGem(UUID.fromString(gemNbt.getString("instance-id")), gemNbt.getString("definition-id"), gemNbt.getString("type-id"), gemNbt.getString("rarity-id"), gemNbt.getInt("item-level"), gemNbt.getInt("upgrade-level"), gemNbt.getInt("definition-revision"), gemNbt.getLong("seed"), gemNbt.getLong("state-revision"), gemNbt.getString("color"), stats, metadata);
        return new SocketState(nbt.getString("color"), gem);
    }
}
