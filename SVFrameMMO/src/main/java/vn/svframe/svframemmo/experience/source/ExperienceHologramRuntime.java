package vn.svframe.svframemmo.experience.source;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerExperienceGainEvent;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.experience.EXPSource;
import vn.svframe.svframemmo.experience.Profession;
import vn.svframe.svframemmo.mixin.DisplayEntityAccessor;
import vn.svframe.svframemmo.mixin.TextDisplayEntityAccessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native Fabric replacement for MMOCore's one-second EXP hologram indicators. */
public final class ExperienceHologramRuntime {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-ExpHologram");
    private static final ExperienceHologramRuntime INSTANCE = new ExperienceHologramRuntime();
    private static final long LIFETIME_TICKS = 20L;
    private static final String DEFAULT_FORMAT = "&e+{exp} EXP!";

    private final ThreadLocal<Context> current = new ThreadLocal<>();
    private final List<ActiveHologram> active = new ArrayList<>();
    private final DecimalFormat decimal = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));
    private long messagesModified = Long.MIN_VALUE;
    private String expFormat = DEFAULT_FORMAT;

    private ExperienceHologramRuntime() {
        PlayerExperienceGainEvent.EVENT.register(this::capture);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    public static ExperienceHologramRuntime instance() { return INSTANCE; }

    /** Immutable world position carried only through the synchronous EXP source award. */
    public record HologramLocation(ServerWorld world, Vec3d position) {
        public HologramLocation {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(position, "position");
        }
        public static HologramLocation block(ServerWorld world, BlockPos pos) {
            return new HologramLocation(world, new Vec3d(pos.getX(), pos.getY(), pos.getZ()));
        }
        public static HologramLocation player(net.minecraft.server.network.ServerPlayerEntity player) {
            return new HologramLocation(player.getServerWorld(), player.getPos());
        }
        public static HologramLocation center(Entity entity) {
            if (!(entity.getWorld() instanceof ServerWorld world)) return null;
            Box box = entity.getBoundingBox();
            return new HologramLocation(world, new Vec3d((box.minX + box.maxX) * .5d, (box.minY + box.maxY) * .5d, (box.minZ + box.maxZ) * .5d));
        }
        public HologramLocation add(double x, double y, double z) {
            return new HologramLocation(world, position.add(x, y, z));
        }
    }

    public void giveClass(PlayerData data, double value, EXPSource source, HologramLocation location) {
        if (location == null) { data.giveExperience(value, source); return; }
        Context context = new Context(data, null, source, location);
        run(context, () -> data.giveExperience(value, source));
    }

    public void giveProfession(PlayerData data, Profession profession, double value, EXPSource source, HologramLocation location) {
        if (location == null) { data.getProfessions().giveExperience(profession, value, source); return; }
        Context context = new Context(data, profession, source, location);
        run(context, () -> data.getProfessions().giveExperience(profession, value, source));
    }

    private void run(Context context, Runnable award) {
        Context previous = current.get();
        current.set(context);
        try { award.run(); }
        finally {
            current.set(previous);
            renderCaptured(context);
        }
    }

    /** Captures the mutable event object; rendering happens after all event listeners have returned. */
    private void capture(PlayerExperienceGainEvent event) {
        Context context = current.get();
        if (context == null || context.captured != null) return;
        if (event.getData() != context.data || event.getSource() != context.source) return;
        if (!Objects.equals(event.getProfession(), context.profession)) return;
        context.captured = event;
    }

    private void renderCaptured(Context context) {
        PlayerExperienceGainEvent event = context.captured;
        if (event == null || event.isCancelled()) return;
        HologramLocation location = context.location;
        Profession profession = context.profession;
        if (profession == null) {
            if (!SVFrameMMO.config().displayMainClassExpHolograms()) return;
        } else {
            if (!profession.getOption(Profession.ProfessionOption.EXP_HOLOGRAMS)) return;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            location = location.add(.5d + .7d * random.nextDouble(), 1.3d + .33d * random.nextDouble(), .5d + .7d * random.nextDouble());
        }
        display(location, event.getExperience());
    }

    private synchronized void display(HologramLocation location, double experience) {
        reloadMessagesIfChanged();
        DisplayEntity.TextDisplayEntity hologram = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, location.world());
        String message = expFormat.replace("{exp}", decimal.format(experience));
        ((TextDisplayEntityAccessor) (Object) hologram).svframemmo$setText(Text.literal(SVFrameLib.inst().parseColors(message)));
        ((TextDisplayEntityAccessor) (Object) hologram).svframemmo$setBackground(0);
        ((DisplayEntityAccessor) (Object) hologram).svframemmo$setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        hologram.setPosition(location.position().x, location.position().y, location.position().z);
        if (location.world().spawnEntity(hologram))
            active.add(new ActiveHologram(hologram, SVFrameMMO.currentTick() + LIFETIME_TICKS));
    }

    private synchronized void tick(MinecraftServer server) {
        if (active.isEmpty()) return;
        long now = SVFrameMMO.currentTick();
        active.removeIf(entry -> {
            if (entry.hologram.isRemoved()) return true;
            if (now < entry.expireTick) return false;
            entry.hologram.discard();
            return true;
        });
    }

    private synchronized void clear() {
        for (ActiveHologram entry : active) if (!entry.hologram.isRemoved()) entry.hologram.discard();
        active.clear();
        current.remove();
    }

    private void reloadMessagesIfChanged() {
        Path file = DefaultFiles.ROOT.resolve("messages.yml");
        try {
            long modified = Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
            if (modified == messagesModified) return;
            String next = DEFAULT_FORMAT;
            if (Files.isRegularFile(file)) {
                Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
                Object raw = root.get("exp-hologram");
                if (raw != null && !String.valueOf(raw).isBlank()) next = String.valueOf(raw);
            }
            expFormat = next;
            messagesModified = modified;
        } catch (IOException | RuntimeException exception) {
            LOG.log(Level.WARNING, "Could not reload EXP hologram message; keeping last valid format", exception);
        }
    }

    public synchronized int activeHolograms() { return active.size(); }

    private static final class Context {
        final PlayerData data;
        final Profession profession;
        final EXPSource source;
        final HologramLocation location;
        PlayerExperienceGainEvent captured;
        Context(PlayerData data, Profession profession, EXPSource source, HologramLocation location) {
            this.data = Objects.requireNonNull(data, "data");
            this.profession = profession;
            this.source = Objects.requireNonNull(source, "source");
            this.location = Objects.requireNonNull(location, "location");
        }
    }
    private record ActiveHologram(DisplayEntity.TextDisplayEntity hologram, long expireTick) { }
}
