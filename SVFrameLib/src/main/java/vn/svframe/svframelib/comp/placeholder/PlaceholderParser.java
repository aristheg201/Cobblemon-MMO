package vn.svframe.svframelib.comp.placeholder;
import net.minecraft.server.network.ServerPlayerEntity; import java.util.UUID;
@FunctionalInterface public interface PlaceholderParser {
    String parse(UUID playerId,String input);
    default String parse(ServerPlayerEntity player,String input){return parse(player==null?null:player.getUuid(),input);}
}
