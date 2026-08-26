package io.lumine.mythic.lib.version;

import io.lumine.mythic.lib.util.lang3.Validate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Objects;
import java.util.Random;

public class OreDrops {
    private final Item item;
    private final int min;
    private final int max;
    private static final Random RANDOM = new Random();

    public OreDrops(Item item) { this(item, 1, 1); }
    public OreDrops(Item item, int min, int max) {
        this.item = Objects.requireNonNull(item, "Item cannot be null");
        Validate.isTrue(min > 0, "Min amount must be positive");
        Validate.isTrue(max >= min, "Max amount must be higher than min amount");
        this.min = min;
        this.max = max;
    }

    public ItemStack generate(int fortuneLevel) {
        Validate.isTrue(fortuneLevel >= 0, "Fortune level must be positive");
        int base = min == max ? min : RANDOM.nextInt(min, max + 1);
        return new ItemStack(item, base * rollFortuneCoefficient(fortuneLevel));
    }

    private int rollFortuneCoefficient(int fortune) {
        if (fortune == 0) return 1;
        if (RANDOM.nextDouble() < 2d / (2d + fortune)) return 1;
        return RANDOM.nextInt(2, fortune + 2);
    }
}
