package io.lumine.mythic.lib.api.stat.api;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import io.lumine.mythic.lib.player.modifier.PlayerModifier;
import io.lumine.mythic.lib.util.configobject.ConfigObject;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.UUID;

public abstract class InstanceModifier extends PlayerModifier {
    protected final double value; protected final ModifierType type;
    public InstanceModifier(String key,double value){this(key,EquipmentSlot.OTHER,ModifierSource.OTHER,value,ModifierType.FLAT);}
    public InstanceModifier(String key,EquipmentSlot slot,ModifierSource source,double value,ModifierType type){this(UUID.randomUUID(),key,slot,source,value,type);}
    public InstanceModifier(UUID id,String key,EquipmentSlot slot,ModifierSource source,double value,ModifierType type){super(id,key,slot,source);if(!Double.isFinite(value))throw new IllegalArgumentException("Modifier value must be finite");this.value=value;this.type=Objects.requireNonNull(type);}
    public InstanceModifier(String key,EquipmentSlot slot,ModifierSource source,String encoded){this(UUID.randomUUID(),key,slot,source,ModifierType.pairFromString(encoded).getRight(),ModifierType.pairFromString(encoded).getLeft());}
    public InstanceModifier(ConfigObject config){this(config.getString("key"),EquipmentSlot.OTHER,ModifierSource.OTHER,config.getDouble("value"),config.getBoolean("multiplicative",false)?ModifierType.RELATIVE:config.getBoolean("scalar",false)?ModifierType.ADDITIVE_MULTIPLIER:ModifierType.FLAT);}
    public ModifierType getType(){return type;} public double getValue(){return value;}
    @Override public String toString(){return new DecimalFormat("0.#").format(value)+type.toStringSuffix();}
}
