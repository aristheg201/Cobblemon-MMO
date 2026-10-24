package vn.svframe.fantasyhub.core.sync;
import java.util.Map;
/** Accessories emits container reconciliation even when its change map is empty. */
public final class EquipmentEventPolicy {
 private EquipmentEventPolicy(){}
 public static boolean containerChanged(Map<?,?> changes){return !changes.isEmpty();}
 public static boolean stackChanged(boolean equal){return !equal;}
}
