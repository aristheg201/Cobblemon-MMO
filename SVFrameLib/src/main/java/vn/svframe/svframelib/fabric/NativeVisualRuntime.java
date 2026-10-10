package vn.svframe.svframelib.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
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

/** Animated resource-pack bones using vanilla item_display packets. No client mod or custom entity registry. */
public final class NativeVisualRuntime {
    private record Bone(String item, int modelData, float scale, float endScale, float x, float y, float z, float endY, float spin, List<float[]> frames) { }
    private record Active(Entity owner, ServerWorld world, Vec3d origin, boolean follow, List<DisplayEntity.ItemDisplayEntity> displays,
                          List<Bone> bones, long start, int duration, float yaw) { }
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static Map<String,List<Bone>> models = Map.of();
    private NativeVisualRuntime() { }
    public static void install() {
        reload();
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
            models = Map.copyOf(next);
        } catch (Exception error) { throw new IllegalArgumentException("Visual models reload rejected; previous models retained",error); }
    }
    public static boolean spawn(String model, Entity owner, Vec3d origin, int ticks, boolean follow) {
        List<Bone> bones = models.get(model);
        if (bones == null || owner == null || !(owner.getWorld() instanceof ServerWorld world) || ACTIVE.size()>=64 || activeBoneCount()+bones.size()>512) return false;
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
                transform(display,bone,0,0);
                display.refreshPositionAndAngles(origin.x,origin.y,origin.z,owner.getYaw(),0);
                if (!world.spawnEntity(display)) throw new IllegalStateException("Visual entity spawn rejected");
                displays.add(display);
            }
            ACTIVE.add(new Active(owner,world,origin,follow,displays,bones,SVFrameLibFabricMod.currentTick(),Math.max(1,Math.min(1200,ticks)),owner.getYaw()));
            return true;
        } catch (RuntimeException error) { displays.forEach(Entity::discard); throw error; }
    }
    private static void tick() {
        long now = SVFrameLibFabricMod.currentTick();
        for (var iterator = ACTIVE.iterator(); iterator.hasNext();) {
            Active active=iterator.next(); long age=now-active.start();
            if (age>=active.duration() || !active.owner().isAlive() || active.owner().isRemoved() || active.owner().getWorld()!=active.world()) { discard(active);iterator.remove();continue; }
            Vec3d position=active.follow()?active.owner().getPos():active.origin();
            for (int i=0;i<active.displays().size();i++) {
                var display=active.displays().get(i);
                display.refreshPositionAndAngles(position.x,position.y,position.z,active.follow()?active.owner().getYaw():active.yaw(),0);
                if ((age & 1)==0) transform(display,active.bones().get(i),(float)age/active.duration(),age);
            }
        }
    }
    private static void transform(DisplayEntity.ItemDisplayEntity display,Bone bone,float progress,long age) {
        float scale=bone.scale()+(bone.endScale()-bone.scale())*progress;
        var transform=bone.frames().isEmpty()?new AffineTransformation(new Vector3f(bone.x(),bone.y()+(bone.endY()-bone.y())*progress,bone.z()),new Quaternionf().rotateY((float)Math.toRadians(bone.spin()*age)),new Vector3f(scale),new Quaternionf()):new AffineTransformation(new Matrix4f().set(bone.frames().get((int)Math.min(age,bone.frames().size()-1))));
        var accessor=(DisplayEntityAccessor)display;
        accessor.svframelib$setStartInterpolation(0); accessor.svframelib$setTransformation(transform);
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
