package vn.svframe.svquest.server;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.network.ActionPayload;
import vn.svframe.svquest.network.CatalogPayload;
import vn.svframe.svquest.network.StatePayload;
import vn.svframe.svquest.quest.QuestCatalog;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.command.argument.EntityArgumentType.getPlayer;
import static net.minecraft.command.argument.EntityArgumentType.player;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ServerRuntime {
    private static final int CATALOG_CHUNK_CHARS = 12000;

    private final QuestStateStore store = new QuestStateStore();
    private final QuestEngine engine = new QuestEngine(store, new RewardDispatcher());
    private volatile ReflectionIntegrationBridge integrations;
    private volatile GuidedProgressBridge guidedProgress;
    private volatile ProductionProgressPoller productionPoller;
    private volatile SeasonProgressPoller seasonPoller;

    public void register() {
        QuestCatalog.loadServer();
        ProgressSettings.loadServer();
        FeatureCatalog.loadServer();
        store.rebindCatalog();
        engine.setSync(this::sendState);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload.action())));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ReflectionIntegrationBridge bridge = new ReflectionIntegrationBridge(server, engine);
            integrations = bridge;
            bridge.install();

            GuidedProgressBridge guided = new GuidedProgressBridge(server, engine);
            guidedProgress = guided;
            guided.install();

            ProductionProgressPoller poller = new ProductionProgressPoller(server, engine);
            productionPoller = poller;
            SeasonProgressPoller seasonal = new SeasonProgressPoller(server, engine);
            seasonPoller = seasonal;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                bridge.onJoin(player);
                guided.onJoin(player);
                poller.onJoin(player);
                seasonal.onJoin(player);
                sendFullSync(player);
            }
            SVQuest.LOGGER.info("SVQuest production integrations installed; quests={}", QuestCatalog.QUESTS.size());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ProductionProgressPoller poller = productionPoller;
            if (poller != null) poller.tick();
            SeasonProgressPoller seasonal = seasonPoller;
            if (seasonal != null) seasonal.tick();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ReflectionIntegrationBridge bridge = integrations;
            if (bridge != null) bridge.onJoin(handler.player);
            GuidedProgressBridge guided = guidedProgress;
            if (guided != null) guided.onJoin(handler.player);
            ProductionProgressPoller poller = productionPoller;
            if (poller != null) poller.onJoin(handler.player);
            SeasonProgressPoller seasonal = seasonPoller;
            if (seasonal != null) seasonal.onJoin(handler.player);
            sendFullSync(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ReflectionIntegrationBridge bridge = integrations;
            if (bridge != null) bridge.onQuit(handler.player);
            GuidedProgressBridge guided = guidedProgress;
            if (guided != null) guided.onQuit(handler.player);
            ProductionProgressPoller poller = productionPoller;
            if (poller != null) poller.onQuit(handler.player);
            SeasonProgressPoller seasonal = seasonPoller;
            if (seasonal != null) seasonal.onQuit(handler.player);
            store.unload(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> store.saveAll());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("svquest")
                        .executes(ctx -> { sendFullSync(ctx.getSource().getPlayerOrThrow()); return 1; })
                        .then(literal("sync").executes(ctx -> { sendFullSync(ctx.getSource().getPlayerOrThrow()); return 1; }))
                        .then(literal("reload").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                            try {
                                int quests = QuestCatalog.loadServer();
                                ProgressSettings.loadServer();
                                int features = FeatureCatalog.loadServer();
                                store.rebindCatalog();
                                for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) sendFullSync(player);
                                ctx.getSource().sendFeedback(() -> Text.literal("SVQuest reload OK: " + quests + " quest, " + features + " feature."), true);
                                return 1;
                            } catch (Throwable error) {
                                SVQuest.LOGGER.error("SVQuest reload rejected", error);
                                ctx.getSource().sendError(Text.literal("SVQuest reload thất bại: " + error.getMessage()));
                                return 0;
                            }
                        }))
                        .then(literal("progress").requires(src -> src.hasPermissionLevel(2))
                                .then(argument("player", player())
                                        .then(argument("key", word())
                                                .then(argument("amount", integer())
                                                        .executes(ctx -> {
                                                            ServerPlayerEntity target = getPlayer(ctx, "player");
                                                            engine.adminAdd(target, getString(ctx, "key"), getInteger(ctx, "amount"));
                                                            ctx.getSource().sendFeedback(() -> Text.literal("SVQuest progress updated for " + target.getName().getString()), false);
                                                            return 1;
                                                        })))))
                        .then(literal("set").requires(src -> src.hasPermissionLevel(2))
                                .then(argument("player", player())
                                        .then(argument("key", word())
                                                .then(argument("value", integer(0))
                                                        .executes(ctx -> {
                                                            ServerPlayerEntity target = getPlayer(ctx, "player");
                                                            engine.adminSet(target, getString(ctx, "key"), getInteger(ctx, "value"));
                                                            return 1;
                                                        })))))
        ));
        SVQuest.LOGGER.info("SVQuest dedicated-server runtime registered.");
    }

    private void handle(ServerPlayerEntity player, String action) {
        if (action == null || action.length() > 128) return;
        if (action.equals("sync")) {
            sendFullSync(player);
            return;
        }
        if (!action.startsWith("feature:")) return;

        String id = action.substring("feature:".length());
        FeatureCatalog.Feature feature = FeatureCatalog.get(id);
        if (feature == null) {
            player.sendMessage(Text.literal("§cTính năng này chưa được cấu hình trên server."), false);
            return;
        }
        try {
            if (!feature.opener().isBlank() && FeatureOpeners.handle(player, feature.opener())) {
                engine.signal(player, "feature." + id);
                return;
            }
            if (feature.command().isBlank()) {
                player.sendMessage(Text.literal("§cTính năng này chưa có action hợp lệ."), false);
                return;
            }
            String command = feature.command().replace("{player}", player.getName().getString()).replace("%player%", player.getName().getString());
            player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), command);
            engine.signal(player, "feature." + id);
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("Feature action '{}' failed safely for {}: {}", id, player.getName().getString(), t.toString());
            player.sendMessage(Text.literal("§cKhông thể mở tính năng này lúc này."), false);
        }
    }

    private void sendFullSync(ServerPlayerEntity player) {
        sendCatalog(player);
        sendState(player);
    }

    private void sendCatalog(ServerPlayerEntity player) {
        try {
            String token = QuestCatalog.snapshotToken();
            String sequence = Integer.toHexString(token.hashCode()) + "-" + token.length();
            int total = Math.max(1, (token.length() + CATALOG_CHUNK_CHARS - 1) / CATALOG_CHUNK_CHARS);
            for (int index = 0; index < total; index++) {
                int from = index * CATALOG_CHUNK_CHARS;
                int to = Math.min(token.length(), from + CATALOG_CHUNK_CHARS);
                String chunk = sequence + "|" + index + "|" + total + "|" + token.substring(from, to);
                ServerPlayNetworking.send(player, new CatalogPayload(chunk));
            }
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("Could not send SVQuest catalog to {}: {}", player.getName().getString(), t.toString());
        }
    }

    private void sendState(ServerPlayerEntity player) {
        try {
            ServerPlayNetworking.send(player, new StatePayload(store.get(player.getUuid()).encode()));
        } catch (Throwable t) {
            SVQuest.LOGGER.debug("Could not send SVQuest state to {}: {}", player.getName().getString(), t.toString());
        }
    }
}
