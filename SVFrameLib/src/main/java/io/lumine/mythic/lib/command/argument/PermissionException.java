package io.lumine.mythic.lib.command.argument;

public class PermissionException extends ArgumentParseException {
    public PermissionException() { super("You don't have permission to use this command"); }
}
