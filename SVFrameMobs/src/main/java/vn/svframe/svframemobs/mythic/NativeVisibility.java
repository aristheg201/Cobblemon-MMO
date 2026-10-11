package vn.svframe.svframemobs.mythic;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemobs.mixin.*;
import java.util.*;

/** Authoritative observer-specific hiding. Vanilla tracking restores real armor and equipment on expiry. */
public final class NativeVisibility {
    private record Pair(UUID hidden,UUID observer){}
    private static final Map<Pair,Long> hidden=new HashMap<>();
    private static long tick;
    private NativeVisibility(){}
    public static void hide(ServerPlayerEntity actor,ServerPlayerEntity observer,long until) {
        hidden.merge(new Pair(actor.getUuid(),observer.getUuid()),until,Math::max);
        observer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(actor.getId()));
    }
    public static boolean hidden(Entity actor,ServerPlayerEntity observer){return hidden.getOrDefault(new Pair(actor.getUuid(),observer.getUuid()),0L)>tick;}
    public static void tick(MinecraftServer server,long now) {
        tick=now;if(server==null)return;
        List<Pair> expired=hidden.entrySet().stream().filter(e->e.getValue()<=now).map(Map.Entry::getKey).toList();
        for(Pair pair:expired) {
            hidden.remove(pair);
            ServerPlayerEntity actor=server.getPlayerManager().getPlayer(pair.hidden()),observer=server.getPlayerManager().getPlayer(pair.observer());
            if(actor==null||observer==null||actor.getServerWorld()!=observer.getServerWorld())continue;
            var trackers=((ChunkTrackingAccessor)actor.getServerWorld().getChunkManager().chunkLoadingManager).svframe$trackers();
            Object tracker=trackers.get(actor.getId());
            if(tracker instanceof EntityTrackingAccessor access&&access.svframe$listeners().contains(observer.networkHandler))
                access.svframe$entry().sendPackets(observer,observer.networkHandler::sendPacket);
        }
    }
    public static void clear(){hidden.clear();tick=0;}
}
