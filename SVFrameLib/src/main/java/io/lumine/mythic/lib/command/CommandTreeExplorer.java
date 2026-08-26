package io.lumine.mythic.lib.command;

import io.lumine.mythic.lib.command.argument.Argument;
import io.lumine.mythic.lib.command.argument.ArgumentParseException;
import io.lumine.mythic.lib.command.argument.MissingArgumentException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class CommandTreeExplorer {
    private final String[] args;
    private final ServerCommandSource sender;
    private final CommandTreeRoot root;
    private CommandTreeNode current;
    private int argCount;

    public CommandTreeExplorer(ServerCommandSource sender, CommandTreeRoot root, boolean tabCompletion, String[] args) {
        this.current = Objects.requireNonNull(root, "root");
        this.root = root;
        this.args = args == null ? new String[0] : args;
        this.sender = Objects.requireNonNull(sender, "sender");
        for (int i = 0; i < this.args.length; i++) {
            String raw = this.args[i];
            if (argCount == 0 && current.hasChild(raw) && (!tabCompletion || i < this.args.length - 1)) current = current.getChild(raw);
            else argCount++;
        }
    }

    public CommandTreeRoot getCommand() { return root; }
    public CommandTreeNode.CommandResult fail(String message) { if (message != null) verbose("§c" + message); return CommandTreeNode.CommandResult.FAILURE; }
    public CommandTreeNode.CommandResult success(String message) { if (message != null) verbose("§e" + message); return CommandTreeNode.CommandResult.SUCCESS; }

    public void verbose(String message) {
        if (message == null) return;
        boolean player = sender.getEntity() instanceof ServerPlayerEntity;
        switch (root.getVerbose()) {
            case ALL -> sender.sendFeedback(() -> Text.literal(message), false);
            case PLAYER -> { if (player) sender.sendFeedback(() -> Text.literal(message), false); }
            case CONSOLE -> { if (!player) sender.sendFeedback(() -> Text.literal(message), false); }
            case REDIRECT_TO_CONSOLE -> sender.getServer().sendMessage(Text.literal(message));
            case NONE -> { }
        }
    }

    public ServerCommandSource getSender() { return sender; }

    public <T> T parse(Argument<T> argument) { return parse(argument, null); }

    public <T> T parse(Argument<T> argument, Function<CommandTreeExplorer, T> dynamicFallback) {
        int index = current.getLevel() + argument.getIndex();
        if (args.length <= index) {
            Function<CommandTreeExplorer, T> fallback = dynamicFallback != null ? dynamicFallback : argument.getFallback();
            if (fallback == null) throw new MissingArgumentException(argument);
            try { return fallback.apply(this); }
            catch (CommandException exception) { throw exception; }
            catch (Exception exception) { throw new ArgumentParseException("Could not resolve fallback for '" + argument.getKey() + "'", exception); }
        }
        return argument.parse(this, args[index]);
    }

    public CommandTreeNode getNode() { return current; }
    public String[] getArguments() { return args; }
    public List<String> calculateTabCompletion() { return current.calculateTabCompletion(this, Math.max(0, argCount - 1)); }
}
