package vn.svframe.svframelib.command;

public class CommandException extends RuntimeException {
    public CommandException(String message, Exception cause) { super(message, cause); }
    public CommandException(String message) { super(message); }
}
