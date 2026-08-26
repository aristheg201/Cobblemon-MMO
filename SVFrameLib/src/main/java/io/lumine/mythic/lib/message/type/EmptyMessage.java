package io.lumine.mythic.lib.message.type;
import io.lumine.mythic.lib.api.player.MMOPlayerData; import io.lumine.mythic.lib.message.PlayerMessage; import net.minecraft.util.Formatting; import java.util.Map;
public class EmptyMessage extends PlayerMessage { public EmptyMessage(){} public EmptyMessage(Map<String,?> config){super(config);} @Override protected void onSend(MMOPlayerData data,Formatting color,Object...args){} }
