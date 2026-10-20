package vn.svframe.fantasyhub.client;

import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Native adaptation of Fisk beam/core and mandala shapes, with bounded Cobblemon bursts. */
final class HeroEffects {
    private static final Identifier WHITE=Identifier.ofVanilla("textures/misc/white.png");
    private static final Map<String,Long> NATIVE=new TreeMap<>();
    private static long tick=-1,dropped;
    private static int emitted;
    private HeroEffects() {}
    static void clear(){NATIVE.clear();tick=-1;emitted=0;dropped=0;}
    static void burst(HeroVisuals.Cue cue,long now,int quality) {
        if(FantasyHubClient.options!=null&&(!FantasyHubClient.options.cobblemonEffects||FantasyHubClient.options.effectsDensity<=0))return;
        var client=MinecraftClient.getInstance();
        if(client.player==null||client.world==null||client.player.squaredDistanceTo(cue.from())>(quality==0?256:1600))return;
        if(tick!=now){tick=now;emitted=0;}
        if(emitted>=(quality==0?1:quality==1?2:4)){dropped++;return;}
        boolean impact=cue.phase().equals("IMPACT");
        if(!impact&&!cue.phase().equals("CAST"))return;
        String effect=switch(cue.hero()) {
            case "SORCERER_SUPREME" -> impact?"aerialace_targetburst":"psybeam_charge";
            case "IRON_MAN" -> impact?"explosion_burst_flash":"metal_sparks";
            case "THOR" -> "thunderbolt_actorbolts";
            case "FLASH" -> impact?"aerialace_targetburst":"quickattack_dashlines";
            case "SPIDER_MAN" -> impact?"aerialace_targetburst":"quickattack_dashlines";
            default -> "";
        };
        if(effect.isEmpty())return;
        emitted++;
        if(KabutoSnowstorm.emit(effect,cue.from().add(0,impact?.5:1,0),cue.to().subtract(cue.from()).normalize()))
            NATIVE.merge(cue.hero()+":"+effect,1L,Long::sum);
    }
    static com.google.gson.JsonObject observation() {
        var data=new com.google.gson.JsonObject();var counts=new com.google.gson.JsonObject();
        NATIVE.forEach(counts::addProperty);data.add("nativeEffects",counts);data.addProperty("budgetDrops",dropped);return data;
    }
    static void render(WorldRenderContext context,Iterable<HeroVisuals.Cue> effects,long tick,int quality) {
        if(FantasyHubClient.options!=null&&FantasyHubClient.options.effectsDensity<=0)return;
        if(context.matrixStack()==null||context.consumers()==null)return;
        var camera=context.camera().getPos();var m=context.matrixStack();m.push();m.translate(-camera.x,-camera.y,-camera.z);
        var v=context.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        double now=tick+context.tickCounter().getTickDelta(false);
        for(var c:effects) {
            if(c.from().squaredDistanceTo(camera)>4096)continue;
            int color=switch(c.hero()){case "IRON_MAN"->0x83eaff;case "THOR"->0x95cfff;case "FLASH"->0xffbf28;case "SORCERER_SUPREME"->0xff8a22;case "SPIDER_MAN"->0xe9f2ff;default->0x8397b0;};
            double fade=Math.min(1,Math.max(0,(c.expires()-now)/4));
            if(c.phase().equals("BEAM")) {
                if(c.hero().equals("THOR")||c.hero().equals("FLASH"))lightning(m,v,c.from(),c.to(),camera,now,color,fade);
                else beam(m,v,c.from(),c.to(),camera,c.ability().equals("UNIBEAM")?.18:c.hero().equals("SPIDER_MAN")?.012:.055,color,fade);
            }
            if(c.hero().equals("SORCERER_SUPREME")&&c.phase().equals("CAST")) {
                Vec3d forward=c.to().subtract(c.from()).multiply(1,0,1).normalize();if(forward.lengthSquared()<.01)forward=new Vec3d(0,0,1);
                Vec3d right=new Vec3d(0,1,0).crossProduct(forward).normalize();double angle=(now-c.start())*.035;
                if(c.ability().equals("TAO_MANDALAS")) {
                    var client=MinecraftClient.getInstance();var actor=client.world==null?null:client.world.getEntityById(c.entity());
                    if(actor!=null){forward=actor.getRotationVec(1).multiply(1,0,1).normalize();right=new Vec3d(0,1,0).crossProduct(forward).normalize();}
                    var center=(actor==null?c.from():actor.getLerpedPos(context.tickCounter().getTickDelta(false))).add(0,1.25,0).add(forward.multiply(.7));
                    mandala(m,v,center.add(right.multiply(.62)),right,new Vec3d(0,1,0),.5,angle,color,quality);
                    mandala(m,v,center.add(right.multiply(-.62)),right,new Vec3d(0,1,0),.5,-angle,color,quality);
                } else if(c.ability().equals("SLING_RING")) {
                    mandala(m,v,c.from().add(0,1.1,0),right,new Vec3d(0,1,0),1.05,angle,color,quality);
                    mandala(m,v,c.to().add(0,1.1,0),right,new Vec3d(0,1,0),1.05,-angle,color,quality);
                } else mandala(m,v,c.to().add(0,.12,0),new Vec3d(1,0,0),new Vec3d(0,0,1),c.ability().equals("EYE_OF_AGAMOTTO")?3:1.6,angle,color,quality);
            }
        }
        m.pop();
    }
    private static void lightning(MatrixStack m,VertexConsumer v,Vec3d from,Vec3d to,Vec3d camera,double time,int color,double fade) {
        Vec3d axis=to.subtract(from).normalize(),side=axis.crossProduct(new Vec3d(0,1,0));if(side.lengthSquared()<.01)side=new Vec3d(1,0,0);side=side.normalize();
        int segments=Math.max(3,Math.min(18,(int)from.distanceTo(to)*2));Vec3d last=from;
        for(int i=1;i<=segments;i++) {
            double fraction=i/(double)segments;
            Vec3d next=from.lerp(to,fraction).add(side.multiply(i==segments?0:Math.sin(i*13.7+Math.floor(time/2)*3.1)*.18));
            beam(m,v,last,next,camera,.027,color,fade);last=next;
        }
    }
    private static void beam(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d camera,double width,int color,double fade) {
        Vec3d axis=b.subtract(a),side=axis.crossProduct(camera.subtract(a.lerp(b,.5))).normalize();
        if(side.lengthSquared()<.01)side=new Vec3d(1,0,0);
        ribbon(m,v,a,b,side.multiply(width*3),((int)(40*fade)<<24)|color);
        ribbon(m,v,a,b,side.multiply(width*1.6),((int)(110*fade)<<24)|color);
        ribbon(m,v,a,b,side.multiply(width*.55),((int)(245*fade)<<24)|0xffffff);
    }
    private static void mandala(MatrixStack m,VertexConsumer v,Vec3d center,Vec3d x,Vec3d y,double scale,double angle,int color,int quality) {
        int segments=quality==0?24:48;
        for(double radius:new double[]{.3,.625,.775,.875,.95,1})polygon(m,v,center,x,y,radius*scale,segments,angle,color);
        polygon(m,v,center,x,y,.625*scale,4,angle,color);polygon(m,v,center,x,y,.625*scale,4,angle+Math.PI/4,color);polygon(m,v,center,x,y,.3*scale,4,-angle,color);
    }
    private static void polygon(MatrixStack m,VertexConsumer v,Vec3d center,Vec3d x,Vec3d y,double radius,int n,double angle,int color) {
        Vec3d normal=x.crossProduct(y).normalize();
        for(int i=0;i<n;i++) {
            double a=i*2*Math.PI/n+angle,b=(i+1)*2*Math.PI/n+angle;
            Vec3d p=center.add(x.multiply(Math.cos(a)*radius)).add(y.multiply(Math.sin(a)*radius));
            Vec3d q=center.add(x.multiply(Math.cos(b)*radius)).add(y.multiply(Math.sin(b)*radius));
            Vec3d edge=q.subtract(p).crossProduct(normal).normalize();
            ribbon(m,v,p,q,edge.multiply(.009),0xd0000000|color);
            ribbon(m,v,p,q,edge.multiply(.022),0x26000000|color);
        }
    }
    private static void ribbon(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d side,int color) {
        Vec3d[] p={a.add(side),a.subtract(side),b.subtract(side),b.add(side)};
        for(int i=0;i<4;i++)v.vertex(m.peek(),(float)p[i].x,(float)p[i].y,(float)p[i].z).color(color).texture(i<2?0:1,i==0||i==3?0:1).overlay(OverlayTexture.DEFAULT_UV).light(0xf000f0).normal(m.peek(),0,1,0);
    }
}
