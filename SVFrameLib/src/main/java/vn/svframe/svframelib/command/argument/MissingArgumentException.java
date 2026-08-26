package vn.svframe.svframelib.command.argument;

import vn.svframe.svframelib.command.CommandException;

public class MissingArgumentException extends CommandException {
    public MissingArgumentException(Argument<?> argument) {
        super("Missing argument " + (argument == null ? "<unknown>" : argument.format()));
    }
}
