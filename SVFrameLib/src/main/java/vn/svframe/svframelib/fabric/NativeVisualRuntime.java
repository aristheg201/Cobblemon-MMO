package vn.svframe.svframelib.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.fabric.mixin.DisplayEntityAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Server-confirmed model poses using vanilla item displays and packaged presentation assets. */
public final class NativeVisualRuntime {
    private record Bone(String item, int modelData, float scale, float endScale, float x, float y, float z, float endY, float spin, List<float[]> frames) { }
    private record Active(UUID id,String model,Entity owner, ServerWorld world, Vec3d origin, boolean follow, List<DisplayEntity.ItemDisplayEntity> displays,
                          List<Bone> bones, long start, int duration, float yaw,float scale) { }
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static Map<String,List<Bone>> models = Map.of();
    private static Map<String,Map<String,List<List<Double>>>> anchors=Map.of();
    private NativeVisualRuntime() { }
    public static void install() {
        // Optional integration items and presentation carriers must finish registration first.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> reload());
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { ACTIVE.forEach(NativeVisualRuntime::discard); ACTIVE.clear(); });
    }
    public static void reload() {
        Path config = SVFrameLibFabricMod.configRoot().resolve("visual-models.yml");
        try {
            if (!Files.exists(config)) try (var input = NativeVisualRuntime.class.getResourceAsStream("/default/visual-models.yml")) { Files.copy(Objects.requireNonNull(input),config); }
            Map<String,List<Bone>> next = new LinkedHashMap<>();
            for (var entry : YamlLite.map(YamlLite.parse(config)).entrySet()) {
                if (!(entry.getValue() instanceof List<?> raw) || raw.isEmpty() || raw.size()>32) throw new IllegalArgumentException("Model requires 1..32 bones: "+entry.getKey());
                List<Bone> bones = new ArrayList<>();
                for (Object value : raw) {
                    Map<String,Object> bone = YamlLite.map(value);
                    for (String key : bone.keySet()) if (!Set.of("item","custom-model-data","scale","end-scale","x","y","z","end-y","spin").contains(key)) throw new IllegalArgumentException("Unsupported visual property: "+key);
                    String item = String.valueOf(bone.getOrDefault("item","minecraft:iron_sword"));
                    if (!Registries.ITEM.containsId(Identifier.of(item))) throw new IllegalArgumentException("Unknown model item: "+item);
                    float scale = number(bone,"scale",1,.01f,16), y = number(bone,"y",0,-32,32);
                    bones.add(new Bone(item,(int)number(bone,"custom-model-data",0,0,Integer.MAX_VALUE),scale,number(bone,"end-scale",scale,.01f,16),number(bone,"x",0,-32,32),y,number(bone,"z",0,-32,32),number(bone,"end-y",y,-32,32),number(bone,"spin",0,-360,360),List.of()));
                }
                next.put(entry.getKey(),List.copyOf(bones));
            }
            Path directory = SVFrameLibFabricMod.configRoot().resolve("visual-models");
            if (Files.isDirectory(directory)) try (var paths = Files.list(directory)) {
                for (Path file : paths.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    if (Files.size(file)>16_000_000) throw new IllegalArgumentException("Visual file too large: "+file);
                    try (var reader = Files.newBufferedReader(file)) {
                        Map<String,List<Map<String,Object>>> imported = new Gson().fromJson(reader,
                                new TypeToken<Map<String,List<Map<String,Object>>>>(){}.getType());
                        for (var entry : imported.entrySet()) {
                            if (entry.getValue().isEmpty() || entry.getValue().size()>128 || next.containsKey(entry.getKey()))
                                throw new IllegalArgumentException("Invalid or duplicate imported model: "+entry.getKey());
                            List<Bone> bones = new ArrayList<>();
                            for (Map<String,Object> value : entry.getValue()) bones.add(importedBone(value));
                            next.put(entry.getKey(),List.copyOf(bones));
                        }
                    }
                }
            }
            Map<String,Map<String,List<List<Double>>>> nextAnchors=Map.of();
            Path anchorFile=SVFrameLibFabricMod.configRoot().resolve("visual-anchors.json");
            if(Files.exists(anchorFile)){
                if(Files.size(anchorFile)>8_000_000)throw new IllegalArgumentException("Anchor registry too large");
                try(var reader=Files.newBufferedReader(anchorFile)){
                    Map<String,Map<String,List<List<Double>>>> imported=new Gson().fromJson(reader,new TypeToken<Map<String,Map<String,List<List<Double>>>>>(){}.getType());
                    for(var model:imported.values())for(var frames:model.values()){
                        if(frames.size()>241)throw new IllegalArgumentException("Too many anchor frames");
                        for(var point:frames)if(point.size()!=3||point.stream().anyMatch(n->n==null||!Double.isFinite(n)||Math.abs(n)>128))throw new IllegalArgumentException("Invalid anchor point");
                    }
                    nextAnchors=Map.copyOf(imported);
                }
            }
            models=Map.copyOf(next);anchors=nextAnchors;
        } catch (Exception error) { throw new IllegalArgumentException("Visual models reload rejected; previous models retained",error); }
    }
    public static boolean spawn(String model, Entity owner, Vec3d origin, int ticks, boolean follow) {
        return spawnTracked(model,owner,origin,ticks,follow,1)!=null;
    }
    public static UUID spawnTracked(String model,Entity owner,Vec3d origin,int ticks,boolean follow,float scale){
        List<Bone> bones = models.get(model);
        if (bones == null || owner == null || !(owner.getWorld() instanceof ServerWorld world) || ACTIVE.size()>=64 || activeBoneCount()+bones.size()>512||!Float.isFinite(scale)||scale<.01||scale>16) return null;
        UUID id=UUID.randomUUID();
        List<DisplayEntity.ItemDisplayEntity> displays = new ArrayList<>();
        try {
            for (Bone bone : bones) {
                DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY,world);
                ItemStack stack = new ItemStack(Registries.ITEM.get(Identifier.of(bone.item())));
                if (bone.modelData()>0) stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelDataComponent(bone.modelData()));
                display.getStackReference(0).set(stack);
                display.setNoGravity(true); display.addCommandTag("svframe_ephemeral_visual");
                var accessor = (DisplayEntityAccessor)display;
                accessor.svframelib$setInterpolationDuration(2); accessor.svframelib$setTeleportDuration(2);
                transform(display,bone,0,0,scale);
                display.refreshPositionAndAngles(origin.x,origin.y,origin.z,owner.getYaw(),0);
                if (!world.spawnEntity(display)) throw new IllegalStateException("Visual entity spawn rejected");
                displays.add(display);
            }
            ACTIVE.add(new Active(id,model,owner,world,follow?origin.subtract(owner.getPos()):origin,follow,displays,bones,SVFrameLibFabricMod.currentTick(),Math.max(1,Math.min(1200,ticks)),owner.getYaw(),scale));
            return id;
        } catch (RuntimeException error) { displays.forEach(Entity::discard); throw error; }
    }
    private static void tick() {
        long now = SVFrameLibFabricMod.currentTick();
        for (var iterator = ACTIVE.iterator(); iterator.hasNext();) {
            Active active=iterator.next(); long age=now-active.start();
            if (age>=active.duration() || !active.owner().isAlive() || active.owner().isRemoved() || active.owner().getWorld()!=active.world()) { discard(active);iterator.remove();continue; }
            Vec3d position=active.follow()?active.owner().getPos().add(active.origin()):active.origin();
            for (int i=0;i<active.displays().size();i++) {
                var display=active.displays().get(i);
                display.refreshPositionAndAngles(position.x,position.y,position.z,active.follow()?active.owner().getYaw():active.yaw(),0);
                if ((age & 1)==0) transform(display,active.bones().get(i),(float)age/active.duration(),age,active.scale());
            }
        }
    }
    private static void transform(DisplayEntity.ItemDisplayEntity display,Bone bone,float progress,long age,float modelScale) {
        float scale=bone.scale()+(bone.endScale()-bone.scale())*progress;
        var transform=bone.frames().isEmpty()?new AffineTransformation(new Vector3f(bone.x(),bone.y()+(bone.endY()-bone.y())*progress,bone.z()),new Quaternionf().rotateY((float)Math.toRadians(bone.spin()*age)),new Vector3f(scale),new Quaternionf()):new AffineTransformation(new Matrix4f().set(bone.frames().get((int)Math.min(age,bone.frames().size()-1))));
        var accessor=(DisplayEntityAccessor)display;
        accessor.svframelib$setStartInterpolation(0); accessor.svframelib$setTransformation(new AffineTransformation(transform.getMatrix().scaleLocal(modelScale)));
    }
    private static Active active(UUID handle){if(handle==null)return null;for(var visual:ACTIVE)if(visual.id().equals(handle))return visual;return null;}
    public static boolean appearance(UUID handle,String model){
        var visual=active(handle);var replacement=models.get(model);if(visual==null||replacement==null||replacement.size()!=visual.displays().size())return false;
        for(int i=0;i<replacement.size();i++){
            var bone=replacement.get(i);var display=visual.displays().get(i);var color=display.getItemStack().get(DataComponentTypes.DYED_COLOR);
            var stack=new ItemStack(Registries.ITEM.get(Identifier.of(bone.item())));stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelDataComponent(bone.modelData()));if(color!=null)stack.set(DataComponentTypes.DYED_COLOR,color);display.getStackReference(0).set(stack);
        }return true;
    }
    public static void tint(UUID handle,int rgb){var visual=active(handle);if(visual==null)return;for(var display:visual.displays()){var stack=display.getItemStack().copy();if(!stack.isEmpty()){stack.set(DataComponentTypes.DYED_COLOR,new DyedColorComponent(rgb&0xffffff,false));display.getStackReference(0).set(stack);}}}
    public static void visible(UUID handle,boolean visible){var visual=active(handle);if(visual==null)return;if(visible)appearance(handle,visual.model());else visual.displays().forEach(display->display.getStackReference(0).set(ItemStack.EMPTY));}
    public static Vec3d anchor(UUID handle,String name){
        var visual=active(handle);if(visual==null)return null;var frames=anchors.getOrDefault(visual.model(),Map.of()).get(name);if(frames==null||frames.isEmpty())return null;
        int age=(int)Math.max(0,Math.min(frames.size()-1,SVFrameLibFabricMod.currentTick()-visual.start()));var raw=frames.get(age);
        float yaw=visual.follow()?visual.owner().getYaw():visual.yaw();var point=new Vec3d(raw.get(0),raw.get(1),raw.get(2)).multiply(visual.scale()).rotateY((float)Math.toRadians(-yaw));
        return point.add(visual.follow()?visual.owner().getPos().add(visual.origin()):visual.origin());
    }
    private static Bone importedBone(Map<String,Object> value) {
        for (String key : value.keySet()) if (!Set.of("item","custom-model-data","frames").contains(key))
            throw new IllegalArgumentException("Unsupported imported bone property: "+key);
        String item=String.valueOf(value.get("item"));
        if (!Registries.ITEM.containsId(Identifier.of(item))) throw new IllegalArgumentException("Invalid imported item");
        if (!(value.get("frames") instanceof List<?> raw) || raw.isEmpty() || raw.size()>241)
            throw new IllegalArgumentException("Imported bone requires 1..241 frames");
        List<float[]> frames=new ArrayList<>();
        for (Object frame : raw) {
            if (!(frame instanceof List<?> cells) || cells.size()!=16) throw new IllegalArgumentException("Expected column-major 4x4 pose");
            float[] matrix=new float[16];
            for (int i=0;i<16;i++) { matrix[i]=Float.parseFloat(String.valueOf(cells.get(i))); if(!Float.isFinite(matrix[i])||Math.abs(matrix[i])>128) throw new IllegalArgumentException("Invalid imported pose"); }
            if (matrix[3]!=0 || matrix[7]!=0 || matrix[11]!=0 || matrix[15]!=1) throw new IllegalArgumentException("Pose must be affine");
            frames.add(matrix);
        }
        return new Bone(item,(int)number(value,"custom-model-data",0,1,Integer.MAX_VALUE),1,1,0,0,0,0,0,List.copyOf(frames));
    }
    private static int activeBoneCount() { return ACTIVE.stream().mapToInt(active -> active.displays().size()).sum(); }
    private static void discard(Active active) { active.displays().forEach(Entity::discard); }
    public static int activeCount() { return ACTIVE.size(); }
    private static float number(Map<String,Object> map,String key,float fallback,float min,float max) { float n=Float.parseFloat(String.valueOf(map.getOrDefault(key,fallback)));if(!Float.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Invalid visual "+key);return n; }
}
