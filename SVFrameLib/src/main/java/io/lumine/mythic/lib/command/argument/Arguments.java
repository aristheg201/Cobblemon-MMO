package io.lumine.mythic.lib.command.argument;

public class Arguments {
    public static void notNull(Object value, String message) {
        if (value == null) throw new ArgumentParseException(message);
    }

    public static void isTrue(boolean value, String message) {
        if (!value) throw new ArgumentParseException(message);
    }

    public static void isInstanceOf(Class<?> type, Object value, String message) {
        if (type == null || !type.isInstance(value)) throw new ArgumentParseException(message);
    }
}
