package vn.svframe.svframelib.fabric;

import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Source-specific presentation of the audited Death_Sentence chain.
 * Timed native particles retain the source colors; gameplay stays in the ability runtime.
 * The rupture and model bone swaps require the corresponding imported visual assets. */
public final class DeathSentencePresentation {
    public static final DustColorTransitionParticleEffect TARGET=transition(0xfefffc,0xd0ffb3,1.2f);
    private static final DustColorTransitionParticleEffect BODY=transition(0xa2f8cb,0x0eaf9b,1.3f);
    private static final DustColorTransitionParticleEffect RING=transition(0xa2f8cb,0x0eaf9b,.75f);
    private static final DustColorTransitionParticleEffect SPHERE=transition(0xa2f8cb,0x0eaf9b,.55f);
    private static final int[] RING_DELAYS={0,1,2,3,4,5,6,7,8,10,12,15,18};
    private static final double[] RING_RADII={1,2,3,4,5,6,6.5,7,7.4,7.7,7.9,8,8.1};
    private static final int[] SPHERE_AMOUNTS={230,200,170,140,120,100,80,60,40,20};
    private DeathSentencePresentation() { }

    static void started(ServerPlayerEntity player) {
        sphere(player);
        var wings=NativeVisualRuntime.spawnTracked("vfx_death_wings_1@ultimate",player,player.getPos().add(0,1.5,0),114,true,1);
        // Original aura swaps six wing parts through seven authored frames, then reverses.
        for(int age=1;age<=6;age++){final int frame=age+1;SVFrameLibFabricMod.schedule(age,()->NativeVisualRuntime.appearance(wings,"vfx_death_wings_"+frame));}
        for(int age=107;age<=112;age++){final int frame=113-age;SVFrameLibFabricMod.schedule(age,()->NativeVisualRuntime.appearance(wings,"vfx_death_wings_"+frame));}
        sound(player,"minecraft:entity.ender_dragon.flap",.8f,.8f);
    }

    static void tick(ServerPlayerEntity player,int age) {
        if(age==3||age==23||age==55||age==88||age==99)
            sound(player,"minecraft:entity.ender_dragon.flap",.8f,age==99?.7f:.6f);
        if(age>=10&&age<=120){
            Vec3d p=player.getPos().add(player.getRotationVec(1f).multiply(.6)).add(0,.9,0);
            player.getServerWorld().spawnParticles(BODY,p.x,p.y,p.z,30,.35,.6,.35,0);
        }
        if(age==43||age==58||age==73||age==88)sphere(player);
        if(age==93){
            sound(player,"death_knight_sounds:samus.death_knight.death_sentence_smash",.5f,.85f);
            sound(player,"death_knight_sounds:samus.death_knight.soul_blade_slash",.8f,1f);
        }
    }

    static void launched(ServerPlayerEntity player,Vec3d origin) {
        sound(player,"minecraft:entity.breeze.death",1,.7f);
        sound(player,"minecraft:event.mob_effect.raid_omen",.8f,.6f);
        expandingRing(player,origin);
    }

    static void hovering(ServerPlayerEntity player) {
        var sword=NativeVisualRuntime.spawnTracked("vfx_soul_blade@skill",player,player.getPos().add(0,3,0),90,true,1);
        NativeVisualRuntime.visible(sword,false);
        SVFrameLibFabricMod.schedule(42,()->NativeVisualRuntime.visible(sword,true));
        int[] delays={2,4,6,8,10,10,12,12,14,14,10,12,14,16,18,20};
        var forming=transition(0xa2f8cb,0x05090a,1.25f);ServerWorld world=player.getServerWorld();
        for(int part=0;part<delays.length;part++)for(int age=delays[part];age<=42;age++){
            final String name="p"+(part+1);
            SVFrameLibFabricMod.schedule(age,()->{if(!valid(player,world))return;Vec3d point=NativeVisualRuntime.anchor(sword,name);if(point!=null)world.spawnParticles(forming,point.x,point.y,point.z,3,.15,.05,.15,0);});
        }
        SVFrameLibFabricMod.schedule(22,()->{if(valid(player,world))sound(player,"minecraft:event.mob_effect.bad_omen",.9f,.5f);});
    }

    static void descending(ServerPlayerEntity player) {
        sound(player,"minecraft:entity.breeze.death",1,.7f);
        sound(player,"minecraft:event.mob_effect.raid_omen",.8f,.6f);
    }

