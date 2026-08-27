package vn.svframe.svframeitems;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import vn.svframe.svframeitems.command.SVFrameItemsCommands;
import vn.svframe.svframeitems.config.DefaultFiles;
import vn.svframe.svframeitems.item.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;
import vn.svframe.svframeitems.runtime.*;

import java.io.IOException;
import java.util.logging.*;

public final class SVFrameItems implements ModInitializer {
    public static final String ID="svframeitems";
    private static final Logger LOG=Logger.getLogger("SVFrameItems");
    private static final SVFrameItemsRegistry REGISTRY=new SVFrameItemsRegistry();
    private static final ItemFormatter FORMATTER=new ItemFormatter();
    private static final ItemGenerator GENERATOR=new ItemGenerator(REGISTRY,FORMATTER);
    private static final UpgradeService UPGRADES=new UpgradeService(REGISTRY,GENERATOR);
    private static final SocketService SOCKETS=new SocketService(REGISTRY,GENERATOR);
    private static final LootService LOOT=new LootService(REGISTRY,GENERATOR);
    private static final RecipeService RECIPES=new RecipeService(REGISTRY,GENERATOR);
    private static final AbilityRuntime ABILITIES=new AbilityRuntime(REGISTRY);
    private static final EquipmentRuntime EQUIPMENT=new EquipmentRuntime(REGISTRY,UPGRADES,ABILITIES);
    private static volatile MinecraftServer server;

    @Override public void onInitialize(){
        try{DefaultFiles.ensure();REGISTRY.reload(DefaultFiles.root());}catch(IOException|RuntimeException exception){throw new IllegalStateException("Could not initialize SVFrameItems definitions",exception);}
        ABILITIES.initialize();
        ServerTickEvents.END_SERVER_TICK.register(value->EQUIPMENT.tick(value));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,value)->EQUIPMENT.clear(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(value->{server=value;LOG.info("SVFrameItems Fabric online; "+REGISTRY.summary());});
        ServerLifecycleEvents.SERVER_STOPPING.register(value->{EQUIPMENT.clear();server=null;});
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->SVFrameItemsCommands.register(dispatcher));
    }

    public static synchronized boolean reload(){try{REGISTRY.reload(DefaultFiles.root());MinecraftServer current=server;if(current!=null)for(var player:current.getPlayerManager().getPlayerList())EQUIPMENT.refresh(player);return true;}catch(Exception exception){LOG.log(Level.SEVERE,"SVFrameItems reload failed; keeping previous registry snapshot",exception);return false;}}
    public static SVFrameItemsRegistry registry(){return REGISTRY;} public static ItemGenerator generator(){return GENERATOR;} public static UpgradeService upgrades(){return UPGRADES;} public static SocketService sockets(){return SOCKETS;} public static LootService loot(){return LOOT;} public static RecipeService recipes(){return RECIPES;} public static EquipmentRuntime equipment(){return EQUIPMENT;} public static MinecraftServer server(){return server;}
}
