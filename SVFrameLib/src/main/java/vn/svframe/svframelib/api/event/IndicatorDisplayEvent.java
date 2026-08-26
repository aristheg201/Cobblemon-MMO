package vn.svframe.svframelib.api.event;
import net.minecraft.entity.Entity;
import vn.svframe.mythiclibfabric.runtime.MythicLibEventHub;
import java.util.function.Consumer;
public class IndicatorDisplayEvent {
 public enum IndicatorType { DAMAGE, CRITICAL, HEAL, REGEN, CUSTOM }
 private final Entity entity; private final IndicatorType type; private String message; private boolean cancelled;
 public IndicatorDisplayEvent(Entity entity,String message,IndicatorType type){this.entity=entity;this.message=message==null?"":message;this.type=type==null?IndicatorType.CUSTOM:type;}
 public Entity getEntity(){return entity;} public IndicatorType getType(){return type;} public String getMessage(){return message;} public void setMessage(String v){message=v==null?"":v;} public boolean isCancelled(){return cancelled;} public void setCancelled(boolean v){cancelled=v;}
 public void call(){MythicLibEventHub.events().publish(this);} public static AutoCloseable subscribe(Consumer<? super IndicatorDisplayEvent> l){return MythicLibEventHub.events().subscribe(IndicatorDisplayEvent.class,l);}
}
