package vn.svframe.fantasyhub.protocol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Versioned requests and immutable server snapshots. No client damage/resource values accepted. */
public final class Protocol implements ModInitializer {
  public static Item batmanCape, classSigil, grapplingHook;
  public static net.minecraft.entity.EntityType<HeroNpcEntity> heroNpcType;
  public static final java.util.Map<String,Item> infinityItems=new java.util.LinkedHashMap<>();
  private static boolean installed;
  public static final int VERSION = 3;
  public static final String ASSET_REVISION = "heroes-infinity-v6";

  public record Handshake(int version, String assets, String clientVersion, String diagnostic)
      implements CustomPayload {
    public static final Id<Handshake> ID = new Id<>(Identifier.of("fantasyhub", "handshake_v1"));
    public static final PacketCodec<PacketByteBuf, Handshake> CODEC =
        PacketCodec.of(
            (p, b) -> {
              b.writeVarInt(p.version);
              b.writeString(p.assets, 128);
              b.writeString(p.clientVersion, 32);
              b.writeString(p.diagnostic, 512);
            },
            b ->
                new Handshake(
                    b.readVarInt(), b.readString(128), b.readString(32), b.readString(512)));

    @Override
    public Id<Handshake> getId() {
      return ID;
    }
  }

  public static Item visualBone,
      dragonbornHat,
      kabutoHat,
      zecterBelt,
      kunaiGun,
      stormbreaker,
      thorCape,
      levitationCloak,
      agitoBelt,
      zoldaBelt,
      ryukiBelt,
      knightBelt,
      decadeBelt;

  /** Only the server publishes form changes; clients never choose visual gameplay state. */
  public record Kabuto(
      int entityId, int form, boolean clock, boolean hyper, int energy, int gauge, int clockTicks)
      implements CustomPayload {
    public static final Id<Kabuto> ID = new Id<>(Identifier.of("fantasyhub", "kabuto_v1"));
    public static final PacketCodec<RegistryByteBuf, Kabuto> CODEC =
        PacketCodec.of(
            (p, b) -> {
              b.writeVarInt(p.entityId);
              b.writeVarInt(p.form);
              b.writeBoolean(p.clock);
              b.writeBoolean(p.hyper);
              b.writeVarInt(p.energy);
              b.writeVarInt(p.gauge);
              b.writeVarInt(p.clockTicks);
            },
            b ->
                new Kabuto(
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readBoolean(),
                    b.readBoolean(),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readVarInt()));

    @Override
    public Id<Kabuto> getId() {
      return ID;
    }
  }

  /** A form snapshot is presentation only; counters and form choice are server-owned. */
  public record Rider(int entityId, String form, int force, int remaining)
      implements CustomPayload {
    public static final Id<Rider> ID = new Id<>(Identifier.of("fantasyhub", "rider_v1"));
    public static final PacketCodec<RegistryByteBuf, Rider> CODEC =
        PacketCodec.of(
            (p, b) -> {
              b.writeVarInt(p.entityId);
              b.writeString(p.form, 24);
              b.writeVarInt(p.force);
              b.writeVarInt(p.remaining);
            },
            b -> new Rider(b.readVarInt(), b.readString(24), b.readVarInt(), b.readVarInt()));

    @Override
    public Id<Rider> getId() {
      return ID;
    }
  }

