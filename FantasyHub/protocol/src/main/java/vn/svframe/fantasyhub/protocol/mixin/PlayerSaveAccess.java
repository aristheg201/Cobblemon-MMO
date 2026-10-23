package vn.svframe.fantasyhub.protocol.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.server.PlayerManager;import net.minecraft.server.network.ServerPlayerEntity;
@Mixin(PlayerManager.class)
public interface PlayerSaveAccess {@Invoker("savePlayerData") void svframe$savePlayer(ServerPlayerEntity player);}
