package io.lumine.mythic.lib.player.resource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
public class Resources {
    private static volatile HealthUpdateEventSupplier<?> healthEventCaller;
    public static void setResourceEventCaller(HealthUpdateEventSupplier<?> caller){healthEventCaller=caller;}
    public static boolean setHealth(LivingEntity entity,double health){return setHealth(entity,health,ResourceUpdateReason.OTHER);}
    public static boolean setHealth(LivingEntity entity,double health,ResourceUpdateReason reason){
        double old=entity.getHealth(), next=Math.max(0d,Math.min(health,entity.getMaxHealth()));
        if(entity instanceof ServerPlayerEntity player && reason.callsEvent() && healthEventCaller!=null){AbstractHealthUpdateEvent event=healthEventCaller.onHealthUpdate(player,old,next,reason);if(event!=null){if(event.isCancelled())return false;next=Math.max(0d,Math.min(event.getNewAmount(),entity.getMaxHealth()));}}
        entity.setHealth((float)next);return true;
    }
    public static boolean heal(LivingEntity entity,double amount){return heal(entity,amount,ResourceUpdateReason.OTHER);}
    public static boolean heal(LivingEntity entity,double amount,ResourceUpdateReason reason){return setHealth(entity,entity.getHealth()+amount,reason);}
    public static boolean saturate(ServerPlayerEntity player,double amount){return saturate(player,amount,true);}
    public static boolean saturate(ServerPlayerEntity player,double amount,boolean clamp){float next=player.getHungerManager().getSaturationLevel()+(float)amount;if(clamp)next=Math.max(0f,Math.min(next,20f));player.getHungerManager().setSaturationLevel(next);return true;}
    public static boolean feed(ServerPlayerEntity player,int amount){return feed(player,amount,true);}
    public static boolean feed(ServerPlayerEntity player,int amount,boolean clamp){int next=player.getHungerManager().getFoodLevel()+amount;if(clamp)next=Math.max(0,Math.min(next,20));player.getHungerManager().setFoodLevel(next);return true;}
}
