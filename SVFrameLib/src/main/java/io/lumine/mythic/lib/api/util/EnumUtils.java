package io.lumine.mythic.lib.api.util;
import java.util.Optional;
public class EnumUtils { public static <T> Optional<T> getIfPresent(Class<T> type,String key){if(type==null||!type.isEnum()||key==null)return Optional.empty();for(T c:type.getEnumConstants())if(((Enum<?>)c).name().equals(key))return Optional.of(c);return Optional.empty();} }
