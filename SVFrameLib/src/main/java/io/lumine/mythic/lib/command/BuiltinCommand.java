package io.lumine.mythic.lib.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class BuiltinCommand {
    private final boolean hardcoded;
    private final String label;
    private final String configPath;
    private final String description;
    private final String permission;
    private final Function<Map<String, ?>, CommandTreeRoot> builder;
    private final List<String> aliases;
    private final Supplier<Boolean> enabled;
    private final VerboseMode verbose;

    public BuiltinCommand(boolean hardcoded, String label, Function<Map<String, ?>, CommandTreeRoot> builder) { this(hardcoded, label, "ignore", "ignore", builder, null, null, List.of()); }
    public BuiltinCommand(String label, String permission, String description, Function<Map<String, ?>, CommandTreeRoot> builder) { this(false, label, permission, description, builder, null, null, List.of()); }
    public BuiltinCommand(String label, String permission, String description, Function<Map<String, ?>, CommandTreeRoot> builder, List<String> aliases) { this(false, label, permission, description, builder, null, null, aliases); }
    public BuiltinCommand(String label, String permission, String description, Function<Map<String, ?>, CommandTreeRoot> builder, Supplier<Boolean> enabled, List<String> aliases) { this(false, label, permission, description, builder, enabled, null, aliases); }
    public BuiltinCommand(String label, String permission, String description, Function<Map<String, ?>, CommandTreeRoot> builder, Supplier<Boolean> enabled, VerboseMode verbose, List<String> aliases) { this(false, label, permission, description, builder, enabled, verbose, aliases); }
    private BuiltinCommand(boolean hardcoded, String label, String permission, String description, Function<Map<String, ?>, CommandTreeRoot> builder, Supplier<Boolean> enabled, VerboseMode verbose, List<String> aliases) {
        this.hardcoded = hardcoded; this.label = Objects.requireNonNull(label, "label"); this.configPath = label.toLowerCase(Locale.ROOT).replace(' ', '-');
        this.permission = permission; this.description = description; this.builder = Objects.requireNonNull(builder, "builder"); this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
        this.verbose = Objects.requireNonNullElse(verbose, VerboseMode.ALL); this.enabled = enabled == null ? () -> true : enabled;
    }

    public boolean isHardcoded() { return hardcoded; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public String getPermission() { return permission; }
    public List<String> getAliases() { return aliases; }
    public String getConfigPath() { return configPath; }
    public VerboseMode getVerbose() { return verbose; }
    public CommandTreeRoot build(Map<String, ?> config) { if (!isEnabled()) throw new CommandDisabledException(); return builder.apply(config == null ? Map.of() : config); }
    public boolean isEnabled() { return Boolean.TRUE.equals(enabled.get()); }

    public static void initializeAll(CommandDispatcher<ServerCommandSource> dispatcher, Iterable<? extends BuiltinCommand> commands, Map<String, ?> config) {
        for (BuiltinCommand command : commands) if (command != null && command.isEnabled()) command.build(config).register(dispatcher);
    }
    public static void initializeAll(CommandDispatcher<ServerCommandSource> dispatcher, Class<?> holder, Map<String, ?> config) {
        java.util.ArrayList<BuiltinCommand> commands = new java.util.ArrayList<>();
        for (java.lang.reflect.Field field : holder.getDeclaredFields()) {
            if (!BuiltinCommand.class.isAssignableFrom(field.getType())) continue;
            try { field.setAccessible(true); Object value = field.get(null); if (value instanceof BuiltinCommand command) commands.add(command); }
            catch (ReflectiveOperationException exception) { throw new CommandException("Could not initialize command field " + field.getName(), exception); }
        }
        initializeAll(dispatcher, commands, config);
    }
}
