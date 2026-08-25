package io.lumine.mythic.lib.api.stat.modifier;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.api.InstanceModifier;
import io.lumine.mythic.lib.player.modifier.ModifierMap;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import io.lumine.mythic.lib.util.configobject.ConfigObject;
import vn.svframe.mythiclibfabric.MythicLibStatMod;
import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;
import java.util.Objects;
import java.util.UUID;

public class StatModifier extends InstanceModifier {
    private final String stat;
    public StatModifier(String key,String stat,double value){this(key,stat,value,ModifierType.FLAT);}
    public StatModifier(String key,String stat,double value,ModifierType type){this(key,stat,value,type,EquipmentSlot.OTHER,ModifierSource.OTHER);}
    public StatModifier(String key,String stat,double value,ModifierType type,EquipmentSlot slot,ModifierSource source){this(UUID.randomUUID(),key,stat,value,type,slot,source);}
    public StatModifier(UUID id,String key,String stat,double value,ModifierType type,EquipmentSlot slot,ModifierSource source){super(id,key,slot,source,value,type);this.stat=Objects.requireNonNull(stat,"stat");}
    public StatModifier(String key,String stat,String encoded){this(UUID.randomUUID(),key,stat,ModifierType.pairFromString(encoded).getRight(),ModifierType.pairFromString(encoded).getLeft(),EquipmentSlot.OTHER,ModifierSource.OTHER);}
    public StatModifier(ConfigObject config){super(config);this.stat=Objects.requireNonNull(config.getString("stat"),"stat");}
    public String getStat(){return stat;} public StatModifier add(double amount){return new StatModifier(getUniqueId(),getKey(),stat,value+amount,type,getSlot(),getSource());}public StatModifier multiply(double scalar){return new StatModifier(getUniqueId(),getKey(),stat,value*scalar,type,getSlot(),getSource());}
    @Override public void register(MMOPlayerData data){MythicLibStatMod.engine().register(data.getUniqueId(),stat,toNative());}
    @Override public void unregister(MMOPlayerData data){MythicLibStatMod.engine().remove(data.getUniqueId(),stat,getUniqueId());}
    @Override public ModifierMap<?> getMap(MMOPlayerData data){return data.getStatMap();}
    public NativeStatEngine.Modifier toNative(){return new NativeStatEngine.Modifier(getUniqueId(),getKey(),value,toNative(type),toNative(getSlot()),toNative(getSource()));}
    public static NativeStatEngine.ModifierType toNative(ModifierType type){return NativeStatEngine.ModifierType.valueOf(type.name());}
    public static NativeStatEngine.EquipmentSlot toNative(EquipmentSlot slot){return NativeStatEngine.EquipmentSlot.valueOf(slot.name());}
    public static NativeStatEngine.ModifierSource toNative(ModifierSource source){return NativeStatEngine.ModifierSource.valueOf(source.name());}
    public static StatModifier fromNative(String stat,NativeStatEngine.Modifier m){return new StatModifier(m.id(),m.key(),stat,m.value(),ModifierType.valueOf(m.type().name()),EquipmentSlot.valueOf(m.slot().name()),ModifierSource.valueOf(m.source().name()));}
}
