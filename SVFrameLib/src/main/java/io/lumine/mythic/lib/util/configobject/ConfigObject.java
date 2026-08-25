package io.lumine.mythic.lib.util.configobject;

import java.util.Set;
import java.util.function.Function;

public interface ConfigObject {
    String getString(String key);
    String getString(String key,String fallback);
    double getDouble(String key);
    double getDouble(String key,double fallback);
    int getInt(String key);
    int getInt(String key,int fallback);
    boolean getBoolean(String key);
    boolean getBoolean(String key,boolean fallback);
    float getFloat(String key);
    float getFloat(String key,float fallback);
    String string(String... keys);
    String stringFb(String fallback,String... keys);
    double dble(String... keys);
    double dble(double fallback,String... keys);
    float flpt(String... keys);
    float flpt(float fallback,String... keys);
    int integer(String... keys);
    int integer(int fallback,String... keys);
    boolean bool(String... keys);
    boolean bool(boolean fallback,String... keys);
    ConfigObject getObject(String key);
    ConfigObject adaptObject(String key);
    boolean contains(String key);
    Set<String> getKeys();
    String getKey();
    default boolean hasKey(){return getKey()!=null&&!getKey().isEmpty();}
    default <T>T parse(T fallback, Function<String,T> parser,String...keys){for(String key:keys)if(contains(key))return parser.apply(getString(key));return fallback;}
    default <T>T parse(Function<String,T> parser,String...keys){String value=string(keys);return parser.apply(value);}
    default void validateKeys(String... keys){for(String key:keys)if(!contains(key))throw new MissingArgumentException(key);}
    default void validateArgs(int expected){for(int i=0;i<expected;i++)if(!contains(Integer.toString(i)))throw new MissingArgumentException(Integer.toString(i));}
}
