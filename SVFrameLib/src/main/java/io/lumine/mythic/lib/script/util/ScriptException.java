package io.lumine.mythic.lib.script.util;
public class ScriptException extends RuntimeException {
    public ScriptException(String message, Exception cause){super(message,cause);}
    public ScriptException(Exception cause){super(cause);}
    public ScriptException(String message){super(message);}
}
