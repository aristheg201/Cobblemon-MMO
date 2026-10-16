package vn.svframe.svframeitems.runtime;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.ItemCodec;
import vn.svframe.svframelib.config.YamlLite;
import java.io.IOException;

/** Native TAN enchantment; works on existing stacks and accessory providers without replacing equipment. */
public final class ThermalTuningRuntime {
    public static final Identifier ID = Identifier.of("toughasnails", "thermal_tuning");
    private static boolean enabled = true;
    private ThermalTuningRuntime() {}
    public static boolean isThermal(net.minecraft.enchantment.Enchantment enchantment) {
        var server = SVFrameItems.server();
        return server != null && server.getRegistryManager().get(RegistryKeys.ENCHANTMENT).get(ID) == enchantment;
    }
    public static void reload() throws IOException {
        var config = YamlLite.map(YamlLite.parse(vn.svframe.svframeitems.config.DefaultFiles.root().resolve("compatibility.yml")));
        EnchantmentPolicy.configure(config);
        enabled = !Boolean.FALSE.equals(YamlLite.map(config.get("tough-as-nails")).get("automatic-thermal-tuning"));
    }
    public static boolean equipment(ItemStack stack) {
        return ItemCodec.read(stack).map(i -> {
            var definition = SVFrameItems.registry().item(i.definitionId());
            var type = definition == null ? null : SVFrameItems.registry().type(definition.typeId());
            return type != null && !type.allowedSlots().isEmpty();
        }).orElse(false);
    }
    public static void apply(ItemStack stack) {
        var server = SVFrameItems.server();
        if (!enabled || server == null || !FabricLoader.getInstance().isModLoaded("toughasnails") || !equipment(stack)) return;
        var enchantment = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(ID);
        if (enchantment.isPresent() && EnchantmentHelper.getLevel(enchantment.get(), stack) == 0) {
            stack.addEnchantment(enchantment.get(), 1);
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA,stack,nbt->nbt.putBoolean("SVFRAME_INTRINSIC_THERMAL",true));
        }
        if (intrinsic(stack)) {
            var data=stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
            boolean muted=data.getBoolean("SVFRAME_THERMAL_GLINT_MUTED");
            if (onlyThermalTuning(stack) && (!stack.contains(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)||muted)) {
                stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,false);
                if(!muted)NbtComponent.set(DataComponentTypes.CUSTOM_DATA,stack,nbt->nbt.putBoolean("SVFRAME_THERMAL_GLINT_MUTED",true));
            } else if (!onlyThermalTuning(stack)&&muted) {
                stack.remove(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
                NbtComponent.set(DataComponentTypes.CUSTOM_DATA,stack,nbt->nbt.remove("SVFRAME_THERMAL_GLINT_MUTED"));
            }
        }
    }
    public static boolean intrinsic(ItemStack stack) {
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);
        return equipment(stack)&&data!=null&&data.copyNbt().getBoolean("SVFRAME_INTRINSIC_THERMAL");
    }
    public static boolean onlyThermalTuning(ItemStack stack) {
        var enchants = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        return !enchants.isEmpty() && enchants.getEnchantments().stream()
                .allMatch(e -> e.getKey().map(k -> k.getValue().equals(ID)).orElse(false));
    }
    public static boolean hasThermalTuning(ItemStack stack) {
        var enchants = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        return equipment(stack) && enchants.getEnchantments().stream()
                .anyMatch(e -> e.getKey().map(k -> k.getValue().equals(ID)).orElse(false) && enchants.getLevel(e) > 0);
    }
    public static boolean protectedPlayer(net.minecraft.entity.player.PlayerEntity player) {
        for (var stack : player.getArmorItems()) if (hasThermalTuning(stack)) return true;
        for (var stack : player.getHandItems()) if (hasThermalTuning(stack)) return true;
        if (player instanceof ServerPlayerEntity serverPlayer)
            for (var provider : EquipmentProviderRegistry.providers()) {
                var equipment = provider.equipment(serverPlayer);
                if (equipment != null) for (var item : equipment)
                    if (item != null && hasThermalTuning(item.stack())) return true;
            }
        return false;
    }
}
