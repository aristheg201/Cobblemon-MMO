package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Vanilla server-side party selector for /fusion; no integration client mod is required. */
public final class FusionPartyGui {
    private final FusionService fusions;
    public FusionPartyGui(FusionService fusions) { this.fusions = fusions; }

    public void open(ServerPlayerEntity player) {
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        SimpleInventory inventory = new SimpleInventory(9);
        Map<Integer, UUID> pokemonBySlot = new HashMap<>();
        for (int i = 0; i < Math.min(6, party.size()); i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null) continue;
            boolean battling = pokemon.getEntity() != null && pokemon.getEntity().isBattling();
            ItemStack icon = new ItemStack(battling ? Items.BARRIER : Items.PAPER);
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(pokemon.getDisplayName(false).getString()
                    + "  Lv." + pokemon.getLevel() + (battling ? "  [In Battle]" : "")));
            inventory.setStack(i + 1, icon);
            pokemonBySlot.put(i + 1, pokemon.getUuid());
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, owner) -> new Handler(syncId, playerInventory, inventory, pokemonBySlot, player),
                Text.literal("Fusion Dance - Select Pokemon")));
    }

    private final class Handler extends GenericContainerScreenHandler {
        private final Map<Integer, UUID> pokemonBySlot;
        private final UUID owner;
        Handler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, Map<Integer, UUID> pokemonBySlot, ServerPlayerEntity owner) {
            super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, inventory, 1);
            this.pokemonBySlot = Map.copyOf(pokemonBySlot);
            this.owner = owner.getUuid();
        }

        @Override public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity playerEntity) {
            if (!(playerEntity instanceof ServerPlayerEntity player) || !owner.equals(player.getUuid())) return;
            if (slot < 0 || slot >= 9) return;
            UUID pokemonId = pokemonBySlot.get(slot);
            if (pokemonId == null) return;
            Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(pokemonId);
            if (pokemon == null) { player.sendMessage(Text.literal("That Pokemon is no longer in your party."), true); player.closeHandledScreen(); return; }
            FusionService.StartResult result = fusions.startDance(player, pokemon);
            if (!result.success()) player.sendMessage(Text.literal(result.rejection()), true);
            else player.sendMessage(Text.literal("Fusion Dance started with " + result.session().pokemonName() + " for 10 minutes."), true);
            player.closeHandledScreen();
        }

        @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    }
}
