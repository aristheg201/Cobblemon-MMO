package vn.svframe.fantasyhub.client;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.entity.LivingEntity;
class HeroArmorStateTest {
  @Test void adultArmorCopiesAgeAndLocomotionRatherThanRetainingDefaultBabyScaling() throws Exception {
    var source=new PlayerEntityModel<LivingEntity>(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(new Dilation(0),false),64,64).createModel(),false);
    source.child=false;source.riding=true;source.handSwingProgress=.7f;source.sneaking=true;
    var copy=HeroArmorFeature.class.getDeclaredMethod("copy",BipedEntityModel.class);copy.setAccessible(true);copy.invoke(null,source);
    var field=HeroArmorFeature.class.getDeclaredField("SUIT");field.setAccessible(true);var suit=(PlayerEntityModel<?>)field.get(null);
    assertFalse(suit.child,"Default EntityModel.child=true shrinks armor inside the adult player's body");assertTrue(suit.riding);assertEquals(.7f,suit.handSwingProgress);assertTrue(suit.sneaking);
  }
}
