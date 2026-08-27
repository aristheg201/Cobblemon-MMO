package vn.svframe.svframeitems;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import vn.svframe.svframeitems.command.SVFrameItemsCommands;
import vn.svframe.svframeitems.config.DefaultFiles;
import vn.svframe.svframeitems.item.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;
import vn.svframe.svframeitems.runtime.*;
import vn.svframe.svframeitems.validation.NativeRuntimeSmoke;

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
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->EQUIPMENT.refresh(newPlayer,true));
        ServerLifecycleEvents.SERVER_STARTED.register(value->{RuntimeDefinitionValidator.validate(REGISTRY,UPGRADES,LOOT);server=value;NativeRuntimeSmoke.runIfRequested();LOG.info("SVFrameItems Fabric online; "+REGISTRY.summary());});
        ServerLifecycleEvents.SERVER_STOPPING.register(value->{EQUIPMENT.clear();server=null;});
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->SVFrameItemsCommands.register(dispatcher));
    }

    public static synchronized boolean reload(){
        SVFrameItemsRegistry.Snapshot before=REGISTRY.snapshot(); MinecraftServer current=server;
        try{
            REGISTRY.reload(DefaultFiles.root()); RuntimeDefinitionValidator.validate(REGISTRY,UPGRADES,LOOT);
            if(current!=null)for(var player:current.getPlayerManager().getPlayerList())EQUIPMENT.refresh(player,true);
            return true;
        }catch(Exception exception){
            REGISTRY.restore(before);
            if(current!=null)for(var player:current.getPlayerManager().getPlayerList())try{EQUIPMENT.refresh(player,true);}catch(RuntimeException rollback){exception.addSuppressed(rollback);}
            LOG.log(Level.SEVERE,"SVFrameItems reload failed; restored previous registry snapshot",exception);return false;
        }
    }
    public static SVFrameItemsRegistry registry(){return REGISTRY;} public static ItemGenerator generator(){return GENERATOR;} public static UpgradeService upgrades(){return UPGRADES;} public static SocketService sockets(){return SOCKETS;} public static LootService loot(){return LOOT;} public static RecipeService recipes(){return RECIPES;} public static EquipmentRuntime equipment(){return EQUIPMENT;} public static MinecraftServer server(){return server;}
}
