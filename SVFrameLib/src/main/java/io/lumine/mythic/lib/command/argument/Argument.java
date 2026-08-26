package io.lumine.mythic.lib.command.argument;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.util.lang3.Validate;
import io.lumine.mythic.lib.version.Attributes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Native argument definition retaining MythicLib 1.7.1 immutable argument semantics. */
public class Argument<T> {
    private final String key;
    private final int indexInNode;
    private final BiConsumer<CommandTreeExplorer, List<String>> autoComplete;
    private final BiFunction<CommandTreeExplorer, String, T> parser;
    private final Function<CommandTreeExplorer, T> fallback;

    public static final Argument<LivingEntity> LIVING_ENTITY = new Argument<>("entity", Argument::players, (explorer, input) -> {
        Entity entity = resolveEntity(explorer, input);
        Arguments.notNull(entity, "Could not find entity '" + input + "'");
        Arguments.isTrue(entity instanceof LivingEntity, "Entity is not living");
        return (LivingEntity) entity;
    });
    public static final Argument<ServerPlayerEntity> PLAYER = new Argument<>("player", Argument::players, (explorer, input) -> {
        ServerPlayerEntity player = explorer.getSender().getServer().getPlayerManager().getPlayer(input);
        Arguments.notNull(player, "Could not find player '" + input + "'");
        return player;
    });
    public static final Argument<ServerPlayerEntity> PLAYER_OR_SENDER = new Argument<>("player", Argument::players, PLAYER.parser, explorer -> {
        Entity entity = explorer.getSender().getEntity();
        if (entity instanceof ServerPlayerEntity player) return player;
        throw new ArgumentParseException("Please provide a player");
    });
    public static final Argument<Integer> AMOUNT_INT = new Argument<>("amount", Argument::amounts, (explorer, input) -> parseInt(input));
    public static final Argument<SkillHandler<?>> SKILL_HANDLER = new Argument<>("skill_id", (explorer, out) -> MythicLib.plugin.getSkills().getHandlers().forEach(handler -> out.add(handler.getId())), (explorer, input) -> {
        SkillHandler<?> handler = MythicLib.plugin.getSkills().getHandler(UtilityMethods.enumName(input));
        if (handler == null) throw new ArgumentParseException("Could not find skill handler '" + input + "'");
        return handler;
    });
    public static final Argument<String> STRING = new Argument<>("string", (explorer, out) -> { }, (explorer, input) -> input);
    public static final Argument<Double> AMOUNT_DOUBLE = new Argument<>("amount", Argument::amounts, (explorer, input) -> parseDouble(input));
    public static final Argument<Long> DURATION_TICKS = new Argument<>("duration", (explorer, out) -> { for (int i = 1; i <= 10; i += 2) out.add(Integer.toString(i * 20)); }, (explorer, input) -> parseLong(input));
    public static final Argument<Item> MATERIAL = new Argument<>("material", (explorer, out) -> Registries.ITEM.getIds().stream().limit(256).forEach(id -> out.add(id.toString())), (explorer, input) -> {
        Identifier id = Identifier.tryParse(input.contains(":") ? input.toLowerCase(Locale.ROOT) : "minecraft:" + input.toLowerCase(Locale.ROOT));
        if (id == null || !Registries.ITEM.containsId(id)) throw new ArgumentParseException("Could not find material '" + input + "'");
        return Registries.ITEM.get(id);
    });
    public static final Argument<String> STAT = new Argument<>("stat", (explorer, out) -> out.addAll(MythicLib.plugin.getStats().getRegisteredStats()), (explorer, input) -> UtilityMethods.enumName(input));
    public static final Argument<Boolean> BOOLEAN = new Argument<>("boolean",
            (explorer, out) -> { out.add("true"); out.add("false"); },
            (explorer, input) -> {
                if (input.equalsIgnoreCase("true")) return Boolean.TRUE;
                if (input.equalsIgnoreCase("false")) return Boolean.FALSE;
                throw new ArgumentParseException("Expected true or false, got '" + input + "'");
            });
    public static final Argument<RegistryEntry<EntityAttribute>> VANILLA_ATTRIBUTE = new Argument<>("attribute", (explorer, out) -> Attributes.getAll().forEach(attribute -> out.add(Attributes.name(attribute))), (explorer, input) -> {
        try { return Attributes.fromName(input); }
        catch (IllegalArgumentException exception) { throw new ArgumentParseException("Could not find attribute '" + input + "'", exception); }
    });
    public static final String DEFAULT_MODIFIER_KEY = "default";
    public static final Argument<String> MODIFIER_KEY = new Argument<>("key", (explorer, out) -> { out.add("default"); out.add("plugin_name"); out.add("passive_skill_name"); }, (explorer, input) -> UtilityMethods.enumName(input), explorer -> DEFAULT_MODIFIER_KEY);
    public static final Argument<String> COOLDOWN_CURRENT = new Argument<>("cooldown_key", (explorer, out) -> {
        Entity entity = explorer.getSender().getEntity();
        if (entity instanceof ServerPlayerEntity player && io.lumine.mythic.lib.api.player.MMOPlayerData.has(player)) {
            var keys = io.lumine.mythic.lib.api.player.MMOPlayerData.get(player).getCooldownMap().getCooldownKeys();
            if (keys.isEmpty()) out.add("my_cooldown_key"); else out.addAll(keys);
        } else out.add("my_cooldown_key");
    }, (explorer, input) -> UtilityMethods.enumName(input));
    @SuppressWarnings("rawtypes") public static final Argument PLAYER_OPTIONAL = PLAYER.withDynamicFallback();
    @SuppressWarnings("rawtypes") public static final Argument AMOUNT_OPTIONAL = AMOUNT_INT.withDynamicFallback();

