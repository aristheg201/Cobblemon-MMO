package io.lumine.mythic.lib.version;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public enum VMaterial {
    BLAST_FURNACE(Items.BLAST_FURNACE),
    CAMPFIRE(Items.CAMPFIRE),
    SMOKER(Items.SMOKER),
    SMITHING_TABLE(Items.SMITHING_TABLE),
    GRASS_BLOCK(Items.GRASS_BLOCK),
    SPYGLASS(Items.SPYGLASS);

    private final Item item;
    VMaterial(Item item) { this.item = item; }
    public Item get() { return item; }
    public ItemStack toItem() { return new ItemStack(item); }
}
