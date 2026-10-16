package vn.svframe.svframemobs.mythic;

import java.util.function.BooleanSupplier;
import static vn.svframe.svframemobs.mythic.MythicEngine.*;

/** Fixed-location Mythic totem lifecycle; expiry is checked on the configured interval. */
final class AnchoredTotem {
    static void start(MythicEngine engine,Context context,int duration,int interval,
                      BooleanSupplier valid,Runnable onStart,Runnable onTick,Runnable onEnd) {
        if(duration<1||interval<1)throw new IllegalArgumentException("Invalid totem timer");
        onStart.run();
        engine.schedule(1,context,()->advance(engine,context,duration,interval,valid,onTick,onEnd));
    }
    private static void advance(MythicEngine engine,Context context,int remaining,int interval,
                                BooleanSupplier valid,Runnable onTick,Runnable onEnd) {
        if(remaining<=0||!valid.getAsBoolean()){onEnd.run();return;}
        onTick.run();
        engine.schedule(interval,context,()->advance(engine,context,remaining-interval,interval,valid,onTick,onEnd));
    }
}
