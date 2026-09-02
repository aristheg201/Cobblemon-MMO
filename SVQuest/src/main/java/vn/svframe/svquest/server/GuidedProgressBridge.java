package vn.svframe.svquest.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import vn.svframe.svquest.SVQuest;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Generic integration signals for the guided campaign. Quest content remains entirely in JSON. */
public final class GuidedProgressBridge {
    private final MinecraftServer server;
    private final QuestEngine engine;
    private final Map<UUID, Integer> rankedMatches = new HashMap<>();
    private final Set<UUID> activeRaid = new HashSet<>();
    private int ticks;

    public GuidedProgressBridge(MinecraftServer server, QuestEngine engine) {
        this.server = server;
        this.engine = engine;
    }

    public void install() {
        safeInstall("Cobblemon guided battle signals", "cobblemon", this::installCobblemonBattleSignals);
        safeInstall("CobbleDollars amount signals", "cobbledollars", this::installCobbleDollarsAmount);
        safeInstall("SkiesShop sell signals", "skiesshop", this::installSkiesShopSell);
        installBlockInteractionSignals();
        ServerTickEvents.END_SERVER_TICK.register(s -> tick());
    }

    public void onJoin(ServerPlayerEntity player) {
        rankedMatches.remove(player.getUuid());
        activeRaid.remove(player.getUuid());
    }

    public void onQuit(ServerPlayerEntity player) {
        rankedMatches.remove(player.getUuid());
        activeRaid.remove(player.getUuid());
    }