  public record RiderFx(
      int entityId,
      String rider,
      String phase,
      double x,
      double y,
      double z,
      double tx,
      double ty,
      double tz,
      int duration,
      int count)
      implements CustomPayload {
    public RiderFx(
        int entityId,
        String rider,
        String phase,
        double x,
        double y,
        double z,
        double tx,
        double ty,
        double tz,
        int duration) {
      this(entityId, rider, phase, x, y, z, tx, ty, tz, duration, 0);
    }

    public static final Id<RiderFx> ID = new Id<>(Identifier.of("fantasyhub", "rider_fx_v2"));
    public static final PacketCodec<RegistryByteBuf, RiderFx> CODEC =
        PacketCodec.of(
            (p, b) -> {
              b.writeVarInt(p.entityId);
              b.writeString(p.rider, 16);
              b.writeString(p.phase, 32);
              b.writeDouble(p.x);
              b.writeDouble(p.y);
              b.writeDouble(p.z);
              b.writeDouble(p.tx);
              b.writeDouble(p.ty);
              b.writeDouble(p.tz);
              b.writeVarInt(p.duration);
              b.writeVarInt(p.count);
            },
            b ->
                new RiderFx(
                    b.readVarInt(),
                    b.readString(16),
                    b.readString(32),
                    b.readDouble(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readVarInt(),
                    b.readVarInt()));

    @Override
    public Id<RiderFx> getId() {
      return ID;
    }
  }

  /** One spawn and one terminal event; collision and damage remain entirely server-owned. */
  public record RiderProjectile(
      int entityId,
      int token,
      boolean active,
      boolean fire,
      double x,
      double y,
      double z,
      float vx,
      float vy,
      float vz,
      int lifetime)
      implements CustomPayload {
    public static final Id<RiderProjectile> ID =
        new Id<>(Identifier.of("fantasyhub", "rider_projectile_v1"));
    public static final PacketCodec<RegistryByteBuf, RiderProjectile> CODEC =
        PacketCodec.of(
            (p, b) -> {
              b.writeVarInt(p.entityId);
              b.writeVarInt(p.token);
              b.writeBoolean(p.active);
              b.writeBoolean(p.fire);
              b.writeDouble(p.x);
              b.writeDouble(p.y);
              b.writeDouble(p.z);
              b.writeFloat(p.vx);
              b.writeFloat(p.vy);
              b.writeFloat(p.vz);
              b.writeVarInt(p.lifetime);
            },
            b ->
                new RiderProjectile(
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readBoolean(),
                    b.readBoolean(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readFloat(),
                    b.readFloat(),
                    b.readFloat(),
                    b.readVarInt()));

    @Override
    public Id<RiderProjectile> getId() {
      return ID;
    }
  }

  public record KabutoCue(int entityId, String ability, int duration) implements CustomPayload {
    public static final Id<KabutoCue> ID = new Id<>(Identifier.of("fantasyhub", "kabuto_cue_v1"));
    public static final PacketCodec<RegistryByteBuf, KabutoCue> CODEC =
        PacketCodec.of(
            (p, b) -> {
              b.writeVarInt(p.entityId);
              b.writeString(p.ability, 32);
              b.writeVarInt(p.duration);
            },
            b -> new KabutoCue(b.readVarInt(), b.readString(32), b.readVarInt()));

    @Override
    public Id<KabutoCue> getId() {
      return ID;
    }
  }

  /** Semantic presentation only: positions/timing come from accepted server actions. */
  public record KabutoFx(
      int entityId,
      String phase,
      double x,
      double y,
      double z,
      double tx,
      double ty,
      double tz,
      boolean hyper,
      int duration,
      float radius,
      float density,
      int backends)
      implements CustomPayload {
    public static final Id<KabutoFx> ID = new Id<>(Identifier.of("fantasyhub", "kabuto_fx_v1"));
    public static final PacketCodec<RegistryByteBuf, KabutoFx> CODEC =
        PacketCodec.of(
            (p, b) -> {
              b.writeVarInt(p.entityId);
              b.writeString(p.phase, 24);
              b.writeDouble(p.x);
              b.writeDouble(p.y);
              b.writeDouble(p.z);
              b.writeDouble(p.tx);
              b.writeDouble(p.ty);
              b.writeDouble(p.tz);
              b.writeBoolean(p.hyper);
              b.writeVarInt(p.duration);
              b.writeFloat(p.radius);
              b.writeFloat(p.density);
              b.writeVarInt(p.backends);
            },
            b ->
                new KabutoFx(
                    b.readVarInt(),
                    b.readString(24),
                    b.readDouble(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readDouble(),
                    b.readBoolean(),
                    b.readVarInt(),
                    b.readFloat(),
                    b.readFloat(),
                    b.readVarInt()));

    @Override
    public Id<KabutoFx> getId() {
      return ID;
    }
  }

  /** Compact server-only presentation snapshots/cues, independent of saved player data. */
  public record Hero(String json) implements CustomPayload {
    public static final Id<Hero> ID=new Id<>(Identifier.of("fantasyhub","hero_visual_v1"));
    public static final PacketCodec<RegistryByteBuf,Hero> CODEC=PacketCodec.of((p,b)->b.writeString(p.json,12288),b->new Hero(b.readString(12288)));
    public Id<Hero> getId(){return ID;}
  }
  public record State(String json) implements CustomPayload {
    public static final Id<State> ID = new Id<>(Identifier.of("fantasyhub", "state_v2"));
    public static final PacketCodec<RegistryByteBuf, State> CODEC =
        PacketCodec.of(
            (p, b) -> b.writeByteArray(StateCompression.encode(p.json)),
            b ->
                new State(
                    StateCompression.decode(b.readByteArray(StateCompression.MAX_WIRE_BYTES))));

    @Override
    public Id<State> getId() {
      return ID;
    }
  }

  /** Small authoritative casting updates are independent from the GUI catalog snapshot. */
  public record Casting(String json) implements CustomPayload {
    public static final Id<Casting> ID = new Id<>(Identifier.of("fantasyhub", "casting_v1"));
    public static final PacketCodec<RegistryByteBuf, Casting> CODEC =
        PacketCodec.of(
            (p, b) -> b.writeString(p.json, 16384), b -> new Casting(b.readString(16384)));

    @Override
    public Id<Casting> getId() {
      return ID;
    }
  }

  public record Action(String action, String id, int slot) implements CustomPayload {
    public static final Id<Action> ID = new Id<>(Identifier.of("fantasyhub", "action_v1"));
    public static final PacketCodec<RegistryByteBuf, Action> CODEC =
        PacketCodec.of(
            (p, b) -> {
              b.writeString(p.action, 32);
              b.writeString(p.id, 128);
              b.writeVarInt(p.slot);
            },
            b -> new Action(b.readString(32), b.readString(128), b.readVarInt()));

    @Override
    public Id<Action> getId() {
      return ID;
    }
  }

  public record Open(String screen) implements CustomPayload {
    public static final Id<Open> ID = new Id<>(Identifier.of("fantasyhub", "open_v1"));
    public static final PacketCodec<RegistryByteBuf, Open> CODEC =
        PacketCodec.of((p, b) -> b.writeString(p.screen, 32), b -> new Open(b.readString(32)));

    @Override
    public Id<Open> getId() {
      return ID;
    }
  }

  /** QA observation only. The server never consumes these as gameplay state. */
  public record Observation(String json) implements CustomPayload {
    public static final Id<Observation> ID =
        new Id<>(Identifier.of("fantasyhub", "observation_v1"));
    public static final PacketCodec<RegistryByteBuf, Observation> CODEC =
        PacketCodec.of(
            (p, b) -> b.writeString(p.json, 4096), b -> new Observation(b.readString(4096)));

    @Override
    public Id<Observation> getId() {
      return ID;
    }
  }

  @Override
  public void onInitialize() {
    install();
  }

  public static synchronized void install() {
    if (installed) return;
    ((HealthAttributeAccess)(Object)net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH.value()).svframe$maximum(1_000_000);
    for(String stone:java.util.List.of("space","power","reality","mind","soul","time","gauntlet"))infinityItems.put(stone,Registry.register(Registries.ITEM,Identifier.of("fantasyhub",stone.equals("gauntlet")?"infinity_gauntlet":stone+"_stone"),new InfinityItem(stone)));
    net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.COMBAT).register(entries->infinityItems.values().forEach(entries::add));
    visualBone =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "visual_bone"),
            new Item(new Item.Settings()));
    dragonbornHat =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "dragonborn_hat"),
            new net.minecraft.item.ArmorItem(
                net.minecraft.item.ArmorMaterials.LEATHER,
                net.minecraft.item.ArmorItem.Type.HELMET,
                new Item.Settings().maxCount(1)));
    kabutoHat =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "kabuto_hat"),
            new net.minecraft.item.ArmorItem(
                net.minecraft.item.ArmorMaterials.LEATHER,
                net.minecraft.item.ArmorItem.Type.HELMET,
                new Item.Settings().maxCount(1)));
    zecterBelt =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "zecter_belt"),
            new Item(new Item.Settings().maxCount(1)));
    kunaiGun =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "kunai_gun"),
            new Item(new Item.Settings().maxCount(1)));
    stormbreaker=Registry.register(Registries.ITEM,Identifier.of("fantasyhub","stormbreaker"),
        new net.minecraft.item.AxeItem(net.minecraft.item.ToolMaterials.IRON,
            new Item.Settings().component(net.minecraft.component.DataComponentTypes.UNBREAKABLE,new net.minecraft.component.type.UnbreakableComponent(true)).attributeModifiers(net.minecraft.item.AxeItem.createAttributeModifiers(net.minecraft.item.ToolMaterials.IRON,6,-3.1f))));
    agitoBelt =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "agito_belt"),
            new Item(new Item.Settings().maxCount(1)));
    zoldaBelt =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "zolda_belt"),
            new Item(new Item.Settings().maxCount(1)));
    ryukiBelt =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "ryuki_belt"),
            new Item(new Item.Settings().maxCount(1)));
    knightBelt =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "knight_belt"),
            new Item(new Item.Settings().maxCount(1)));
    decadeBelt =
        Registry.register(
            Registries.ITEM,
            Identifier.of("fantasyhub", "decade_belt"),
            new Item(new Item.Settings().maxCount(1)));
    thorCape=Registry.register(Registries.ITEM,Identifier.of("fantasyhub","thor_cape"),new HeroCapeItem(new Item.Settings().maxCount(1),"fantasyhub.flight.requirements.thor"));
    levitationCloak=Registry.register(Registries.ITEM,Identifier.of("fantasyhub","cloak_of_levitation"),new HeroCapeItem(new Item.Settings().maxCount(1),"fantasyhub.flight.requirements.sorcerer_supreme"));
    batmanCape=Registry.register(Registries.ITEM,Identifier.of("fantasyhub","batman_cape"),new HeroCapeItem(new Item.Settings().maxCount(1),"fantasyhub.glide.requirements.batman"));
    classSigil=Registry.register(Registries.ITEM,Identifier.of("fantasyhub","class_sigil"),new Item(new Item.Settings()));
    grapplingHook=Registry.register(Registries.ITEM,Identifier.of("fantasyhub","batman_grappling_hook"),new GrapplingHookItem(new Item.Settings().maxCount(1)));
    heroNpcType=Registry.register(Registries.ENTITY_TYPE,Identifier.of("fantasyhub","hero_npc"),net.minecraft.entity.EntityType.Builder.create(HeroNpcEntity::new,net.minecraft.entity.SpawnGroup.MONSTER).dimensions(.6f,1.8f).maxTrackingRange(10).trackingTickInterval(1).build("fantasyhub:hero_npc"));
    net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(heroNpcType,HeroNpcEntity.attributes());
    BeltEquipment.install();
    BackEquipment.install();
    PayloadTypeRegistry.playS2C().register(Kabuto.ID, Kabuto.CODEC);
    PayloadTypeRegistry.playS2C().register(Rider.ID, Rider.CODEC);
    PayloadTypeRegistry.playS2C().register(RiderFx.ID, RiderFx.CODEC);
    PayloadTypeRegistry.playS2C().register(RiderProjectile.ID, RiderProjectile.CODEC);
    PayloadTypeRegistry.playS2C().register(KabutoCue.ID, KabutoCue.CODEC);
    PayloadTypeRegistry.configurationS2C().register(Handshake.ID, Handshake.CODEC);
    PayloadTypeRegistry.configurationC2S().register(Handshake.ID, Handshake.CODEC);
    PayloadTypeRegistry.playS2C().register(State.ID, State.CODEC);
    PayloadTypeRegistry.playS2C().register(Hero.ID,Hero.CODEC);
    PayloadTypeRegistry.playS2C().register(KabutoFx.ID, KabutoFx.CODEC);
    PayloadTypeRegistry.playS2C().register(Casting.ID, Casting.CODEC);
    PayloadTypeRegistry.playS2C().register(Open.ID, Open.CODEC);
    PayloadTypeRegistry.playC2S().register(Action.ID, Action.CODEC);
    PayloadTypeRegistry.playC2S().register(Observation.ID, Observation.CODEC);
    installed = true;
  }
}
