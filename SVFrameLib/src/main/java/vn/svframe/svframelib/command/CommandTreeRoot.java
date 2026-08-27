package vn.svframe.svframelib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Brigadier-backed native replacement for the server-plugin platform CommandTreeRoot. */
public abstract class CommandTreeRoot extends CommandTreeNode {
    private final String name;
    private final String description;
    private final String usageMessage;
    private final List<String> aliases;
    private final String permission;
    private final VerboseMode verbose;
    private boolean onlyPlayers;
    private static volatile BiPredicate<ServerCommandSource, String> permissionResolver = (source, node) -> source.hasPermissionLevel(2);

    public CommandTreeRoot(Map<String, ?> config) {
        this(string(config, "name", "command"), string(config, "description", ""), string(config, "permission", ""), list(config, "aliases"), verbose(config));
    }
    public CommandTreeRoot(BuiltinCommand builtin, Map<String, ?> config) {
        this(builtin.getLabel(), builtin.getDescription(), builtin.getPermission(), builtin.getAliases(), builtin.getVerbose());
    }
    public CommandTreeRoot(String name) { this(name, ""); }
    public CommandTreeRoot(String name, String description) { this(name, description, "", List.of(), VerboseMode.ALL); }
    private CommandTreeRoot(String name, String description, String permission, List<String> aliases, VerboseMode verbose) {
        super(null, Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT));
        this.name = name;
        this.description = Objects.requireNonNullElse(description, "");
        this.permission = Objects.requireNonNullElse(permission, "");
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
        this.verbose = Objects.requireNonNullElse(verbose, VerboseMode.ALL);
        this.usageMessage = "/" + name + " " + formatParameters();
    }

    public VerboseMode getVerbose() { return verbose; }
    public String getPermission() { return permission; }
    protected void setOnlyForPlayers() { onlyPlayers = true; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getAliases() { return aliases; }
    public String getUsageMessage() { return usageMessage; }

    public static void setPermissionResolver(BiPredicate<ServerCommandSource, String> resolver) {
        permissionResolver = Objects.requireNonNull(resolver, "resolver");
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        registerLiteral(dispatcher, name);
        for (String alias : aliases) if (alias != null && !alias.isBlank()) registerLiteral(dispatcher, alias);
    }

    private void registerLiteral(CommandDispatcher<ServerCommandSource> dispatcher, String label) {
        LiteralArgumentBuilder<ServerCommandSource> literal = CommandManager.literal(label)
                .requires(this::permitted)
                .executes(context -> run(context.getSource(), new String[0]));
        literal.then(CommandManager.argument("args", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    String raw = builder.getRemaining();
                    String[] args = splitForCompletion(raw);
                    CommandTreeExplorer explorer = new CommandTreeExplorer(context.getSource(), this, true, args);
                    String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
                    for (String suggestion : explorer.calculateTabCompletion())
                        if (suggestion.toLowerCase(Locale.ROOT).startsWith(prefix)) builder.suggest(suggestion);
                    return builder.buildFuture();
                })
                .executes(context -> run(context.getSource(), split(StringArgumentType.getString(context, "args")))));
        dispatcher.register(literal);
    }

    private boolean permitted(ServerCommandSource source) {
        if (onlyPlayers && !(source.getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity)) return false;
        return permission.isBlank() || permissionResolver.test(source, permission);
    }

    public int run(ServerCommandSource source, String[] args) {
        if (!permitted(source)) {
            source.sendError(Text.literal("You don't have permission to use this command"));
            return 0;
        }
        CommandTreeExplorer explorer = new CommandTreeExplorer(source, this, false, args);
        try {
            CommandTreeNode.CommandResult result = explorer.getNode().execute(explorer, source, args);
            if (result == CommandTreeNode.CommandResult.THROW_USAGE) sendCommandUsage(explorer, explorer.getNode());
            return result == CommandTreeNode.CommandResult.FAILURE ? 0 : 1;
        } catch (CommandException exception) {
            explorer.fail(exception.getMessage());
            return 0;
        } catch (RuntimeException exception) {
            explorer.fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return 0;
        }
    }

    private void sendCommandUsage(CommandTreeExplorer explorer, CommandTreeNode node) {
        for (String usage : node.calculateUsageList()) explorer.verbose("§e/" + usage);
    }

    public List<String> calculateTabCompletion(ServerCommandSource source, String[] args) {
        return new CommandTreeExplorer(source, this, true, args).calculateTabCompletion();
    }

    private static String[] split(String raw) {
        return raw == null || raw.isBlank() ? new String[0] : raw.trim().split("\\s+");
    }
    private static String[] splitForCompletion(String raw) {
        if (raw == null || raw.isEmpty()) return new String[]{""};
        String[] values = raw.split("\\s+", -1);
        return values.length == 0 ? new String[]{""} : values;
    }
    private static String string(Map<String, ?> map, String key, String fallback) {
        Object value = map == null ? null : map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
    private static List<String> list(Map<String, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return List.of();
    }
    private static VerboseMode verbose(Map<String, ?> map) {
        try { return VerboseMode.valueOf(string(map, "verbose", "ALL").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return VerboseMode.ALL; }
    }
}
