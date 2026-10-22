package vn.svframe.fantasyhub.protocol;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.data.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
/** A real server-controlled humanoid with its own hitbox, navigation, class and equipment. */
public final class HeroNpcEntity extends PathAwareEntity {
 private static final TrackedData<String> HERO=DataTracker.registerData(HeroNpcEntity.class,TrackedDataHandlerRegistry.STRING);
 public HeroNpcEntity(EntityType<? extends PathAwareEntity> type,World world){super(type,world);setPersistent();}
 @Override protected void initDataTracker(DataTracker.Builder builder){super.initDataTracker(builder);builder.add(HERO,"THOR");}
 public String hero(){return dataTracker.get(HERO);}
 public void hero(String value){dataTracker.set(HERO,value);}
 public static DefaultAttributeContainer.Builder attributes(){return createMobAttributes().add(EntityAttributes.GENERIC_MAX_ABSORPTION,2000).add(EntityAttributes.GENERIC_MAX_HEALTH,1200).add(EntityAttributes.GENERIC_MOVEMENT_SPEED,.28).add(EntityAttributes.GENERIC_ATTACK_DAMAGE,12).add(EntityAttributes.GENERIC_FOLLOW_RANGE,40).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,.8);}
 @Override public void writeCustomDataToNbt(NbtCompound nbt){super.writeCustomDataToNbt(nbt);nbt.putString("HeroClass",hero());}
 @Override public void readCustomDataFromNbt(NbtCompound nbt){super.readCustomDataFromNbt(nbt);if(nbt.contains("HeroClass"))hero(nbt.getString("HeroClass"));}
 @Override protected void initGoals(){goalSelector.add(8,new net.minecraft.entity.ai.goal.LookAtEntityGoal(this,net.minecraft.entity.player.PlayerEntity.class,32));}
 @Override protected boolean canStartRiding(Entity entity){return false;}
}
