package vn.svframe.svframeitems.runtime;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EquipmentProviderRegistry {
    private static final CopyOnWriteArrayList<EquipmentProvider> PROVIDERS=new CopyOnWriteArrayList<>();
    private EquipmentProviderRegistry(){}
    public static AutoCloseable register(EquipmentProvider provider){Objects.requireNonNull(provider);PROVIDERS.add(provider);return ()->PROVIDERS.remove(provider);}
    public static List<EquipmentProvider> providers(){return List.copyOf(PROVIDERS);}
}
