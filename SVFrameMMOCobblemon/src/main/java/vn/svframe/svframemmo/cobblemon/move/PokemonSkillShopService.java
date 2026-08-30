package vn.svframe.svframemmo.cobblemon.move;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.config.IntegrationConfig;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent Pokemon-move ownership shop backed by SVFrameMMO ExternalSkillProgression. */
public final class PokemonSkillShopService {
    private final PokemonSkillEconomy economy = new PokemonSkillEconomy();
    private final Set<UUID> purchasing = ConcurrentHashMap.newKeySet();

    public void open(ServerPlayerEntity player) { open(player, 0); }

    public void open(ServerPlayerEntity player, int requestedPage) {
        if (player == null) return;
        IntegrationConfig.PokemonSkillShopConfig config = SVFrameMMOCobblemon.config().pokemonSkills;
        if (!config.enabled) {
            player.sendMessage(Text.literal("Pokemon skill shop is disabled."), true);
            return;
        }
        List<Offer> offers = offers(player.getUuid());
        if (offers.isEmpty()) {
            player.sendMessage(Text.literal("Pokemon move catalog is not loaded yet."), true);
            return;
        }
        int last = Math.max(0, (offers.size() - 1) / PokemonSkillShopGui.PAGE_SIZE);
        new PokemonSkillShopGui(player, this, Math.max(0, Math.min(last, requestedPage))).open();
    }

    public List<Offer> offers(UUID player) {
        ArrayList<Offer> result = new ArrayList<>();
        for (Map.Entry<String, ClassSkill> entry : CobblemonMoveSkillAdapter.definitions().entrySet()) {
            String moveId = entry.getKey();
            ClassSkill definition = entry.getValue();
            if (definition == null || definition.getSkill() == null) continue;
            String skillId = definition.getSkill().getId();
            String name = definition.getSkill().getName();
            if (name == null || name.isBlank()) name = moveId;
            result.add(new Offer(moveId, skillId, name, price(moveId, skillId),
                    SVFrameMMO.externalProgression().isLearned(player, skillId)));
        }
        result.sort(Comparator.comparing(Offer::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Offer::moveId));
        return List.copyOf(result);
    }

