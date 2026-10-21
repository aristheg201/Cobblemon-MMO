package vn.svframe.fantasyhub.core;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.ClassOption;
import vn.svframe.svframemmo.persistence.PlayerDataSnapshot;

/** Banked materials and ownership share the canonical native character transaction. */
public final class ClassProgression {
  private static final Logger LOG = Logger.getLogger("FantasyHub-Progression");
  private static final Set<UUID> PENDING = new HashSet<>();
  private static final Set<UUID> UNAVAILABLE = new HashSet<>();
  private static final String STARTER = "fantasyhub:starter", STARTER_RESET = "fantasyhub:starter-reset", SIGILS = "fantasyhub:sigils";
  private static final String LAST_TRANSACTION = "fantasyhub:last-transaction";
  private static final Set<String> HEROES = Set.of("SPIDER_MAN", "SORCERER_SUPREME", "IRON_MAN", "THOR", "FLASH", "BATMAN");
  private static ProgressionPolicy.Settings settings = ProgressionPolicy.defaults();
  private static int ticks;

  public static void reload() throws IOException {
    var path = FabricLoader.getInstance().getConfigDir().resolve("FantasyHub/progression.json");
    var gson = new GsonBuilder().setPrettyPrinting().create();
    Files.createDirectories(path.getParent());
    if (!Files.exists(path)) Files.writeString(path, gson.toJson(ProgressionPolicy.defaults()) + "\n");
    try (var reader = Files.newBufferedReader(path)) {
      var next = gson.fromJson(reader, ProgressionPolicy.Settings.class);
      if (next == null) throw new IllegalArgumentException("Empty progression policy");
      settings = next;
    } catch (RuntimeException invalid) { throw new IOException("Invalid progression policy: " + path, invalid); }
  }
  public static ProgressionPolicy.Settings settings() { return settings; }
  public static int price(String id) { return settings.unlockCost(id); }
  public static int sigils(PlayerData data) { return data.getClaimCount(SIGILS); }
  public static boolean busy(PlayerData data) { return PENDING.contains(data.getUniqueId()) || UNAVAILABLE.contains(data.getUniqueId()); }
  public static boolean owns(PlayerData data, String id) { return data.getClaimCount(ownedKey(id)) > 0; }
  public static boolean starter(PlayerData data) {
    if (data.getClaimCount(STARTER_RESET) > 0) return true;
    return data.getProfess().hasOption(ClassOption.DEFAULT)
        && ProgressionPolicy.starter(SVFrameMMO.classSelection().hasChosen(data), data.getClaimCount(STARTER) > 0);
  }
  private static String ownedKey(String id) { return "fantasyhub:owned:" + id.toUpperCase(Locale.ROOT); }
  public static void own(PlayerData data, String id) { data.setClaimCount(ownedKey(id), 1); }
  public static void consumeStarter(PlayerData data, String id) {
    data.setClaimCount(STARTER, 1); data.setClaimCount(STARTER_RESET, 0); own(data, id);
  }

