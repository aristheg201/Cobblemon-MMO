package vn.svframe.fantasyhub.protocol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.item.Item;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;

/** Versioned requests and immutable server snapshots. No client damage/resource values accepted. */
public final class Protocol implements ModInitializer {
    private static boolean installed;
    public static final int VERSION=1;
    public static final String ASSET_REVISION="reference-classes-v3";
    public static final Identifier LOGIN=Identifier.of("fantasyhub","login_v1");
    public static Item visualBone;
    public record State(String json) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("fantasyhub","state_v1"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.of((p,b)->b.writeString(p.json,262144),b->new State(b.readString(262144)));
        @Override public Id<State> getId(){return ID;}
    }
    public record Action(String action,String id,int slot) implements CustomPayload {
        public static final Id<Action> ID=new Id<>(Identifier.of("fantasyhub","action_v1"));
        public static final PacketCodec<RegistryByteBuf,Action> CODEC=PacketCodec.of((p,b)->{b.writeString(p.action,32);b.writeString(p.id,128);b.writeVarInt(p.slot);},b->new Action(b.readString(32),b.readString(128),b.readVarInt()));
        @Override public Id<Action> getId(){return ID;}
    }
    public record Open(String screen) implements CustomPayload {
        public static final Id<Open> ID=new Id<>(Identifier.of("fantasyhub","open_v1"));
        public static final PacketCodec<RegistryByteBuf,Open> CODEC=PacketCodec.of((p,b)->b.writeString(p.screen,32),b->new Open(b.readString(32)));
        @Override public Id<Open> getId(){return ID;}
    }
    /** QA observation only. The server never consumes these as gameplay state. */
    public record Observation(String json) implements CustomPayload {
        public static final Id<Observation> ID=new Id<>(Identifier.of("fantasyhub","observation_v1"));
        public static final PacketCodec<RegistryByteBuf,Observation> CODEC=PacketCodec.of((p,b)->b.writeString(p.json,4096),b->new Observation(b.readString(4096)));
        @Override public Id<Observation> getId(){return ID;}
    }
    @Override public void onInitialize(){install();}
    public static synchronized void install(){
        if(installed)return;
        visualBone=Registry.register(Registries.ITEM,Identifier.of("fantasyhub","visual_bone"),new Item(new Item.Settings()));
        PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
        PayloadTypeRegistry.playS2C().register(Open.ID,Open.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.ID,Action.CODEC);
        PayloadTypeRegistry.playC2S().register(Observation.ID,Observation.CODEC);
        installed=true;
    }
}
