package vn.svframe.svframemmo.cobblemon.fusion;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Native administrative command for creating Potara items from the live integration config. */
public final class PotaraCommands {
    private static final Map<String, FusionTier> TIERS = tiers();

    private PotaraCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("potara")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("give")
                        .then(argument("tier", StringArgumentType.word())
                                .suggests((context, builder) -> suggestTiers(builder))
                                .executes(context -> give(context, context.getSource().getPlayerOrThrow(), 1))
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> give(context, EntityArgumentType.getPlayer(context, "player"), 1))
                                        .then(argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> give(context,
                                                        EntityArgumentType.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))));
    }

    private static int give(CommandContext<ServerCommandSource> context, ServerPlayerEntity target, int amount) {
        String requested = StringArgumentType.getString(context, "tier");
        FusionTier tier = TIERS.get(normalize(requested));
        if (tier == null) {
            context.getSource().sendError(Text.literal("Unknown Potara tier '" + requested + "'. Use basic, level2, advancement or god."));
            return 0;
        }

        IntegrationConfig.PotaraItem spec = SVFrameMMOCobblemon.config().potara.byTier().get(tier);
        if (spec == null) {
            context.getSource().sendError(Text.literal("Potara tier is not configured: " + tier.name()));
            return 0;
        }

        Item item = Registries.ITEM.get(spec.itemId());
        ItemStack stack = new ItemStack(item, amount);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(spec.customModelData));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName(tier)));

        target.getInventory().insertStack(stack);
        if (!stack.isEmpty()) target.dropItem(stack, false);

        String message = "Gave " + amount + "x " + displayName(tier) + " to " + target.getGameProfile().getName()
                + " (" + spec.itemId() + ", CustomModelData=" + spec.customModelData + ").";
        context.getSource().sendFeedback(() -> Text.literal(message), true);
        if (context.getSource().getEntity() != target)
            target.sendMessage(Text.literal("You received " + amount + "x " + displayName(tier) + "."), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestTiers(SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        TIERS.keySet().stream().filter(id -> id.startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static Map<String, FusionTier> tiers() {
        LinkedHashMap<String, FusionTier> out = new LinkedHashMap<>();
        out.put("basic", FusionTier.BASIC);
        out.put("level2", FusionTier.LEVEL_2);
        out.put("level_2", FusionTier.LEVEL_2);
        out.put("advancement", FusionTier.ADVANCEMENT);
        out.put("god", FusionTier.GOD);
        return Map.copyOf(out);
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String displayName(FusionTier tier) {
        return switch (tier) {
            case BASIC -> "Potara Earrings";
            case LEVEL_2 -> "Potara Earrings Level 2";
            case ADVANCEMENT -> "Potara Earrings of Advancement";
            case GOD -> "Potara Earrings of God";
            case DANCE -> throw new IllegalArgumentException("Fusion Dance does not use a Potara item");
        };
    }
}
