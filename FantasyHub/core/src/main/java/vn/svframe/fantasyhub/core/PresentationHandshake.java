package vn.svframe.fantasyhub.core;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;
import vn.svframe.fantasyhub.protocol.Protocol;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Configuration tasks hold entry to the world until presentation is verified, including proxy routes. */
final class PresentationHandshake {
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger("FantasyHub/Handshake");
    private static final ServerPlayerConfigurationTask.Key KEY=new ServerPlayerConfigurationTask.Key("fantasyhub:presentation");
    private static final Set<ServerConfigurationNetworkHandler> pending=ConcurrentHashMap.newKeySet();
    static void install() {
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler,server)->pending.remove(handler));
        ServerConfigurationNetworking.registerGlobalReceiver(Protocol.Handshake.ID,(reply,context)->{
            var handler=context.networkHandler();
            if(!pending.remove(handler)) {
                handler.disconnect(Text.translatableWithFallback("fantasyhub.connection.unexpected", "Unexpected Fantasy Hub handshake reply [FH_UNEXPECTED_REPLY]."));return;
            }
            if(!"0.1.3".equals(reply.clientVersion())||reply.version()!=Protocol.VERSION||!Protocol.ASSET_REVISION.equals(reply.assets())||!reply.diagnostic().isEmpty()) {
                String detail="client="+reply.clientVersion()+", protocol="+reply.version()+", assets="+reply.assets()+", "+reply.diagnostic();
                LOG.warn("Rejected presentation handshake for {}: {}",handler.getDebugProfile().getName(),detail);
                handler.disconnect(Text.translatableWithFallback("fantasyhub.connection.handshake_failed", "Fantasy Hub client loaded, but presentation verification failed [FH_VERIFY_FAILED]: %s",detail));return;
            }
            LOG.info("Configuration handshake accepted for {}: client={}, protocol={}, assets={}",handler.getDebugProfile().getName(),reply.clientVersion(),reply.version(),reply.assets());
            handler.completeTask(KEY);
        });
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler,server)->{
            if(!ServerConfigurationNetworking.canSend(handler,Protocol.Handshake.ID)) {
                LOG.warn("Missing configuration handshake channel for {}",handler.getDebugProfile().getName());
                handler.disconnect(Text.translatableWithFallback("fantasyhub.connection.channel_missing", "The player's game did not register the Fantasy Hub configuration channel [FH_CHANNEL_MISSING]. Install FantasyHubClient 0.1.3 on the player's game. If already installed, check proxy configuration-packet forwarding."));return;
            }
            handler.addTask(new ServerPlayerConfigurationTask() {
                @Override public Key getKey(){return KEY;}
                @Override public void sendPacket(Consumer<Packet<?>> sender) {
                    pending.add(handler);
                    sender.accept(ServerConfigurationNetworking.createS2CPacket(new Protocol.Handshake(Protocol.VERSION,Protocol.ASSET_REVISION,"0.1.3","")));
                }
            });
        });
    }
}