    private void tick() {
        int interval = ProgressSettings.pollIntervalTicks();
        if (++ticks % interval != 0) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            probe("Ranked participation", () -> pollRanked(player));
            probe("NovaRaids join", () -> pollRaidJoin(player));
        }
    }

    /**
     * Battle Tower 1.10.22 and Cobblemon Expeditions 1.5.5 are world-block driven on this server.
     * Do not fake their quest progression through commands or direct GUI packets. The callback runs
     * before the target block's normal use handler and always returns PASS so the original mod still
     * opens its own GUI.
     */
    private void installBlockInteractionSignals() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            try {
                Object block = world.getBlockState(hitResult.getBlockPos()).getBlock();
                String className = block.getClass().getName();
                if (className.equals("battle.tower.block.HoloBattleTowerBlock")) {
                    engine.signal(serverPlayer, "feature.battle_tower");
                } else if (className.equals("com.cobblemonexpeditions.block.ExpeditionBoardBlock")
                        || className.equals("com.cobblemonexpeditions.block.ExpeditionBoardPartBlock")) {
                    engine.signal(serverPlayer, "feature.expeditions");
                }
            } catch (Throwable t) {
                SVQuest.LOGGER.debug("Block-interaction quest probe failed safely: {}", t.toString());
            }
            return ActionResult.PASS;
        });
    }

    private void installCobblemonBattleSignals() throws Exception {
        subscribeObservable("com.cobblemon.mod.common.api.events.CobblemonEvents", "BATTLE_VICTORY", event -> {
            Set<UUID> winners = actorPlayers(invoke(event, "getWinners"));
            Set<UUID> losers = actorPlayers(invoke(event, "getLosers"));
            Set<UUID> participants = new HashSet<>(winners);
            participants.addAll(losers);
            for (UUID id : participants) withPlayer(id, p -> engine.signal(p, "battle_participation"));
            if (!losers.isEmpty()) {
                for (UUID winner : winners) {
                    boolean defeatedAnotherPlayer = losers.stream().anyMatch(id -> !id.equals(winner));
                    if (defeatedAnotherPlayer) withPlayer(winner, p -> engine.signal(p, "player_battle_win"));
                }
            }
        });
    }

    private void installCobbleDollarsAmount() throws Exception {
        subscribeObservable("fr.harmex.cobbledollars.common.api.event.CobbleDollarsEvents", "COBBLE_DOLLARS_EARNED", event -> {
            Object player = invoke(event, "getPlayer");
            Object amount = invoke(event, "getAmount");
            if (player instanceof ServerPlayerEntity p && amount instanceof BigInteger n && n.signum() > 0) {
                int safe = n.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE : n.intValue();
                engine.signal(p, "cobbledollars_earned", Math.max(1, safe));
            }
        });
    }

    private void installSkiesShopSell() throws Exception {
        Class<?> listener = Class.forName("com.pokeskies.skiesshop.utils.ShopTransactionEvent");
        Object event = listener.getField("EVENT").get(null);
        Object callback = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (proxy, method, args) -> {
            if (method.getName().equals("execute") && args != null && args.length >= 2) {
                try {
                    ServerPlayerEntity player = args[0] instanceof ServerPlayerEntity p ? p : null;
                    Object transaction = args[1];
                    Object type = publicField(transaction, "type");
                    if (player != null && type != null && "SELL".equalsIgnoreCase(type.toString())) engine.signal(player, "shop_sell");
                } catch (Throwable t) {
                    SVQuest.LOGGER.debug("SkiesShop sell progression callback failed safely: {}", t.toString());
                }
                return ActionResult.PASS;
            }
            return defaultValue(method.getReturnType());
        });
        registerFabricEvent(event, callback);
    }

    private void pollRanked(ServerPlayerEntity player) throws Exception {
        if (!loaded("cobblemon_ranked")) return;
        Class<?> root = Class.forName("cn.kurt6.cobblemon_ranked.CobblemonRanked");
        Object dao = root.getField("rankDao").get(null);
        Object season = root.getField("seasonManager").get(null);
        Object config = root.getField("config").get(null);
        if (dao == null || season == null || config == null) return;
        int seasonId = intValue(invoke(season, "getCurrentSeasonId"));
        String format = String.valueOf(invoke(config, "getDefaultFormat"));
        Object data = dao.getClass().getMethod("getPlayerData", UUID.class, int.class, String.class)
                .invoke(dao, player.getUuid(), seasonId, format);
        if (data == null) return;

        int wins = intValue(invoke(data, "getWins"));
        int losses = intValue(invoke(data, "getLosses"));
        int total = intValue(invoke(data, "getGamesPlayed"));
        if (total <= 0) total = Math.max(0, wins) + Math.max(0, losses);
        Integer previous = rankedMatches.put(player.getUuid(), total);
        if (previous != null && total > previous) engine.signal(player, "ranked_participation", total - previous);
        engine.metric(player, "ranked_elo", Math.max(0, intValue(invoke(data, "getElo"))));
    }

    private void pollRaidJoin(ServerPlayerEntity player) throws Exception {
        if (!loaded("novaraids")) return;
        Class<?> cache = Class.forName("me.unariginal.novaraids.cache.PlayerRaidCache");
        Object current = cache.getMethod("currentRaid", ServerPlayerEntity.class).invoke(null, player);
        UUID id = player.getUuid();
        if (current != null) {
            if (activeRaid.add(id)) engine.signal(player, "raid_join");
        } else {
            activeRaid.remove(id);
        }
    }

    private Set<UUID> actorPlayers(Object actors) {
        Set<UUID> result = new HashSet<>();
        if (!(actors instanceof Iterable<?> iterable)) return result;
        for (Object actor : iterable) {
            Object ids = invoke(actor, "getPlayerUUIDs");
            if (ids instanceof Iterable<?> values) for (Object id : values) if (id instanceof UUID uuid) result.add(uuid);
        }
        return result;
    }

    private void subscribeObservable(String ownerClass, String fieldName, Consumer<Object> callback) throws Exception {
        Class<?> owner = Class.forName(ownerClass);
        Object observable = owner.getField(fieldName).get(null);
        Method subscribe = observable.getClass().getMethod("subscribe", Consumer.class);
        subscribe.invoke(observable, (Consumer<Object>) event -> {
            try { callback.accept(event); }
            catch (Throwable t) { SVQuest.LOGGER.debug("{}.{} progression callback failed safely: {}", ownerClass, fieldName, t.toString()); }
        });
    }

    private static void registerFabricEvent(Object event, Object listener) throws Exception {
        Method register = null;
        for (Method m : event.getClass().getMethods()) {
            if (m.getName().equals("register") && m.getParameterCount() == 1) { register = m; break; }
        }
        if (register == null) throw new NoSuchMethodException("Fabric Event.register");
        register.invoke(event, listener);
    }

    private void safeInstall(String label, String modId, ThrowingRunnable installer) {
        if (!loaded(modId)) return;
        try { installer.run(); }
        catch (Throwable t) { SVQuest.LOGGER.warn("{} disabled safely: {}", label, t.toString()); }
    }

    private void probe(String label, ThrowingRunnable action) {
        try { action.run(); }
        catch (Throwable t) { SVQuest.LOGGER.debug("{} probe failed safely: {}", label, t.toString()); }
    }

    private boolean loaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }

    private void withPlayer(UUID id, Consumer<ServerPlayerEntity> action) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
        if (player != null) action.accept(player);
    }

    private static Object invoke(Object target, String method) {
        if (target == null) return null;
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (Throwable ignored) { return null; }
    }

    private static Object publicField(Object target, String name) {
        if (target == null) return null;
        try { return target.getClass().getField(name).get(target); }
        catch (Throwable ignored) { return null; }
    }

    private static int intValue(Object value) { return value instanceof Number n ? n.intValue() : 0; }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
