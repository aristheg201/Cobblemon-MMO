package vn.svframe.svframelib.api.economy;

import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.Objects;

/** Native server-thread economy backend contract used by SVFrame mods. */
public interface CurrencyBackend {
    String id();
    boolean ready();
    boolean supports(String currencyId);
    BigDecimal balance(ServerPlayerEntity player, String currencyId);
    void setBalance(ServerPlayerEntity player, String currencyId, BigDecimal amount);
    void deposit(ServerPlayerEntity player, String currencyId, BigDecimal amount);
    boolean withdraw(ServerPlayerEntity player, String currencyId, BigDecimal amount);
    String symbol(String currencyId);

    default boolean transfer(ServerPlayerEntity from, ServerPlayerEntity to, String currencyId, BigDecimal amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) throw new IllegalArgumentException("Currency amount cannot be negative");
        if (amount.signum() == 0 || from.getUuid().equals(to.getUuid())) return true;

        BigDecimal fromBefore = balance(from, currencyId);
        if (fromBefore.compareTo(amount) < 0) return false;
        BigDecimal toBefore = balance(to, currencyId);
        if (!withdraw(from, currencyId, amount)) return false;
        try {
            deposit(to, currencyId, amount);
            return true;
        } catch (RuntimeException failure) {
            setBalance(from, currencyId, fromBefore);
            setBalance(to, currencyId, toBefore);
            throw failure;
        }
    }
}
