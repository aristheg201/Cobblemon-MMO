package vn.svframe.svframelib.command.argument;

import vn.svframe.svframelib.command.CommandException;

public class ArgumentParseException extends CommandException {
    public ArgumentParseException(String message, Exception cause) { super(message, cause); }
    public ArgumentParseException(String message) { super(message); }
}
