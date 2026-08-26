package io.lumine.mythic.lib.message;
import io.lumine.mythic.lib.api.player.MMOPlayerData; import net.minecraft.server.network.ServerPlayerEntity; import net.minecraft.text.Text; import net.minecraft.util.Formatting;
import java.util.*;
public abstract class PlayerMessage {
    private final SoundReader sound;
    public PlayerMessage(){this.sound=null;} public PlayerMessage(Map<String,?> config){this.sound=SoundReader.fromConfig(config==null?null:config.get("sound"));}
    public void send(MMOPlayerData data,Object...args){send(data,(Formatting)null,args);} public void send(MMOPlayerData data,Formatting color,Object...args){if(data==null||!data.isOnline())return;onSend(data,color,args);if(sound!=null)sound.play(data.getPlayer());}
    protected abstract void onSend(MMOPlayerData data,Formatting color,Object...args);
    protected String parsePlaceholders(ServerPlayerEntity player,String input,Formatting color,Object...args){if(input==null)return "";String out=input.replace("{player}",player.getName().getString());for(int i=0;i<args.length;i++)out=out.replace("{"+i+"}",String.valueOf(args[i])).replace("%"+i+"%",String.valueOf(args[i]));if(color!=null)out=color.toString()+out;return out;}
    protected void sendPlayerMessage(ServerPlayerEntity player,String message,boolean rawJson){player.sendMessage(Text.literal(message==null?"":message),false);} protected boolean inferIsRawJson(String s){return s!=null&&(s.trim().startsWith("{")||s.trim().startsWith("["));}
    public static PlayerMessage fromConfig(Object value){if(value==null)return new io.lumine.mythic.lib.message.type.EmptyMessage();if(value instanceof PlayerMessage p)return p;if(value instanceof Map<?,?> m){Map<String,Object>x=new LinkedHashMap<>();m.forEach((k,v)->x.put(String.valueOf(k),v));String msg=String.valueOf(x.getOrDefault("message",x.getOrDefault("text","")));return new TextMessage(x,msg);}return new TextMessage(Map.of(),String.valueOf(value));}
    private static final class TextMessage extends PlayerMessage {private final String message;TextMessage(Map<String,?> c,String m){super(c);message=m;}protected void onSend(MMOPlayerData d,Formatting c,Object...a){String msg=parsePlaceholders(d.getPlayer(),message,c,a);sendPlayerMessage(d.getPlayer(),msg,inferIsRawJson(msg));}}
}
