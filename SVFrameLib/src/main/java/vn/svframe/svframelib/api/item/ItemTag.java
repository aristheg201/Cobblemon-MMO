package vn.svframe.svframelib.api.item;

import vn.svframe.svframelib.gson.JsonArray;
import vn.svframe.svframelib.gson.JsonElement;
import vn.svframe.svframelib.gson.JsonObject;
import vn.svframe.svframelib.gson.JsonPrimitive;
import vn.svframe.svframelib.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** NBT tag value wrapper plus the 1.7.1 typed JSON compression format. */
public final class ItemTag {
    private static final String INT = "_?int";
    private static final String DOUBLE = "_?dbl";
    private static final String STRING = "_?str";
    private static final String BOOLEAN = "_?bol";
    private static final String LIST = "_?lst";

    private final String path;
    private final Object value;

    public ItemTag(String path, Object value) {
        this.path = Objects.requireNonNull(path, "path");
        this.value = value;
    }

    public String getPath() { return path; }
    public Object getValue() { return value; }

    @Override public boolean equals(Object object) {
        return object instanceof ItemTag tag && path.equals(tag.path) && Objects.deepEquals(value, tag.value);
    }
    @Override public int hashCode() { return 31 * path.hashCode() + Objects.hashCode(value); }

    public static ItemTag getTagAtPath(String path, ArrayList<ItemTag> tags) {
        if (tags == null) return null;
        for (ItemTag tag : tags) if (tag.path.equals(path)) return tag;
        return null;
    }

    public static ItemTag getTagAtPath(String path, NBTItem item, SupportedNBTTagValues expected) {
        if (item == null || !item.hasTag(path)) return null;
        return new ItemTag(path, item.get(path));
    }

    public static ItemTag fromStringList(String path, List<String> list) {
        return new ItemTag(path, list == null ? List.of() : List.copyOf(list));
    }

    public static ArrayList<String> getStringListFromTag(ItemTag tag) {
        ArrayList<String> output = new ArrayList<>();
        if (tag != null && tag.value instanceof Iterable<?> iterable)
            for (Object value : iterable) output.add(String.valueOf(value));
        return output;
    }

    public static JsonArray toJsonArray(ItemTag tag) {
        if (tag == null || !(tag.getValue() instanceof String json)) return new JsonArray();
        return JsonParser.parseString(json).getAsJsonArray();
    }

    public static ItemTag toItemTag(String path, JsonArray array) {
        return new ItemTag(path, array == null ? "[]" : array.toString());
    }

    public static JsonArray compressTags(ArrayList<ItemTag> tags) {
        JsonArray output = new JsonArray();
        if (tags == null) return output;
        for (ItemTag tag : tags) {
            JsonObject encoded = new JsonObject();
            Object value = tag.getValue();
            String path = tag.getPath();
            if (value instanceof Integer number) encoded.addProperty(path + INT, number);
            else if (value instanceof Double number) encoded.addProperty(path + DOUBLE, number);
            else if (value instanceof Float number) encoded.addProperty(path + DOUBLE, number.doubleValue());
            else if (value instanceof Number number) encoded.addProperty(path + DOUBLE, number.doubleValue());
            else if (value instanceof String string) encoded.addProperty(path + STRING, string);
            else if (value instanceof Boolean flag) encoded.addProperty(path + BOOLEAN, flag);
            else if (value instanceof Iterable<?> iterable) {
                JsonArray list = new JsonArray();
                for (Object element : iterable) {
                    if (element instanceof Number number) list.add(number);
                    else if (element instanceof Boolean flag) list.add(flag);
                    else if (element != null) list.add(String.valueOf(element));
                }
                encoded.add(path + LIST, list);
            }
            output.add(encoded);
        }
        return output;
    }

    public static ArrayList<ItemTag> decompressTags(JsonArray input) {
        ArrayList<ItemTag> output = new ArrayList<>();
        if (input == null) return output;
        for (JsonElement element : input) {
            if (!element.isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String encodedPath = entry.getKey();
                JsonElement encoded = entry.getValue();
                String suffix = suffix(encodedPath);
                if (suffix == null) continue;
                String path = encodedPath.substring(0, encodedPath.length() - suffix.length());
                Object value = null;
                if (encoded.isJsonPrimitive()) {
                    JsonPrimitive primitive = encoded.getAsJsonPrimitive();
                    value = switch (suffix) {
                        case INT -> primitive.getAsInt();
                        case DOUBLE -> primitive.getAsDouble();
                        case STRING -> primitive.getAsString();
                        case BOOLEAN -> primitive.getAsBoolean();
                        default -> null;
                    };
                } else if (suffix.equals(LIST) && encoded.isJsonArray()) {
                    ArrayList<String> list = new ArrayList<>();
                    for (JsonElement listElement : encoded.getAsJsonArray()) list.add(listElement.getAsString());
                    value = list;
                }
                if (value != null) output.add(new ItemTag(path, value));
            }
        }
        return output;
    }

    private static String suffix(String key) {
        for (String suffix : new String[]{INT, DOUBLE, STRING, BOOLEAN, LIST}) if (key.endsWith(suffix)) return suffix;
        return null;
    }
}
