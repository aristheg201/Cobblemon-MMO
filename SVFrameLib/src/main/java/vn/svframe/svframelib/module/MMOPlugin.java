package vn.svframe.svframelib.module;
import java.util.Locale;import java.util.logging.Logger;
public abstract class MMOPlugin {private final Logger logger=Logger.getLogger(getClass().getSimpleName());public boolean hasData(){return true;}public boolean isProfilePlugin(){return false;}public String getNamespacedKey(){return getClass().getSimpleName().toLowerCase(Locale.ROOT);}public void debug(String message){logger.info(message);}public void debug(String context,String message){logger.info("["+context+"] "+message);}public Logger logger(){return logger;}}
