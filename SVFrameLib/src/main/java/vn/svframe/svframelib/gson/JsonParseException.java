package vn.svframe.svframelib.gson;

public class JsonParseException extends RuntimeException {
    public JsonParseException(String message) { super(message); }
    public JsonParseException(String message, Throwable cause) { super(message, cause); }
    public JsonParseException(Throwable cause) { super(cause); }
}
