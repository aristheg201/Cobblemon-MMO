package vn.svframe.svframelib.fabric.integration.economy;

import fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.api.economy.CurrencyBackend;
import vn.svframe.svframelib.api.economy.CurrencyKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Direct native bridge to CobbleDollars' public player balance mixin interface. */
public final class CobbleDollarsCurrencyBackend implements CurrencyBackend {
    @Override public String id() { return CurrencyKey.COBBLEDOLLARS; }
    @Override public boolean ready() { return true; }

    @Override public boolean supports(String currencyId) {
        return currencyId == null || currencyId.isBlank()
                || CurrencyKey.COBBLEDOLLARS.equalsIgnoreCase(currencyId)
                || "primary".equalsIgnoreCase(currencyId)
                || "default".equalsIgnoreCase(currencyId);
    }

    @Override public BigDecimal balance(ServerPlayerEntity player, String currencyId) {
        requireCurrency(currencyId);
        return new BigDecimal(account(player).cobbleDollars$getCobbleDollars());
    }

    @Override public void setBalance(ServerPlayerEntity player, String currencyId, BigDecimal amount) {
        requireCurrency(currencyId);
        account(player).cobbleDollars$setCobbleDollars(integer(amount));
    }

    @Override public void deposit(ServerPlayerEntity player, String currencyId, BigDecimal amount) {
        requireCurrency(currencyId);
        BigInteger value = integer(amount);
        if (value.signum() == 0) return;
        CobbleDollarsPlayer account = account(player);
        account.cobbleDollars$setCobbleDollars(account.cobbleDollars$getCobbleDollars().add(value));
    }

    @Override public boolean withdraw(ServerPlayerEntity player, String currencyId, BigDecimal amount) {
        requireCurrency(currencyId);
        BigInteger value = integer(amount);
        if (value.signum() == 0) return true;
        CobbleDollarsPlayer account = account(player);
        BigInteger current = account.cobbleDollars$getCobbleDollars();
        if (current.compareTo(value) < 0) return false;
        account.cobbleDollars$setCobbleDollars(current.subtract(value));
        return true;
    }

    @Override public String symbol(String currencyId) {
        requireCurrency(currencyId);
        return "CD";
    }

    private static CobbleDollarsPlayer account(ServerPlayerEntity player) {
        Objects.requireNonNull(player, "player");
        if (player instanceof CobbleDollarsPlayer account) return account;
        throw new IllegalStateException("CobbleDollars player mixin is not available for " + player.getUuid());
    }

    private static void requireCurrency(String currencyId) {
        if (!(currencyId == null || currencyId.isBlank()
                || CurrencyKey.COBBLEDOLLARS.equalsIgnoreCase(currencyId)
                || "primary".equalsIgnoreCase(currencyId)
                || "default".equalsIgnoreCase(currencyId)))
            throw new IllegalArgumentException("CobbleDollars exposes one currency only: " + currencyId);
    }

    private static BigInteger integer(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) throw new IllegalArgumentException("Currency amount cannot be negative");
        try {
            return amount.toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("CobbleDollars only supports whole-number amounts: " + amount, exception);
        }
    }
}
