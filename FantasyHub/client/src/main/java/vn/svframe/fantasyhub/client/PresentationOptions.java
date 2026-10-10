package vn.svframe.fantasyhub.client;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.IOException;
import java.util.logging.*;

/** Local presentation settings. None of these options change gameplay resources. */
final class PresentationOptions {
    boolean enabled=true;
    float scale=.8f;
    boolean right=false;
    boolean compact=true;
    boolean health=true,mana=true,stamina=true,magic=true;
    private static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("fantasyhub-client.json");}
    static PresentationOptions load(){
        try{
            if(!Files.exists(path()))return new PresentationOptions();
            var options=new com.google.gson.Gson().fromJson(Files.readString(path()),PresentationOptions.class);
            if(options==null)return new PresentationOptions();
            options.scale=Float.isFinite(options.scale)?Math.max(.5f,Math.min(2,options.scale)):1;
            return options;
        }catch(IOException|RuntimeException failure){Logger.getLogger("FantasyHub-Client").log(Level.WARNING,"Could not load presentation options",failure);return new PresentationOptions();}
    }
    void save(){
        try{
            Files.createDirectories(path().getParent());
            Path temporary=path().resolveSibling(path().getFileName()+".tmp");
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(this));
            try{Files.move(temporary,path(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temporary,path(),StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException failure){Logger.getLogger("FantasyHub-Client").log(Level.WARNING,"Could not save presentation options",failure);}
    }
    boolean visible(String resource){return switch(resource){case "health"->health;case "mana"->mana;case "stamina"->stamina;case "magic"->magic;default->false;};}
    void toggle(String resource){switch(resource){case "health"->health=!health;case "mana"->mana=!mana;case "stamina"->stamina=!stamina;case "magic"->magic=!magic;default->throw new IllegalArgumentException("Unknown HUD resource");}save();}
}
