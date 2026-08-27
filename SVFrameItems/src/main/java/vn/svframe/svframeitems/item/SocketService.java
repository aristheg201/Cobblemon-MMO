package vn.svframe.svframeitems.item;

import net.minecraft.item.ItemStack;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.*;

public final class SocketService {
    public enum Status { SUCCESS, NOT_AN_ITEM, NOT_A_GEM, NO_COMPATIBLE_SOCKET, INVALID_DEFINITION }
    public record InsertResult(Status status, ItemStack target, ItemStack gemRemainder, int socketIndex) { public boolean success(){return status==Status.SUCCESS;} }
    public record UnsocketResult(Status status, ItemStack target, ItemStack gem, int socketIndex) { public boolean success(){return status==Status.SUCCESS;} }

    private final SVFrameItemsRegistry registry; private final ItemGenerator generator;
    public SocketService(SVFrameItemsRegistry registry, ItemGenerator generator) { this.registry=Objects.requireNonNull(registry); this.generator=Objects.requireNonNull(generator); }

    public InsertResult insert(ItemStack target, ItemStack gemStack) {
        Optional<ItemInstance> targetInstance = ItemCodec.read(target); Optional<ItemInstance> gemInstance = ItemCodec.read(gemStack);
        if (targetInstance.isEmpty() || gemInstance.isEmpty()) return new InsertResult(Status.NOT_AN_ITEM, target.copy(), gemStack.copy(), -1);
        ItemDefinition gemDefinition = registry.item(gemInstance.get().definitionId());
        if (gemDefinition == null) return new InsertResult(Status.INVALID_DEFINITION, target.copy(), gemStack.copy(), -1);
        if (!gemDefinition.isGem()) return new InsertResult(Status.NOT_A_GEM, target.copy(), gemStack.copy(), -1);
        List<SocketState> sockets = new ArrayList<>(targetInstance.get().sockets());
        int index = -1; for (int i=0;i<sockets.size();i++) if (sockets.get(i).accepts(gemDefinition.gemColor())) { index=i; break; }
        if (index < 0) return new InsertResult(Status.NO_COMPATIBLE_SOCKET, target.copy(), gemStack.copy(), -1);
        sockets.set(index, sockets.get(index).insert(EmbeddedGem.from(gemInstance.get(), gemDefinition.gemColor())));
        ItemStack rebuilt = generator.rebuild(targetInstance.get().withSockets(sockets));
        ItemStack remainder = gemStack.copy(); remainder.decrement(1);
        return new InsertResult(Status.SUCCESS, rebuilt, remainder, index);
    }

    public UnsocketResult unsocket(ItemStack target, int socketIndex) {
        Optional<ItemInstance> instance = ItemCodec.read(target);
        if (instance.isEmpty()) return new UnsocketResult(Status.NOT_AN_ITEM, target.copy(), ItemStack.EMPTY, -1);
        if (socketIndex < 0 || socketIndex >= instance.get().sockets().size()) return new UnsocketResult(Status.NO_COMPATIBLE_SOCKET, target.copy(), ItemStack.EMPTY, -1);
        List<SocketState> sockets = new ArrayList<>(instance.get().sockets()); SocketState socket = sockets.get(socketIndex);
        if (socket.gem() == null) return new UnsocketResult(Status.NO_COMPATIBLE_SOCKET, target.copy(), ItemStack.EMPTY, socketIndex);
        ItemStack gem = generator.rebuild(socket.gem().toItemInstance()); sockets.set(socketIndex, socket.clear());
        return new UnsocketResult(Status.SUCCESS, generator.rebuild(instance.get().withSockets(sockets)), gem, socketIndex);
    }
}
