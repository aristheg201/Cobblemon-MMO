package vn.svframe.svframeitems.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vn.svframe.svframeitems.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Native 1.21.1 item presentation. All visible information is rebuilt from authoritative item state. */
public final class ItemFormatter {
    public void apply(ItemStack stack, ItemDefinition definition, ItemInstance instance, ItemRarity rarity) {
        apply(stack, definition, instance, rarity, instance.stats());
    }

    public void apply(ItemStack stack, ItemDefinition definition, ItemInstance instance, ItemRarity rarity, Collection<ItemStat> effectiveStats) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(effectiveStats, "effectiveStats");

        StringBuilder name = new StringBuilder(definition.displayName());
        if (instance.upgradeLevel() > 0) name.append(" +").append(instance.upgradeLevel());
        stack.set(DataComponentTypes.CUSTOM_NAME, line(name.toString(), Formatting.WHITE));

        List<Text> lore = new ArrayList<>();
        lore.add(line("Rarity: " + rarity.displayName(), Formatting.AQUA));
        lore.add(line("Level: " + instance.itemLevel(), Formatting.GRAY));
        if (instance.upgradeLevel() > 0) lore.add(line("Upgrade: +" + instance.upgradeLevel(), Formatting.GOLD));
        if (definition.setId() != null) lore.add(line("Set: " + definition.setId(), Formatting.LIGHT_PURPLE));
        if (definition.gemColor() != null) lore.add(line("Gem color: " + definition.gemColor(), Formatting.RED));

        if (!effectiveStats.isEmpty()) {
            lore.add(Text.empty());
            lore.add(line("Stats", Formatting.YELLOW));
            Map<StatKey, Double> totals = new LinkedHashMap<>();
            for (ItemStat stat : effectiveStats) totals.merge(new StatKey(stat.stat(), stat.type()), stat.value(), Double::sum);
            for (Map.Entry<StatKey,Double> entry : totals.entrySet()) {
                String suffix = entry.getKey().type() == vn.svframe.svframelib.fabric.runtime.NativeStatEngine.ModifierType.FLAT ? "" : " [" + entry.getKey().type().name() + "]";
                lore.add(line("  " + entry.getKey().stat() + ": " + number(entry.getValue()) + suffix, Formatting.GREEN));
            }
        }

        if (!instance.sockets().isEmpty()) {
            lore.add(Text.empty());
            lore.add(line("Sockets", Formatting.YELLOW));
            for (int index = 0; index < instance.sockets().size(); index++) {
                SocketState socket = instance.sockets().get(index);
                EmbeddedGem gem = socket.gem();
                String value = gem == null ? "empty" : gem.definitionId() + " +" + gem.upgradeLevel();
                lore.add(line("  #" + index + " " + socket.color() + ": " + value, gem == null ? Formatting.DARK_GRAY : Formatting.RED));
            }
        }

        if (!definition.abilities().isEmpty()) {
            lore.add(Text.empty());
            lore.add(line("Abilities", Formatting.YELLOW));
            for (ItemAbility ability : definition.abilities()) lore.add(line("  " + ability.trigger().name() + ": " + ability.skill(), Formatting.LIGHT_PURPLE));
        }

        stack.set(DataComponentTypes.LORE, new LoreComponent(List.copyOf(lore)));
    }

    private static Text line(String value, Formatting color) {
        return Text.literal(value).setStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) return String.valueOf(value);
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        return decimal.toPlainString();
    }

    private record StatKey(String stat, vn.svframe.svframelib.fabric.runtime.NativeStatEngine.ModifierType type) {
        private StatKey { stat = Objects.requireNonNull(stat, "stat"); type = Objects.requireNonNull(type, "type"); }
    }
}