    public List<String> moveIds() {
        return CobblemonMoveSkillAdapter.definitions().keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public void purchase(ServerPlayerEntity player, String rawMoveId, int returnPage) {
        if (player == null) return;
        IntegrationConfig.PokemonSkillShopConfig config = SVFrameMMOCobblemon.config().pokemonSkills;
        if (!config.enabled) {
            player.sendMessage(Text.literal("Pokemon skill shop is disabled."), true);
            return;
        }

        ResolvedSkill resolved = resolve(rawMoveId);
        if (resolved == null) {
            player.sendMessage(Text.literal("Unknown Pokemon skill: " + rawMoveId), true);
            return;
        }
        UUID playerId = player.getUuid();
        if (SVFrameMMO.externalProgression().isLearned(playerId, resolved.skillId)) {
            player.sendMessage(Text.literal(resolved.name + " is already owned."), true);
            open(player, returnPage);
            return;
        }
        if (!purchasing.add(playerId)) {
            player.sendMessage(Text.literal("A Pokemon skill purchase is already being processed."), true);
            return;
        }

        BigDecimal price = price(resolved.moveId, resolved.skillId);
        String provider = config.normalizedProvider();
        String currency = config.currency;
        economy.withdraw(player, provider, currency, price).whenComplete((charge, failure) -> {
            MinecraftServer server = player.getServer();
            if (server == null) {
                purchasing.remove(playerId);
                return;
            }
            server.execute(() -> completePurchase(player, resolved, price, provider, currency, returnPage, charge, failure));
        });
    }

    private void completePurchase(ServerPlayerEntity player, ResolvedSkill resolved, BigDecimal price, String provider,
                                  String currency, int returnPage, PokemonSkillEconomy.ChargeResult charge, Throwable failure) {
        UUID playerId = player.getUuid();
        if (failure != null || charge == null || !charge.charged()) {
            purchasing.remove(playerId);
            if (failure != null) SVFrameMMOCobblemon.LOG.error("Pokemon skill charge failed for {}", playerId, failure);
            String message = charge == null ? "Economy transaction failed. No skill was granted." : charge.message();
            player.sendMessage(Text.literal(message == null || message.isBlank() ? "Economy transaction failed. No skill was granted." : message), true);
            return;
        }

        // Ownership may have changed while an asynchronous economy provider was processing the charge.
        if (SVFrameMMO.externalProgression().isLearned(playerId, resolved.skillId)) {
            refundAndFinish(player, price, provider, currency,
                    resolved.name + " was already granted while payment was processing; the purchase was refunded.", returnPage);
            return;
        }

        boolean learned = false;
        try {
            learned = SVFrameMMO.externalProgression().learn(playerId, resolved.skillId, 1);
            if (!learned) {
                refundAndFinish(player, price, provider, currency,
                        "SVFrameMMO rejected the skill grant; the purchase was refunded.", returnPage);
                return;
            }
            SVFrameMMO.externalProgression().save();
        } catch (Throwable error) {
            SVFrameMMOCobblemon.LOG.error("Could not persist purchased Pokemon skill {} for {}", resolved.skillId, playerId, error);
            if (learned) rollbackLearn(playerId, resolved.skillId);
            refundAndFinish(player, price, provider, currency,
                    "The skill could not be saved; the purchase was refunded.", returnPage);
            return;
        }

        purchasing.remove(playerId);
        player.sendMessage(Text.literal("Purchased " + resolved.name + " for " + format(price) + " "
                + economy.displayCurrency(provider, currency) + ". The skill is now owned in SVFrameMMO."), true);
        open(player, returnPage);
    }

    private void rollbackLearn(UUID playerId, String skillId) {
        try {
            SVFrameMMO.externalProgression().forget(playerId, skillId);
            SVFrameMMO.externalProgression().save();
        } catch (Throwable rollbackError) {
            SVFrameMMOCobblemon.LOG.error("Could not rollback failed Pokemon skill grant {} for {}", skillId, playerId, rollbackError);
        }
    }

    private void refundAndFinish(ServerPlayerEntity player, BigDecimal amount, String provider, String currency,
                                 String successMessage, int returnPage) {
        UUID playerId = player.getUuid();
        economy.refund(player, provider, currency, amount).whenComplete((refunded, failure) -> {
            MinecraftServer server = player.getServer();
            if (server == null) {
                purchasing.remove(playerId);
                return;
            }
            server.execute(() -> {
                purchasing.remove(playerId);
                if (failure == null && Boolean.TRUE.equals(refunded)) {
                    player.sendMessage(Text.literal(successMessage), true);
                } else {
                    player.sendMessage(Text.literal("CRITICAL: the skill grant failed after payment and the economy refund also failed. Contact an administrator."), false);
                    SVFrameMMOCobblemon.LOG.error("Pokemon skill refund failed for player {}, provider {}, currency {}, amount {}",
                            playerId, provider, currency, amount, failure);
                }
                open(player, returnPage);
            });
        });
    }

    public GrantResult adminGive(ServerPlayerEntity target, String rawMoveId) {
        if (target == null) return GrantResult.failed("Target player is unavailable.");
        ResolvedSkill resolved = resolve(rawMoveId);
        if (resolved == null) return GrantResult.failed("Unknown Pokemon skill: " + rawMoveId);
        UUID playerId = target.getUuid();
        if (SVFrameMMO.externalProgression().isLearned(playerId, resolved.skillId))
            return GrantResult.failed(target.getName().getString() + " already owns " + resolved.name + ".");

        boolean learned = false;
        try {
            learned = SVFrameMMO.externalProgression().learn(playerId, resolved.skillId, 1);
            if (!learned) return GrantResult.failed("SVFrameMMO rejected the skill grant for " + resolved.skillId + ".");
            SVFrameMMO.externalProgression().save();
            return GrantResult.success("Gave " + resolved.name + " to " + target.getName().getString() + ".", resolved.name);
        } catch (Throwable error) {
            SVFrameMMOCobblemon.LOG.error("Could not persist admin Pokemon skill grant {} for {}", resolved.skillId, playerId, error);
            if (learned) rollbackLearn(playerId, resolved.skillId);
            return GrantResult.failed("Could not save the Pokemon skill grant; no grant was kept.");
        }
    }

    public BigDecimal price(String moveId, String skillId) {
        IntegrationConfig.PokemonSkillShopConfig config = SVFrameMMOCobblemon.config().pokemonSkills;
        Double override = lookupPrice(config.prices, moveId, skillId);
        return BigDecimal.valueOf(override == null ? config.defaultPrice : override).stripTrailingZeros();
    }

    public String currencyLabel() {
        IntegrationConfig.PokemonSkillShopConfig config = SVFrameMMOCobblemon.config().pokemonSkills;
        return economy.displayCurrency(config.normalizedProvider(), config.currency);
    }

    public String title() { return SVFrameMMOCobblemon.config().pokemonSkills.title; }

    private static Double lookupPrice(Map<String, Double> prices, String moveId, String skillId) {
        if (prices == null || prices.isEmpty()) return null;
        for (Map.Entry<String, Double> entry : prices.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.equalsIgnoreCase(moveId) || key.equalsIgnoreCase(skillId)) return entry.getValue();
            try {
                if (CobblemonMoveSkillAdapter.id(key).equals(moveId)) return entry.getValue();
            } catch (IllegalArgumentException ignored) {
                // Invalid override keys are rejected by config validation; this only protects runtime lookup.
            }
        }
        return null;
    }

    private static ResolvedSkill resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String moveId;
        try {
            moveId = CobblemonMoveSkillAdapter.isCanonicalSkillId(raw)
                    ? CobblemonMoveSkillAdapter.moveIdFromCanonical(raw)
                    : CobblemonMoveSkillAdapter.id(raw);
        } catch (IllegalArgumentException error) {
            return null;
        }
        ClassSkill definition = CobblemonMoveSkillAdapter.definitions().get(moveId);
        if (definition == null || definition.getSkill() == null) return null;
        String name = definition.getSkill().getName();
        if (name == null || name.isBlank()) name = moveId;
        return new ResolvedSkill(moveId, definition.getSkill().getId(), name);
    }

    public static String format(BigDecimal amount) {
        return amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
    }

    public record Offer(String moveId, String skillId, String name, BigDecimal price, boolean owned) { }
    private record ResolvedSkill(String moveId, String skillId, String name) { }
    public record GrantResult(boolean success, String message, String skillName) {
        public static GrantResult success(String message, String name) { return new GrantResult(true, message, name); }
        public static GrantResult failed(String message) { return new GrantResult(false, message, ""); }
    }
}
