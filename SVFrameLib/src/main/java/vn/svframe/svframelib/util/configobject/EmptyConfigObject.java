package vn.svframe.svframelib.util.configobject;

import java.util.Objects;
import java.util.Set;

public final class EmptyConfigObject implements ConfigObject {
    @Override public String getString(String key){throw new MissingArgumentException(key);} @Override public String getString(String key,String fallback){return fallback;}
    @Override public double getDouble(String key){throw new MissingArgumentException(key);} @Override public double getDouble(String key,double fallback){return fallback;}
    @Override public int getInt(String key){throw new MissingArgumentException(key);} @Override public int getInt(String key,int fallback){return fallback;}
    @Override public boolean getBoolean(String key){throw new MissingArgumentException(key);} @Override public boolean getBoolean(String key,boolean fallback){return fallback;}
    @Override public float getFloat(String key){throw new MissingArgumentException(key);} @Override public float getFloat(String key,float fallback){return fallback;}
    @Override public String string(String...keys){throw new MissingArgumentException(keys);} @Override public String stringFb(String fallback,String...keys){return Objects.requireNonNull(fallback);}
    @Override public double dble(String...keys){throw new MissingArgumentException(keys);} @Override public double dble(double fallback,String...keys){return fallback;}
    @Override public float flpt(String...keys){throw new MissingArgumentException(keys);} @Override public float flpt(float fallback,String...keys){return fallback;}
    @Override public int integer(String...keys){throw new MissingArgumentException(keys);} @Override public int integer(int fallback,String...keys){return fallback;}
    @Override public boolean bool(String...keys){throw new MissingArgumentException(keys);} @Override public boolean bool(boolean fallback,String...keys){return fallback;}
    @Override public ConfigObject getObject(String key){throw new MissingArgumentException(key);} @Override public ConfigObject adaptObject(String key){throw new MissingArgumentException(key);}
    @Override public boolean contains(String key){return false;} @Override public Set<String> getKeys(){return Set.of();} @Override public String getKey(){return null;}
}
