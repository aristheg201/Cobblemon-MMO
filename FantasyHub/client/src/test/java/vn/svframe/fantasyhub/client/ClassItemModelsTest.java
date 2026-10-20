package vn.svframe.fantasyhub.client;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClassItemModelsTest {
  @Test void heroArmorInventoryModelsAreAccepted(){assertTrue(ClassItemModels.supports(Identifier.of("fantasyhub","item/heroes/hero_thor_chestplate")));}
  @Test void unrelatedNamespaceCannotOverrideItemModel(){assertFalse(ClassItemModels.supports(Identifier.of("minecraft","item/heroes/hero_thor_chestplate")));assertFalse(ClassItemModels.supports(Identifier.of("fantasyhub","textures/item/heroes/test")));}
}