  public static void install() {
    ProgressionCommands.install();
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> { PENDING.clear(); UNAVAILABLE.clear(); ticks = 0; });
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> migrate(SVFrameMMO.playerData().get(handler.player))));
    ServerTickEvents.END_SERVER_TICK.register(server -> {
      if (++ticks % 40 == 0) for (var player : server.getPlayerManager().getPlayerList()) migrate(SVFrameMMO.playerData().get(player));
    });
  }

  private static void migrate(PlayerData data) {
    if (busy(data) || ClassSwitchService.busy(data)) return;
    boolean ownership = data.getClaimCount("fantasyhub:ownership-migrated") == 0;
    boolean points = HEROES.contains(data.getClassId()) && data.getClaimCount("fantasyhub:points:" + data.getClassId()) == 0;
    if (ownership || points) try { transact(data, () -> initialize(data)); }
    catch (RuntimeException failure) { LOG.log(Level.SEVERE, "Cannot migrate class progression for " + data.getUniqueId(), failure); }
  }

  /** Must be called inside the class-change transaction, before its native snapshot is committed. */
  static void initialize(PlayerData data) {
    if (data.getClaimCount("fantasyhub:ownership-migrated") == 0) {
      for (String id : data.getClassSlots().keySet()) {
        var previous = SVFrameMMO.classes().get(id);
        if (previous != null && !previous.hasOption(ClassOption.DEFAULT)) own(data, id);
      }
      data.setClaimCount("fantasyhub:ownership-migrated", 1);
    }
    if (!data.getProfess().hasOption(ClassOption.DEFAULT)) own(data, data.getClassId());
    String key = "fantasyhub:points:" + data.getClassId();
    if (HEROES.contains(data.getClassId()) && data.getClaimCount(key) == 0) {
      int learned = (int) data.getProfess().getConfiguredSkills().stream()
          .filter(skill -> data.getUnlockedItems().contains(skill.getUnlockNamespacedKey())).count();
      int upgraded = data.getSkillLevels().values().stream().mapToInt(value -> Math.max(0, value - 1)).sum();
      data.setSkillPoints(Math.max(data.getSkillPoints(), ProgressionPolicy.migrationFloor(settings, data.getLevel(), learned, upgraded)));
      data.setClaimCount(key, 1);
      // Subsequent levels use the configured native class_exp_table, avoiding a second tick award.
    }
  }

  /** Convenience grant for integrations without a replayable event; prefer awardSigilsOnce for settlements. */
  public static void awardSigils(ServerPlayerEntity player, int count) {
    awardSigilsOnce(player, "grant:" + UUID.randomUUID(), count).join();
  }

  /** Marker and banked material balance are persisted in the same native snapshot. No inventory drops. */
  public static CompletableFuture<Boolean> awardSigilsOnce(ServerPlayerEntity player, String settlementId, int count) {
    if (player.getServer() == null || !player.getServer().isOnThread())
      return CompletableFuture.failedFuture(new IllegalStateException("Sigils must be awarded on the server thread"));
    if (count <= 0 || count > 1_000_000 || settlementId == null || settlementId.isBlank() || settlementId.length() > 200)
      return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid sigil settlement"));
    var data = SVFrameMMO.playerData().get(player);
    String key = "fantasyhub:boss_settlement:" + settlementId;
    if (data.getClaimCount(key) > 0) return CompletableFuture.completedFuture(false);
    try {
      transact(data, () -> {
        data.setClaimCount(SIGILS, Math.addExact(sigils(data), count));
        data.setClaimCount(key, 1);
      });
      return CompletableFuture.completedFuture(true);
    } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
  }

  public static boolean purchase(ServerPlayerEntity player, String id) {
    id = id.toUpperCase(Locale.ROOT);
    var data = SVFrameMMO.playerData().get(player);
    if (busy(data) || ClassSwitchService.busy(data) || !ClassSwitchService.selectable(id)
        || SVFrameMMO.classes().get(id) == null || owns(data, id)
        || !ProgressionPolicy.affordable(sigils(data), price(id))) return reject(player, "Class chưa hợp lệ, đã sở hữu hoặc không đủ Class Sigil.");
    int cost = price(id); String target = id;
    try {
      transact(data, () -> { own(data, target); data.setClaimCount(SIGILS, sigils(data) - cost); });
      player.sendMessage(Text.literal("Đã mở khóa class vĩnh viễn. Chọn class để sử dụng; phí chuyển áp dụng riêng."), false);
      return true;
    } catch (RuntimeException failure) { return reject(player, "Không lưu được giao dịch mở class; số dư đã được hoàn tác."); }
  }

  public static boolean learn(ServerPlayerEntity player, String id) {
    var data = SVFrameMMO.playerData().get(player);
    var skill = data.getProfess().getSkill(id);
    if (skill == null || busy(data) || ClassSwitchService.busy(data)
        || !ProgressionPolicy.learnable(data.getLevel(), skill.getUnlockLevel(), data.getSkillPoints(), data.hasUnlocked(skill.getUnlockNamespacedKey()), settings.learnCost()))
      return reject(player, "Cần đủ cấp class và điểm học; mỗi kỹ năng chỉ học một lần.");
    try {
      transact(data, () -> { data.setSkillPoints(data.getSkillPoints() - settings.learnCost()); data.unlock(skill.getUnlockNamespacedKey()); });
      player.sendMessage(Text.literal("Đã học " + skill.getSkill().getName() + ". Chọn ô và Trang bị để gán phím."), false);
      return true;
    } catch (RuntimeException failure) { return reject(player, "Không lưu được kỹ năng; điểm học đã được hoàn tác."); }
  }

  /** A small blocking commit prevents a tick, disconnect, cast, or reward from observing speculative state.
   * The native writer has a separate executor and does not call the server thread. */
  static void transact(PlayerData data, Runnable mutation) {
    if (busy(data) || ClassSwitchService.busy(data)) throw new IllegalStateException("Progression transaction is pending or persistence recovery requires restart");
    var before = PlayerDataSnapshot.capture(data);
    PENDING.add(data.getUniqueId());
    try {
      mutation.run();
      data.setPreference(LAST_TRANSACTION, UUID.randomUUID().toString());
      SVFrameMMO.playerData().savePlayerDurably(data).join();
    } catch (RuntimeException failure) {
      restore(data, before);
      try { SVFrameMMO.playerData().savePlayerDurably(data).join(); }
      catch (RuntimeException rollbackFailure) {
        quarantine(data); failure.addSuppressed(rollbackFailure);
        LOG.log(Level.SEVERE, "Progression rollback could not be persisted; further transactions blocked until reload from native storage: " + data.getUniqueId(), rollbackFailure);
      }
      throw failure;
    } finally { PENDING.remove(data.getUniqueId()); }
  }
  static void quarantine(PlayerData data) { UNAVAILABLE.add(data.getUniqueId()); }
  static void restore(PlayerData data, PlayerDataSnapshot before) {
    double health = data.isOnline() ? data.getPlayer().getHealth() : data.getHealth();
    data.prepareReload(); before.apply(data); data.reloadDefinitions();
    if (data.isOnline()) data.getPlayer().setHealth((float) Math.min(health, data.getPlayer().getMaxHealth()));
  }
  static boolean resetStarter(PlayerData data, boolean reset) {
    transact(data, () -> { data.setClaimCount(STARTER_RESET, reset ? 1 : 0); data.setClaimCount(STARTER, reset ? 0 : 1); });
    return true;
  }
  static void changeOwnership(PlayerData data, String id, boolean grant) {
    if (!ClassSwitchService.selectable(id)) throw new IllegalArgumentException("Unknown selectable class");
    transact(data, () -> data.setClaimCount(ownedKey(id), grant ? 1 : 0));
  }
  private static boolean reject(ServerPlayerEntity player, String message) { player.sendMessage(Text.literal(message), false); return false; }
}
