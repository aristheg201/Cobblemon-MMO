package vn.svframe.svframelib.gson;

public class JsonSyntaxException extends JsonParseException {
    public JsonSyntaxException(String message) { super(message); }
    public JsonSyntaxException(String message, Throwable cause) { super(message, cause); }
    public JsonSyntaxException(Throwable cause) { super(cause); }
}
