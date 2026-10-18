package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Supplied Witcher geometry follows the player's actual animated biped bones. */
public final class HunterArmorFeature extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Set<String> COLLECTIONS=Set.of("feline","griffin","ursine","kaer_morhen","wolven");
    private static final Map<String,Geometry> MODELS=new HashMap<>();
    HunterArmorFeature(FeatureRendererContext<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> context){super(context);}
    public static String collection(ItemStack stack) {
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);if(data==null)return "";
        String texture=data.copyNbt().getString("SVFRAME_ARMOR_TEXTURE"),prefix="fantasyhub:hunter/";
        String name=texture.startsWith(prefix)?texture.substring(prefix.length()):"";
        return COLLECTIONS.contains(name)?name:"";
    }
    public static boolean hat(ItemStack stack) {
        var data=stack.get(DataComponentTypes.CUSTOM_DATA);
        return data!=null&&data.copyNbt().getString("SVFRAME_ITEM_MODEL").equals("fantasyhub:item/hunter/halfmoon_helmet");
    }
    @Override public void render(MatrixStack matrices,VertexConsumerProvider vertices,int light,AbstractClientPlayerEntity entity,float limbAngle,float limbDistance,float tickDelta,float age,float headYaw,float headPitch) {
        if(entity.isInvisible()||RiderVisuals.state(entity.getId())!=null)return;
        for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            String collection=collection(entity.getEquippedStack(slot));if(collection.isEmpty())continue;
            Geometry geometry=MODELS.computeIfAbsent(collection,HunterArmorFeature::load);
            VertexConsumer consumer=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/armor/hunter/"+collection+".png")));
            for(String bone:switch(slot){case HEAD->List.of("armorHead");case CHEST->List.of("armorBody","armorLeftArm","armorRightArm");case LEGS->List.of("armorLeftLeg","armorRightLeg");case FEET->List.of("armorLeftBoot","armorRightBoot");default->List.<String>of();}) {
                Node part=geometry.nodes().get(bone);String parent=geometry.parents().get(bone);
                ModelPart animated=switch(parent){case "bipedHead"->getContextModel().head;case "bipedBody"->getContextModel().body;case "bipedLeftArm"->getContextModel().leftArm;case "bipedRightArm"->getContextModel().rightArm;case "bipedLeftLeg"->getContextModel().leftLeg;case "bipedRightLeg"->getContextModel().rightLeg;default->throw new IllegalStateException("Unknown armor attachment "+parent);};
                matrices.push();animated.rotate(matrices);part.render(matrices,consumer,light);matrices.pop();
            }
        }
    }
    private record Geometry(Map<String,Node> nodes,Map<String,String> parents) {}
    private record Face(float[][] xyz,float u,float v,float width,float height,float nx,float ny,float nz,float textureWidth,float textureHeight) {
        void render(MatrixStack matrices,VertexConsumer vertices,int light) {
            float[][] uv={{u+width,v},{u,v},{u,v+height},{u+width,v+height}};
            for(int i=0;i<4;i++)vertices.vertex(matrices.peek(),xyz[i][0]/16,xyz[i][1]/16,xyz[i][2]/16).color(0xffffffff).texture(uv[i][0]/textureWidth,uv[i][1]/textureHeight).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(matrices.peek(),nx,ny,nz);
        }
    }
    private static final class Node {
        final ModelPart transform=new ModelPart(List.of(),Map.of());
        final List<ModelPart.Cuboid> cubes=new ArrayList<>();final List<Face> faces=new ArrayList<>();final List<Node> children=new ArrayList<>();
        void render(MatrixStack matrices,VertexConsumer vertices,int light){matrices.push();transform.rotate(matrices);for(var cube:cubes)cube.renderCuboid(matrices.peek(),vertices,light,OverlayTexture.DEFAULT_UV,0xffffffff);for(var face:faces)face.render(matrices,vertices,light);for(var child:children)child.render(matrices,vertices,light);matrices.pop();}
    }
    private static float[] xyz(JsonObject object,String key,float...fallback){if(!object.has(key))return fallback;var a=object.getAsJsonArray(key);return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};}
    private static Node at(float[] pivot,float[] parent,float[] rotation){Node n=new Node();n.transform.setPivot(pivot[0]-parent[0],parent[1]-pivot[1],pivot[2]-parent[2]);n.transform.setAngles((float)Math.toRadians(-rotation[0]),(float)Math.toRadians(rotation[1]),(float)Math.toRadians(-rotation[2]));return n;}
    private static Geometry load(String name) {
        String resource="/assets/fantasyhub/geo/hunter/"+name+".geo.json";
        try(InputStream input=HunterArmorFeature.class.getResourceAsStream(resource)) {
            if(input==null)throw new IllegalStateException("Missing armor geometry "+resource);
            var geometry=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            var description=geometry.getAsJsonObject("description");float width=description.get("texture_width").getAsFloat(),height=description.get("texture_height").getAsFloat();
            Map<String,JsonObject> sources=new LinkedHashMap<>();Map<String,Node> nodes=new LinkedHashMap<>();Map<String,String> parents=new HashMap<>();
            for(var raw:geometry.getAsJsonArray("bones")){var bone=raw.getAsJsonObject();sources.put(bone.get("name").getAsString(),bone);}
            for(var row:sources.entrySet()) {
                JsonObject bone=row.getValue();String parent=bone.has("parent")?bone.get("parent").getAsString():"";parents.put(row.getKey(),parent);
                float[] pivot=xyz(bone,"pivot",0,0,0),parentPivot=parent.isEmpty()?new float[]{0,24,0}:xyz(sources.get(parent),"pivot",0,0,0);
                Node node=at(pivot,parentPivot,xyz(bone,"rotation",0,0,0));nodes.put(row.getKey(),node);
                if(bone.has("cubes"))for(var raw:bone.getAsJsonArray("cubes")) {
                    var cube=raw.getAsJsonObject();float[] cubePivot=xyz(cube,"pivot",pivot);Node mesh=at(cubePivot,pivot,xyz(cube,"rotation",0,0,0));node.children.add(mesh);
                    float[] origin=xyz(cube,"origin"),size=xyz(cube,"size");float inflate=cube.has("inflate")?cube.get("inflate").getAsFloat():0;
                    float x=origin[0]-cubePivot[0],y=cubePivot[1]-origin[1]-size[1],z=origin[2]-cubePivot[2];
                    if(cube.get("uv").isJsonArray()) {
                        var uv=cube.getAsJsonArray("uv");mesh.cubes.add(new ModelPart.Cuboid(uv.get(0).getAsInt(),uv.get(1).getAsInt(),x,y,z,size[0],size[1],size[2],inflate,inflate,inflate,cube.has("mirror")&&cube.get("mirror").getAsBoolean(),width,height,EnumSet.allOf(Direction.class)));
                    } else {
                        float x0=x-inflate,x1=x+size[0]+inflate,y0=y-inflate,y1=y+size[1]+inflate,z0=z-inflate,z1=z+size[2]+inflate;
                        for(var face:cube.getAsJsonObject("uv").entrySet()) {
                            float[][] points;float nx=0,ny=0,nz=0;
                            switch(face.getKey()) {
                                case "north"->{nz=-1;points=new float[][]{{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}};}
                                case "south"->{nz=1;points=new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};}
                                case "east"->{nx=1;points=new float[][]{{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}};}
                                case "west"->{nx=-1;points=new float[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}};}
                                case "up"->{ny=-1;points=new float[][]{{x1,y0,z1},{x0,y0,z1},{x0,y0,z0},{x1,y0,z0}};}
                                case "down"->{ny=1;points=new float[][]{{x1,y1,z0},{x0,y1,z0},{x0,y1,z1},{x1,y1,z1}};}
                                default->throw new IllegalStateException("Unknown UV face "+face.getKey());
                            }
                            var f=face.getValue().getAsJsonObject();var uv=f.getAsJsonArray("uv");var span=f.getAsJsonArray("uv_size");mesh.faces.add(new Face(points,uv.get(0).getAsFloat(),uv.get(1).getAsFloat(),span.get(0).getAsFloat(),span.get(1).getAsFloat(),nx,ny,nz,width,height));
                        }
                    }
                }
            }
            for(var row:parents.entrySet())if(!row.getValue().isEmpty())nodes.get(row.getValue()).children.add(nodes.get(row.getKey()));
            return new Geometry(Map.copyOf(nodes),Map.copyOf(parents));
        }catch(IOException error){throw new IllegalStateException("Cannot read Hunter armor "+name,error);}
    }
}