    public Argument(String key, BiConsumer<CommandTreeExplorer, List<String>> autoComplete, BiFunction<CommandTreeExplorer, String, T> parser) {
        this(key, -1, autoComplete, parser, null);
    }
    public Argument(String key, BiConsumer<CommandTreeExplorer, List<String>> autoComplete, BiFunction<CommandTreeExplorer, String, T> parser, Function<CommandTreeExplorer, T> fallback) {
        this(key, -1, autoComplete, parser, fallback);
    }
    private Argument(String key, int index, BiConsumer<CommandTreeExplorer, List<String>> autoComplete, BiFunction<CommandTreeExplorer, String, T> parser, Function<CommandTreeExplorer, T> fallback) {
        this.key = Objects.requireNonNull(key, "key");
        this.indexInNode = index;
        this.autoComplete = autoComplete == null ? (e, l) -> { } : autoComplete;
        this.parser = parser;
        this.fallback = fallback;
    }

    public String getKey() { return key; }
    public boolean isOptional() { return fallback != null; }
    public Function<CommandTreeExplorer, T> getFallback() { return fallback; }
    public T parse(CommandTreeExplorer explorer, String value) { Validate.isTrue(parser != null, "No parser provided"); return parser.apply(explorer, value); }
    public String format() { return (isOptional() ? "(" : "<") + key + (isOptional() ? ")" : ">"); }
    public int getIndex() { Validate.isTrue(indexInNode != -1, "Index not set"); return indexInNode; }
    public Argument<T> withIndex(int index) { return new Argument<>(key, index, autoComplete, parser, fallback); }
    public Argument<T> withAutoComplete(BiConsumer<CommandTreeExplorer, List<String>> value) { return new Argument<>(key, indexInNode, value, parser, fallback); }
    public Argument<T> withKey(String value) { return new Argument<>(value, indexInNode, autoComplete, parser, fallback); }
    public Argument<T> withFallback(Function<CommandTreeExplorer, T> value) { return new Argument<>(key, indexInNode, autoComplete, parser, value); }
    public Argument<T> withDynamicFallback() { return withFallback(explorer -> { throw new ArgumentParseException("No dynamic fallback provided"); }); }
    public Argument<T> empty() { return new Argument<>(key, indexInNode, (e, l) -> { }, (e, s) -> null, e -> null); }
    public void autoComplete(CommandTreeExplorer explorer, List<String> list) { autoComplete.accept(explorer, list); }

    public static <E extends Enum<E>> Argument<E> choices(String key, Class<E> type) {
        List<E> values = Arrays.asList(type.getEnumConstants());
        return new Argument<>(key, (e, out) -> values.forEach(value -> out.add(value.name().toLowerCase(Locale.ROOT))), (e, input) -> {
            try { return Enum.valueOf(type, UtilityMethods.enumName(input)); }
            catch (IllegalArgumentException exception) { throw new ArgumentParseException("Invalid choice '" + input + "'", exception); }
        });
    }
    public static Argument<String> choices(String key, String... choices) {
        List<String> values = Arrays.asList(choices);
        return new Argument<>(key, (e, out) -> out.addAll(values), (e, input) -> {
            if (!values.contains(input)) throw new ArgumentParseException("Invalid choice '" + input + "'");
            return input;
        });
    }
    public Argument(String key, BiConsumer<CommandTreeExplorer, List<String>> autoComplete) { this(key, autoComplete, null, explorer -> null); }
    public Argument(String key, boolean ignored, BiConsumer<CommandTreeExplorer, List<String>> autoComplete) { this(key, autoComplete, null, explorer -> null); }
    public Argument(String key, Boolean ignored, BiConsumer<CommandTreeExplorer, List<String>> autoComplete) { this(key, autoComplete, null, explorer -> null); }

    private static void players(CommandTreeExplorer explorer, List<String> out) {
        explorer.getSender().getServer().getPlayerManager().getPlayerList().forEach(player -> out.add(player.getName().getString()));
    }
    private static void amounts(CommandTreeExplorer explorer, List<String> out) {
        for (int i = 1; i <= 10; i++) out.add(Integer.toString(i));
    }
    private static Integer parseInt(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new ArgumentParseException("Invalid integer '" + value + "'", e); }
    }
    private static Double parseDouble(String value) {
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { throw new ArgumentParseException("Invalid number '" + value + "'", e); }
    }
    private static Long parseLong(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { throw new ArgumentParseException("Invalid duration '" + value + "'", e); }
    }
    private static Entity resolveEntity(CommandTreeExplorer explorer, String input) {
        ServerPlayerEntity player = explorer.getSender().getServer().getPlayerManager().getPlayer(input);
        if (player != null) return player;
        try {
            java.util.UUID uuid = java.util.UUID.fromString(input);
            for (var world : explorer.getSender().getServer().getWorlds()) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) return entity;
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }
}
