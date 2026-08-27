package vn.svframe.svframelib.command;

import vn.svframe.svframelib.command.argument.Argument;
import vn.svframe.svframelib.util.lang3.Validate;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Native command-tree node retaining SVFrameLib 1.7.1 traversal/usage semantics. */
public abstract class CommandTreeNode {
    private final String id;
    private final CommandTreeNode parent;
    private final int heightInTree;
    private final Map<String, CommandTreeNode> children = new LinkedHashMap<>();
    private final List<Argument<?>> arguments = new ArrayList<>();
    protected static final Random RANDOM = new Random();

    public CommandTreeNode(CommandTreeNode parent, String id) {
        this.id = Validate.notBlank(id, "Command node id cannot be blank");
        this.parent = parent;
        this.heightInTree = parent == null ? 0 : parent.getLevel() + 1;
    }

    public String getId() { return id; }
    public String getPath() { return (hasParent() ? parent.getPath() + " " : "") + getId(); }
    public Collection<CommandTreeNode> getChildren() { return java.util.List.copyOf(children.values()); }
    public boolean hasParameters() { return !arguments.isEmpty(); }
    public List<Argument<?>> getArguments() { return java.util.List.copyOf(arguments); }

    public <T> Argument<T> addArgument(Argument<T> argument) {
        Validate.notNull(argument, "Argument cannot be null");
        if (!arguments.isEmpty() && arguments.get(arguments.size() - 1).isOptional()) {
            Validate.isTrue(argument.isOptional(), "Cannot add non-optional argument after an optional one");
        }
        Argument<T> indexed = argument.withIndex(arguments.size());
        arguments.add(indexed);
        return indexed;
    }

    public boolean hasParent() { return parent != null; }
    public boolean hasChild(String id) { return id != null && children.containsKey(id.toLowerCase(Locale.ROOT)); }
    public CommandTreeNode getChild(String id) { return id == null ? null : children.get(id.toLowerCase(Locale.ROOT)); }
    public int getLevel() { return heightInTree; }

    public void addChild(CommandTreeNode child) {
        Validate.notNull(child, "Child cannot be null");
        String key = child.getId().toLowerCase(Locale.ROOT);
        Validate.isTrue(!children.containsKey(key), "Command '%s' already has child '%s'", getPath(), child.getId());
        children.put(key, child);
    }

    public CommandResult execute(CommandTreeExplorer explorer, ServerCommandSource sender, String[] args) {
        return CommandResult.THROW_USAGE;
    }

    public List<String> calculateTabCompletion(CommandTreeExplorer explorer, int argumentIndex) {
        List<String> suggestions = new ArrayList<>();
        for (CommandTreeNode child : children.values()) {
            if (!child.getClass().isAnnotationPresent(Deprecated.class)) suggestions.add(child.getId());
        }
        if (argumentIndex >= 0 && arguments.size() > argumentIndex) arguments.get(argumentIndex).autoComplete(explorer, suggestions);
        return suggestions;
    }

    public List<String> calculateUsageList() {
        return calculateUsageList(getPath(), new ArrayList<>());
    }

    private List<String> calculateUsageList(String path, List<String> out) {
        if (hasParameters() || children.isEmpty()) out.add((path + " " + formatParameters()).trim());
        for (CommandTreeNode child : children.values()) child.calculateUsageList(path + " " + child.getId(), out);
        return out;
    }

    public String formatParameters() {
        StringBuilder builder = new StringBuilder();
        for (Argument<?> argument : arguments) builder.append(argument.format()).append(' ');
        return builder.length() == 0 ? "" : builder.substring(0, builder.length() - 1);
    }

    public enum CommandResult { SUCCESS, FAILURE, THROW_USAGE }
}
