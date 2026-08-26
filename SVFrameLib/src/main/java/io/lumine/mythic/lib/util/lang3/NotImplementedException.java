package io.lumine.mythic.lib.util.lang3;

/** Runtime exception retained for MythicLib API compatibility. */
public class NotImplementedException extends RuntimeException {
    public NotImplementedException() { super(); }
    public NotImplementedException(String message) { super(message); }
    public NotImplementedException(String message, Throwable cause) { super(message, cause); }
    public NotImplementedException(Throwable cause) { super(cause); }
}
