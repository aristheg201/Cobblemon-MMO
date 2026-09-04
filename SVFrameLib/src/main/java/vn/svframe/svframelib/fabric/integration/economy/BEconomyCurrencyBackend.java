package vn.svframe.svframelib.fabric.integration.economy;

import net.minecraft.server.network.ServerPlayerEntity;
import org.krripe.beconomy.api.BEconomy;
import org.krripe.beconomy.api.EconomyAPI;
import org.krripe.beconomy.config.ConfigManager;
import vn.svframe.svframelib.api.economy.CurrencyBackend;
import vn.svframe.svframelib.api.economy.CurrencyKey;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/** Direct native bridge to BEconomy's public API. */
public final class BEconomyCurrencyBackend implements CurrencyBackend {
    @Override public String id() { return CurrencyKey.BECONOMY; }
    @Override public boolean ready() { return BEconomy.INSTANCE.isInitialized(); }

    @Override public boolean supports(String currencyId) {
        if (!ready()) return false;
        if (isPrimary(currencyId)) return api().getPrimaryCurrency() != null;
        return api().currencyExists(normalize(currencyId));
    }

    @Override public BigDecimal balance(ServerPlayerEntity player, String currencyId) {
        return api().getBalance(player.getUuid(), resolve(currencyId));
    }

    @Override public void setBalance(ServerPlayerEntity player, String currencyId, BigDecimal amount) {
        requireNonNegative(amount);
        api().setBalance(player.getUuid(), amount, resolve(currencyId));
    }

    @Override public void deposit(ServerPlayerEntity player, String currencyId, BigDecimal amount) {
        requireNonNegative(amount);
        api().addBalance(player.getUuid(), amount, resolve(currencyId));
    }

    @Override public boolean withdraw(ServerPlayerEntity player, String currencyId, BigDecimal amount) {
        requireNonNegative(amount);
        return api().subtractBalance(player.getUuid(), amount, resolve(currencyId));
    }

    @Override public boolean transfer(ServerPlayerEntity from, ServerPlayerEntity to, String currencyId, BigDecimal amount) {
        requireNonNegative(amount);
        if (amount.signum() == 0 || from.getUuid().equals(to.getUuid())) return true;
        return api().transfer(from.getUuid(), to.getUuid(), amount, resolve(currencyId));
    }

    @Override public String symbol(String currencyId) {
        return api().getCurrencySymbol(resolve(currencyId));
    }

    private EconomyAPI api() {
        if (!BEconomy.INSTANCE.isInitialized()) throw new IllegalStateException("BEconomy API is not initialized");
        return BEconomy.INSTANCE.getAPI();
    }

    private String resolve(String currencyId) {
        EconomyAPI api = api();
        if (isPrimary(currencyId)) {
            ConfigManager.EconomyConfig primary = api.getPrimaryCurrency();
            if (primary == null) throw new IllegalStateException("BEconomy has no primary currency configured");
            return primary.getCurrencyType();
        }
        String normalized = normalize(currencyId);
        if (!api.currencyExists(normalized)) throw new IllegalArgumentException("Unknown BEconomy currency: " + currencyId);
        return normalized;
    }

    private static boolean isPrimary(String value) {
        return value == null || value.isBlank() || "primary".equalsIgnoreCase(value) || "default".equalsIgnoreCase(value);
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "currencyId").trim().toLowerCase(Locale.ROOT);
    }

    private static void requireNonNegative(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) throw new IllegalArgumentException("Currency amount cannot be negative");
    }
}
