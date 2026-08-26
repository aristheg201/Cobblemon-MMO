package io.lumine.mythic.lib.command.argument;

import io.lumine.mythic.lib.command.CommandException;

public class ArgumentParseException extends CommandException {
    public ArgumentParseException(String message, Exception cause) { super(message, cause); }
    public ArgumentParseException(String message) { super(message); }
}
