package vn.svframe.fantasyhub.core.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class OptionalCompatibilityPlugin implements IMixinConfigPlugin {
  public void onLoad(String pkg) {}
  public String getRefMapperConfig() { return null; }
  public boolean shouldApplyMixin(String target, String mixin) {
    return !mixin.endsWith("FilamentCosmeticMixin") || FabricLoader.getInstance().isModLoaded("filament");
  }
  public void acceptTargets(Set<String> own, Set<String> other) {}
  public List<String> getMixins() { return null; }
  public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
  public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
