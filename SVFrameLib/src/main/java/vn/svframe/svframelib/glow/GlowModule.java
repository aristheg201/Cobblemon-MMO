package vn.svframe.svframelib.glow;
import net.minecraft.entity.Entity; import net.minecraft.util.Formatting;
public interface GlowModule {
    void setGlowing(Entity entity,Formatting color); void disableGlowing(Entity entity); void enable(); void disable();
    final class Native implements GlowModule {
        private volatile boolean enabled=true;
        public void setGlowing(Entity e,Formatting color){if(enabled&&e!=null)e.setGlowing(true);} public void disableGlowing(Entity e){if(e!=null)e.setGlowing(false);}
        public void enable(){enabled=true;} public void disable(){enabled=false;} public boolean isEnabled(){return enabled;}
    }
}
