package io.lumine.mythic.lib.command.argument;

import io.lumine.mythic.lib.command.CommandException;

public class MissingArgumentException extends CommandException {
    public MissingArgumentException(Argument<?> argument) {
        super("Missing argument " + (argument == null ? "<unknown>" : argument.format()));
    }
}
