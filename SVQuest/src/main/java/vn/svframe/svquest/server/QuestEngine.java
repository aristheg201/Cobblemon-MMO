package vn.svframe.svquest.server;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

/** Central server-authoritative progression engine. */
public final class QuestEngine {
    private final QuestStateStore store;
    private final RewardDispatcher rewards;
    private StateSync sync = player -> {};

    @FunctionalInterface
    public interface StateSync { void send(ServerPlayerEntity player); }

    public QuestEngine(QuestStateStore store, RewardDispatcher rewards) {
        this.store = store;
        this.rewards = rewards;
    }

    public void setSync(StateSync sync) {
        this.sync = sync == null ? player -> {} : sync;
    }

    public void signal(ServerPlayerEntity player, String key) { signal(player, key, 1); }

    public void signal(ServerPlayerEntity player, String key, int amount) {
        if (player == null || key == null || key.isBlank() || amount <= 0) return;
        mutate(player, state -> state.signal(key, amount), "signal:" + key);
    }

    public void metric(ServerPlayerEntity player, String key, int value) {
        if (player == null || key == null || key.isBlank()) return;
        mutate(player, state -> state.metric(key, value), "metric:" + key);
    }

    public void adminAdd(ServerPlayerEntity player, String key, int amount) {
        mutate(player, state -> state.add(key, amount), "admin-add:" + key);
    }

    public void adminSet(ServerPlayerEntity player, String key, int value) {
        mutate(player, state -> state.set(key, value), "admin-set:" + key);
    }

    /** Claims only the server-authoritative current quest. Progress never advances before this succeeds. */
    public void claim(ServerPlayerEntity player) {
        if (player == null) return;
        try {
            QuestStateStore.PlayerState state = store.get(player.getUuid());
            int index = state.questIndex();
            if (index < 0 || index >= QuestCatalog.QUESTS.size()) {
                player.sendMessage(Text.literal("§aBạn đã hoàn thành toàn bộ lộ trình hiện có."), false);
                sync.send(player);
                return;
            }

            QuestCatalog.Quest quest = QuestCatalog.byIndex(index);
            if (!state.currentComplete()) {
                player.sendMessage(Text.literal("§eQuest này chưa hoàn tất mục tiêu."), false);
                sync.send(player);
                return;
            }

            // Legacy/data-recovery guard: never grant a reward twice if it was already persisted as rewarded.
            if (state.rewarded(quest.id())) {
                if (state.advanceClaimed(quest.id())) store.saveNow(player.getUuid());
                sync.send(player);
                return;
            }

            RewardDispatcher.ClaimResult result = rewards.claim(player, quest);
            if (!result.granted()) {
                player.sendMessage(Text.literal(result.message()), false);
                sync.send(player);
                return;
            }

            state.markRewarded(quest.id());
            if (!state.advanceClaimed(quest.id())) {
                SVQuest.LOGGER.error("SVQuest claim advancement failed after reward processing for {} / {}", player.getName().getString(), quest.id());
                store.saveNow(player.getUuid());
                sync.send(player);
                return;
            }
            store.saveNow(player.getUuid());
            player.sendMessage(Text.literal("§a✓ Hoàn thành: §f" + quest.title()), false);
            if (result.partialFailure()) {
                player.sendMessage(Text.literal("§eMột phần thưởng tích hợp gặp lỗi. Quest đã được ghi nhận để tránh nhận trùng; hãy báo admin."), false);
            }
            sync.send(player);
        } catch (Throwable t) {
            SVQuest.LOGGER.error("SVQuest reward claim failed safely for {}", player.getName().getString(), t);
            player.sendMessage(Text.literal("§cKhông thể nhận thưởng lúc này. Phần thưởng vẫn đang chờ, hãy thử lại."), false);
            sync.send(player);
        }
    }

    private void mutate(ServerPlayerEntity player, java.util.function.Consumer<QuestStateStore.PlayerState> operation, String source) {
        try {
            QuestStateStore.PlayerState state = store.get(player.getUuid());
            long before = state.revision();
            operation.accept(state);
            if (state.revision() != before) store.saveNow(player.getUuid());
            sync.send(player);
        } catch (Throwable t) {
            SVQuest.LOGGER.error("SVQuest progression mutation failed safely for {} ({})", player.getName().getString(), source, t);
        }
    }
}
