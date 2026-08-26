package vn.svframe.svframelib.message.type;
import vn.svframe.svframelib.api.player.MMOPlayerData; import vn.svframe.svframelib.message.PlayerMessage; import net.minecraft.util.Formatting; import java.util.Map;
public class EmptyMessage extends PlayerMessage { public EmptyMessage(){} public EmptyMessage(Map<String,?> config){super(config);} @Override protected void onSend(MMOPlayerData data,Formatting color,Object...args){} }
