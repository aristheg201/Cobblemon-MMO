package vn.svframe.svframelib.hologram;
import net.minecraft.entity.decoration.ArmorStandEntity; import net.minecraft.server.world.ServerWorld; import net.minecraft.text.Text; import net.minecraft.util.math.Vec3d;
import java.util.*;
public abstract class Hologram {
    public abstract void despawn(); public abstract boolean isSpawned(); public abstract void updateLocation(ServerWorld world,Vec3d location); public abstract void updateLines(List<String> lines); public abstract List<String> getLines(); public abstract Vec3d getLocation();
    public static Hologram create(ServerWorld world,Vec3d location,List<String> lines){return new Native(world,location,lines);}
    private static final class Native extends Hologram {
        private ServerWorld world; private Vec3d location; private List<String> lines; private final List<ArmorStandEntity> entities=new ArrayList<>();
        Native(ServerWorld w,Vec3d p,List<String> l){world=Objects.requireNonNull(w);location=Objects.requireNonNull(p);lines=l==null?List.of():List.copyOf(l);spawn();}
        private void spawn(){despawn();for(int i=0;i<lines.size();i++){ArmorStandEntity e=new ArmorStandEntity(world,location.x,location.y-i*.25,location.z);e.setInvisible(true);e.setNoGravity(true);e.setCustomName(Text.literal(lines.get(i).replace('&','§')));e.setCustomNameVisible(true);world.spawnEntity(e);entities.add(e);}}
        public void despawn(){for(ArmorStandEntity e:entities)if(!e.isRemoved())e.discard();entities.clear();} public boolean isSpawned(){return !entities.isEmpty();}
        public void updateLocation(ServerWorld w,Vec3d p){world=Objects.requireNonNull(w);location=Objects.requireNonNull(p);spawn();}
        public void updateLines(List<String> l){lines=l==null?List.of():List.copyOf(l);spawn();} public List<String> getLines(){return lines;} public Vec3d getLocation(){return location;}
    }
}
