package vn.svframe.svframelib.player.particle.type;

import vn.svframe.svframelib.player.particle.*;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.Map;

public class AuraParticleEffect extends ParticleEffect {
    private final double speed,height,radius,rSpeed,yOffset,ySpeed; private final int amount; private double a;
    public AuraParticleEffect(String key,Map<String,Double> m,ParticleInformation p){super(key,p);speed=resolveModifier(m,"speed");height=resolveModifier(m,"height");radius=resolveModifier(m,"radius");rSpeed=resolveModifier(m,"rotation-speed");ySpeed=resolveModifier(m,"y-speed");yOffset=resolveModifier(m,"y-offset");amount=(int)resolveModifier(m,"amount");}
    public AuraParticleEffect(ConfigObject o){super(o);speed=o.getDouble("speed");height=o.getDouble("height");radius=o.getDouble("radius");rSpeed=o.getDouble("rotation-speed");ySpeed=o.getDouble("y-speed");yOffset=o.getDouble("y-offset");amount=o.getInt("amount");}
    public ParticleEffectType getType(){return ParticleEffectType.AURA;}
    public void tick(){ServerPlayerEntity pl=playerData.getPlayer();Vec3d base=pl.getPos();int n=Math.max(1,amount);for(int k=0;k<n;k++){double angle=a+Math.PI*2*k/n;particle.display(pl.getServerWorld(),base.add(Math.cos(angle)*radius,Math.sin(a*ySpeed*3)*yOffset+height,Math.sin(angle)*radius),speed);}a+=Math.PI/48*rSpeed;if(ySpeed!=0&&a>Math.PI*2/ySpeed)a-=Math.PI*2/ySpeed;}
}
