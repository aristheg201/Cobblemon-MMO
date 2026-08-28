package vn.svframe.svframelib.fabric.integration.economy;

import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.api.economy.CurrencyBackend;
import vn.svframe.svframelib.api.economy.CurrencyKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Direct native bridge to CobbleDollars' player balance API. */
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
        return new BigDecimal(PlayerExtensionKt.getCobbleDollars(player));
    }

    @Override public void setBalance(ServerPlayerEntity player, String currencyId, BigDecimal amount) {
        requireCurrency(currencyId);
        PlayerExtensionKt.setCobbleDollars(player, integer(amount));
    }

    @Override public void deposit(ServerPlayerEntity player, String currencyId, BigDecimal amount) {
        requireCurrency(currencyId);
        BigInteger value = integer(amount);
        if (value.signum() == 0) return;
        if (!PlayerExtensionKt.earnCobbleDollars(player, value, false))
            throw new IllegalStateException("CobbleDollars earn transaction was cancelled");
    }

    @Override public boolean withdraw(ServerPlayerEntity player, String currencyId, BigDecimal amount) {
        requireCurrency(currencyId);
        BigInteger value = integer(amount);
        if (value.signum() == 0) return true;
        BigInteger current = PlayerExtensionKt.getCobbleDollars(player);
        if (current.compareTo(value) < 0) return false;
        PlayerExtensionKt.setCobbleDollars(player, current.subtract(value));
        return true;
    }

    @Override public String symbol(String currencyId) {
        requireCurrency(currencyId);
        return "CD";
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
