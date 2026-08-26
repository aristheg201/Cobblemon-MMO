package io.lumine.mythic.lib.version;
import net.minecraft.particle.*; import net.minecraft.server.world.ServerWorld; import net.minecraft.util.math.Vec3d;
public enum VParticle {
 EXPLOSION(ParticleTypes.EXPLOSION), LARGE_EXPLOSION(ParticleTypes.EXPLOSION_EMITTER), WITCH(ParticleTypes.WITCH,true), LARGE_SMOKE(ParticleTypes.LARGE_SMOKE), SMOKE(ParticleTypes.SMOKE),
 REDSTONE(ParticleTypes.DUST), FIREWORK(ParticleTypes.FIREWORK), INSTANT_EFFECT(ParticleTypes.INSTANT_EFFECT,true), EFFECT(ParticleTypes.EFFECT,true), ITEM_SNOWBALL(ParticleTypes.ITEM_SNOWBALL),
 ENTITY_EFFECT(ParticleTypes.ENTITY_EFFECT,true), ENTITY_EFFECT_AMBIENT(ParticleTypes.ENTITY_EFFECT,true), TOTEM_OF_UNDYING(ParticleTypes.TOTEM_OF_UNDYING), HAPPY_VILLAGER(ParticleTypes.HAPPY_VILLAGER),
 SNOWFLAKE(ParticleTypes.SNOWFLAKE), BLOCK(ParticleTypes.BLOCK), BLOCK_DUST(ParticleTypes.BLOCK), ITEM_SLIME(ParticleTypes.ITEM_SLIME), ENCHANTED_HIT(ParticleTypes.ENCHANTED_HIT), ITEM(ParticleTypes.ITEM);
 public final boolean spell; private final ParticleType<?> wrapped;
 VParticle(ParticleType<?> type){this(type,false);} VParticle(ParticleType<?> type,boolean spell){wrapped=type;this.spell=spell;}
 public ParticleType<?> get(){return wrapped;}
 public void spawnSafeSpell(ServerWorld world,Vec3d pos){spawnSafeSpell(world,pos,1,0,0,0,0);}
 public void spawnSafeSpell(ServerWorld world,Vec3d pos,int count,double dx,double dy,double dz,double speed){if(wrapped instanceof SimpleParticleType effect)world.spawnParticles(effect,pos.x,pos.y,pos.z,count,dx,dy,dz,speed);}
}
