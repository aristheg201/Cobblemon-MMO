package vn.svframe.svframelib.util.configobject;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.script.Script;
import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;

import java.util.Set;
import java.util.function.Function;

/** Platform-neutral configuration contract retaining the MythicLib 1.7.1 helpers. */
public interface ConfigObject {
    Object get(String key);
    String getString(String key);
    String getString(String key, String fallback);
    double getDouble(String key);
    double getDouble(String key, double fallback);
    int getInt(String key);
    int getInt(String key, int fallback);
    boolean getBoolean(String key);
    boolean getBoolean(String key, boolean fallback);
    float getFloat(String key);
    float getFloat(String key, float fallback);
    String string(String... keys);
    String stringFb(String fallback, String... keys);
    double dble(String... keys);
    double dble(double fallback, String... keys);
    float flpt(String... keys);
    float flpt(float fallback, String... keys);
    int integer(String... keys);
    int integer(int fallback, String... keys);
    boolean bool(String... keys);
    boolean bool(boolean fallback, String... keys);

    default Script script(String... keys) {
        for (String key : keys) if (contains(key)) return MythicLib.inst().getSkills().loadScript(getString(key));
        throw new MissingArgumentException(keys);
    }

    default Script script(Script fallback, String... keys) {
        for (String key : keys) if (contains(key)) return MythicLib.inst().getSkills().loadScript(getString(key));
        return fallback;
    }

    default <T> T parse(T fallback, Function<String, T> parser, String... keys) {
        for (String key : keys) if (contains(key)) return parser.apply(getString(key));
        return fallback;
    }

    default <T> T parse(Function<String, T> parser, String... keys) {
        for (String key : keys) if (contains(key)) return parser.apply(getString(key));
        throw new MissingArgumentException(keys);
    }

    default NumericExpression numericExpr(String... keys) {
        for (String key : keys) if (contains(key)) return NumericExpression.compile(getString(key));
        throw new MissingArgumentException(keys);
    }

    default NumericExpression numericExpr(NumericExpression fallback, String... keys) {
        for (String key : keys) if (contains(key)) return NumericExpression.compile(getString(key));
        return fallback;
    }

    default Script getScriptOrNull(String key) {
        return contains(key) ? MythicLib.inst().getSkills().getScriptOrThrow(getString(key)) : null;
    }

    default Script getScript(String... keys) {
        for (String key : keys) if (contains(key)) return MythicLib.inst().getSkills().getScriptOrThrow(getString(key));
        throw new MissingArgumentException(keys);
    }

    default EntityTargeter getEntityTargeter(String key) {
        return MythicLib.inst().getSkills().loadEntityTargeter(adaptObject(key));
    }

    default LocationTargeter getLocationTargeter(String key) {
        return MythicLib.inst().getSkills().loadLocationTargeter(adaptObject(key));
    }

    ConfigObject getObject(String key);
    ConfigObject adaptObject(String key);
    boolean contains(String key);
    Set<String> getKeys();
    String getKey();
    default boolean hasKey() { return getKey() != null && !getKey().isEmpty(); }
    default void validateKeys(String... keys) { for (String key : keys) if (!contains(key)) throw new MissingArgumentException(key); }
    default void validateArgs(int expected) { for (int i = 0; i < expected; i++) if (!contains(Integer.toString(i))) throw new MissingArgumentException(Integer.toString(i)); }
}
