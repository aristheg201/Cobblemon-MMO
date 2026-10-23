package vn.svframe.fantasyhub.protocol;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
/** Common collision contract. The server resolves the active native class; client only predicts it. */
public final class FlashMovement {
 public static Predicate<PlayerEntity> server=p->false,client=p->false;
 public static boolean waterWalking(PlayerEntity p){return !p.isSpectator()&&!p.hasVehicle()&&!p.isSneaking()&&!p.isSwimming()&&!p.isSubmergedInWater()&&(p.getWorld().isClient?client:server).test(p);}
}
