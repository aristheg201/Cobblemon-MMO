package vn.svframe.svframelib.fabric;

import com.google.gson.GsonBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/** Opt-in, real player/server QA. Builds an arena only when explicitly invoked with SVFRAME_RUNTIME_QA=1. */
public final class HeroEntranceRuntimeQa {
    private static final Logger LOG = Logger.getLogger("SVFrameLib-HeroQA");
    private HeroEntranceRuntimeQa() { }
    public static boolean enabled() { return "1".equals(System.getenv("SVFRAME_RUNTIME_QA")); }

    public static int run(ServerPlayerEntity player) {
        if (!enabled()) return 0;
        var world = player.getServerWorld();
        for (int x = -36; x <= 36; x++) for (int z = -36; z <= 36; z++)
            world.setBlockState(new BlockPos(x, 64, z), Blocks.SMOOTH_STONE.getDefaultState());
        player.changeGameMode(GameMode.SURVIVAL);
        player.networkHandler.requestTeleport(0, 65, 0, 0, 45);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("minecraft", "1.21.1");
        report.put("loader", net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("fabricloader").orElseThrow().getMetadata().getVersion().getFriendlyString());
        SVFrameLibFabricMod.schedule(10, () -> {
            player.setOnGround(true);
            float health = player.getHealth();
            require(report, "launch_started", HeroEntranceRuntime.start(player, Map.of("aim_ticks", 180, "damage", 5)));
            require(report, "duplicate_cast_rejected", !HeroEntranceRuntime.start(player, Map.of()));
            SVFrameLibFabricMod.schedule(35, () -> {
                var first = HeroEntranceRuntime.view(player);
                require(report, "airborne_aim", first != null && first.phase() == HeroEntranceRuntime.Phase.AIM
                        && player.getY() > 75 && first.target() != null);
                report.put("first_target", first.target().toString());
                LOG.info("HERO_QA_CAPTURE first_aim");
                SVFrameLibFabricMod.schedule(25, () -> {
                    Vec3d apex = player.getPos();
                    player.networkHandler.requestTeleport(apex.x, apex.y, apex.z, 90, 45);
                    SVFrameLibFabricMod.schedule(20, () -> {
                        var second = HeroEntranceRuntime.view(player);
                        require(report, "camera_moves_ring", second != null && second.target() != null
                                && first.target().distanceTo(second.target()) > 3);
                        Vec3d target = second.target();
                        report.put("second_target", target.toString());
                        CowEntity inside = cow(player, target.add(1, 0, 0));
                        CowEntity edge = cow(player, target.add(4, 0, 0));
                        CowEntity outside = cow(player, target.add(8, 0, 0));
                        LOG.info("HERO_QA_CAPTURE moved_aim");
                        SVFrameLibFabricMod.schedule(30, () -> {
                            require(report, "confirmed", HeroEntranceRuntime.confirm(player));
                            SVFrameLibFabricMod.schedule(12, () -> LOG.info("HERO_QA_CAPTURE impact"));
                            SVFrameLibFabricMod.schedule(14, () -> {
                                require(report, "inside_damaged", inside.getHealth() < 100);
                                require(report, "edge_damaged", edge.getHealth() < 100);
                                require(report, "outside_untouched", outside.getHealth() == 100);
                                require(report, "knockup", inside.getVelocity().y > .3);
                                require(report, "landed_at_target", player.getPos().distanceTo(target) < 1);
                                require(report, "gravity_restored", !player.hasNoGravity());
                                require(report, "cast_removed", HeroEntranceRuntime.view(player) == null);
                                require(report, "no_caster_damage", player.getHealth() >= health);
                                report.put("inside_health", inside.getHealth());
                                report.put("edge_health", edge.getHealth());
                                report.put("outside_health", outside.getHealth());
                                report.put("inside_velocity_y", inside.getVelocity().y);
                                float impactedHealth = inside.getHealth();
                                SVFrameLibFabricMod.schedule(12, () -> {
                                    require(report, "single_impact", inside.getHealth() == impactedHealth);
                                    inside.discard(); edge.discard(); outside.discard();
                                    player.networkHandler.requestTeleport(0, 65, 0, 0, 45);
                                    player.setOnGround(true);
                                    require(report, "restart", HeroEntranceRuntime.start(player, Map.of()));
                                    require(report, "cancel", HeroEntranceRuntime.cancel(player));
                                    require(report, "cancel_restores_gravity", !player.hasNoGravity() && HeroEntranceRuntime.view(player) == null);
                                    inside.discard(); edge.discard(); outside.discard();
                                    save(report);
                                });
                            });
                        });
                    });
                });
            });
        });
        return 1;
    }

    private static CowEntity cow(ServerPlayerEntity player, Vec3d position) {
        CowEntity cow = EntityType.COW.create(player.getServerWorld());
        if (cow == null) throw new IllegalStateException("Could not create QA cow");
        cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
        cow.setHealth(100); cow.setAiDisabled(true);
        cow.refreshPositionAndAngles(position.x, position.y, position.z, 0, 0);
        player.getServerWorld().spawnEntity(cow);
        return cow;
    }
    private static void require(Map<String, Object> report, String key, boolean passed) {
        report.put(key, passed);
        if (!passed) { save(report); throw new IllegalStateException("Hero QA failed: " + key); }
    }
    private static void save(Map<String, Object> report) {
        try {
            Path output = Path.of("qa", "hero-entrance-runtime.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            LOG.info("HERO_QA_REPORT " + output.toAbsolutePath() + " " + report);
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
}
