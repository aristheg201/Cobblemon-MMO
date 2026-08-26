package io.lumine.mythic.lib.gson;

public class JsonParser {
    public JsonParser() { }
    public JsonElement parse(String json) { return parseString(json); }
    public static JsonElement parseString(String json) {
        try { return JsonElement.wrap(com.google.gson.JsonParser.parseString(json)); }
        catch (com.google.gson.JsonSyntaxException exception) { throw new JsonSyntaxException(exception.getMessage(), exception); }
        catch (com.google.gson.JsonParseException exception) { throw new JsonParseException(exception.getMessage(), exception); }
    }
}