    static void impacted(ServerPlayerEntity player,Vec3d origin) {
        expandingRing(player,origin);
        sound(player,"minecraft:item.mace.smash_air",1,1);
        sound(player,"minecraft:entity.phantom.hurt",.6f,.7f);
        var rupture=NativeVisualRuntime.spawnTracked("vfx_earthquake_rupture_1@skill2",player,origin,72,false,1.5f);
        int[] tintDelays={0,22,24,26,30,32};int[] colors={0x57ffc1,0x37e6c0,0x22c7b6,0x0b8385,0x035354,0x000000};
        NativeVisualRuntime.tint(rupture,colors[0]);
        for(int i=1;i<tintDelays.length;i++){final int rgb=colors[i];SVFrameLibFabricMod.schedule(tintDelays[i],()->NativeVisualRuntime.tint(rupture,rgb));}
        for(int i=2;i<=5;i++){final int frame=i;SVFrameLibFabricMod.schedule(60+2*i,()->NativeVisualRuntime.appearance(rupture,"vfx_earthquake_rupture_"+frame));}
        ServerWorld world=player.getServerWorld();
        // The source slash persists ten ticks, with 50 points over a 210-degree arc.
        double yaw=Math.toRadians(player.getYaw());
        for(int t=0;t<10;t++){
            final int offset=t;
            SVFrameLibFabricMod.schedule(t,()->{
                if(!valid(player,world))return;
                for(int i=0;i<50;i++){
                    double angle=yaw-Math.toRadians(105)+Math.toRadians(210)*i/49d;
                    emit(world,new DustColorTransitionParticleEffect(color(0xa2f8cb),color(0x0eaf9b),.8f),
                            origin.add(Math.cos(angle)*3.5,1.3+Math.sin(offset*Math.PI/10)*.2,Math.sin(angle)*3.5));
                }
            });
        }
    }

    private static void expandingRing(ServerPlayerEntity player,Vec3d origin) {
        ServerWorld world=player.getServerWorld();
        for(int i=0;i<RING_DELAYS.length;i++){
            final double radius=RING_RADII[i];
            SVFrameLibFabricMod.schedule(RING_DELAYS[i],()->{
                if(!valid(player,world))return;
                for(int point=0;point<80;point++){
                    double a=point*Math.PI*2/80;
                    emit(world,RING,origin.add(Math.cos(a)*radius,.1,Math.sin(a)*radius));
                }
            });
        }
    }

    private static void sphere(ServerPlayerEntity player) {
        ServerWorld world=player.getServerWorld();
        sound(player,"minecraft:entity.breeze.inhale",.8f,.7f);
        sound(player,"minecraft:particle.soul_escape",.8f,1);
        for(int delay=0;delay<10;delay++){
            final int count=SPHERE_AMOUNTS[delay];final double radius=5-delay*.5;
            SVFrameLibFabricMod.schedule(delay,()->{
                if(!valid(player,world)||HeroEntranceRuntime.view(player)==null)return;
                Vec3d center=player.getPos().add(0,.6,0);
                for(int i=0;i<count;i++){
                    double y=1-2*(i+.5)/count,a=i*Math.PI*(3-Math.sqrt(5)),r=Math.sqrt(1-y*y);
                    emit(world,SPHERE,center.add(Math.cos(a)*r*radius,y*radius,Math.sin(a)*r*radius));
                }
            });
        }
    }

    private static boolean valid(ServerPlayerEntity p,ServerWorld w){return p.isAlive()&&!p.isDisconnected()&&p.getServerWorld()==w;}
    private static void emit(ServerWorld world,DustColorTransitionParticleEffect effect,Vec3d position){
        for(ServerPlayerEntity viewer:world.getPlayers())if(viewer.squaredDistanceTo(position)<=80*80)
            world.spawnParticles(viewer,effect,true,position.x,position.y,position.z,1,0,0,0,0);
    }
    private static void sound(ServerPlayerEntity player,String id,float volume,float pitch){
        player.getServerWorld().playSound(null,player.getBlockPos(),SoundEvent.of(Identifier.of(id)),SoundCategory.PLAYERS,volume,pitch);
    }
    private static Vector3f color(int rgb){return new Vector3f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f);}
    private static DustColorTransitionParticleEffect transition(int from,int to,float size){return new DustColorTransitionParticleEffect(color(from),color(to),size);}
}
