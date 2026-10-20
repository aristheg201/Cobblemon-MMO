package vn.svframe.fantasyhub.client.mixin;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Do not resolve optional mod targets on clients where that mod is absent. */
public final class ClientCompatMixinPlugin implements IMixinConfigPlugin {
  public void onLoad(String pkg) {}
  public String getRefMapperConfig(){return null;}
  public boolean shouldApplyMixin(String target,String mixin){return !mixin.substring(mixin.lastIndexOf('.')+1).startsWith("BetterCombat")||FabricLoader.getInstance().isModLoaded("bettercombat");}
  public void acceptTargets(Set<String> mine,Set<String> others) {}
  public List<String> getMixins(){return null;}
  public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
  public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
