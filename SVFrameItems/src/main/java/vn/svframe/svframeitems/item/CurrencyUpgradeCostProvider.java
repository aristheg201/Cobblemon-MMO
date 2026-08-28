package vn.svframe.svframeitems.item;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.api.economy.CurrencyKey;
import vn.svframe.svframelib.api.economy.CurrencyService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Transactional SVFrameItems upgrade-cost provider backed by SVFrameLib's native economy bridge. */
final class CurrencyUpgradeCostProvider implements UpgradeService.CostProvider {
    private final String providerId;

    CurrencyUpgradeCostProvider(String providerId) {
        this.providerId = Objects.requireNonNull(providerId, "providerId").trim().toLowerCase(Locale.ROOT);
        if (!CurrencyKey.BECONOMY.equals(this.providerId) && !CurrencyKey.COBBLEDOLLARS.equals(this.providerId))
            throw new IllegalArgumentException("Unsupported built-in currency provider: " + providerId);
    }

    @Override public String id() {
        return providerId;
    }

    @Override public UpgradeService.Reservation reserve(ServerPlayerEntity player, List<UpgradeService.Charge> charges) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(charges, "charges");
        if (charges.isEmpty()) return EmptyReservation.INSTANCE;

        LinkedHashMap<CurrencyKey, BigDecimal> required = new LinkedHashMap<>();
        for (UpgradeService.Charge charge : charges) {
            if (!providerId.equals(charge.cost().provider()))
                throw new IllegalArgumentException("Cost provider mismatch: expected " + providerId + " but got " + charge.cost().provider());
            CurrencyKey key = new CurrencyKey(providerId, charge.cost().id());
            BigDecimal amount = BigDecimal.valueOf(charge.cost().amountForNextLevel(charge.nextLevel() - 1));
            required.merge(key, amount, BigDecimal::add);
        }

        CurrencyService economy = CurrencyService.get();
        LinkedHashMap<CurrencyKey, BigDecimal> before = new LinkedHashMap<>();
        for (Map.Entry<CurrencyKey, BigDecimal> entry : required.entrySet()) {
            if (!economy.isAvailable(entry.getKey())) return UnavailableReservation.INSTANCE;
            BigDecimal balance = economy.balance(player, entry.getKey());
            if (balance.compareTo(entry.getValue()) < 0) return UnavailableReservation.INSTANCE;
            before.put(entry.getKey(), balance);
        }
        return new CurrencyReservation(player, economy, required, before);
    }

    private enum EmptyReservation implements UpgradeService.Reservation {
        INSTANCE;
        @Override public boolean available() { return true; }
        @Override public void commit() {}
        @Override public void rollback() {}
    }

    private enum UnavailableReservation implements UpgradeService.Reservation {
        INSTANCE;
        @Override public boolean available() { return false; }
        @Override public void commit() { throw new IllegalStateException("Cannot commit unavailable currency reservation"); }
        @Override public void rollback() {}
    }

    private static final class CurrencyReservation implements UpgradeService.Reservation {
        private final ServerPlayerEntity player;
        private final CurrencyService economy;
        private final Map<CurrencyKey, BigDecimal> required;
        private final Map<CurrencyKey, BigDecimal> before;
        private boolean committed;

        private CurrencyReservation(ServerPlayerEntity player, CurrencyService economy,
                                    Map<CurrencyKey, BigDecimal> required, Map<CurrencyKey, BigDecimal> before) {
            this.player = Objects.requireNonNull(player, "player");
            this.economy = Objects.requireNonNull(economy, "economy");
            this.required = Map.copyOf(required);
            this.before = Map.copyOf(before);
        }

        @Override public boolean available() {
            return true;
        }

        @Override public void commit() {
            if (committed) return;
            for (Map.Entry<CurrencyKey, BigDecimal> entry : required.entrySet()) {
                if (!economy.isAvailable(entry.getKey()) || economy.balance(player, entry.getKey()).compareTo(entry.getValue()) < 0)
                    throw new IllegalStateException("Currency balance changed after reservation: " + entry.getKey().serialized());
            }

            try {
                for (Map.Entry<CurrencyKey, BigDecimal> entry : required.entrySet()) {
                    if (!economy.withdraw(player, entry.getKey(), entry.getValue()))
                        throw new IllegalStateException("Currency withdrawal failed: " + entry.getKey().serialized());
                }
                committed = true;
            } catch (RuntimeException failure) {
                restore();
                throw failure;
            }
        }

        @Override public void rollback() {
            if (!committed) return;
            restore();
            committed = false;
        }

        private void restore() {
            RuntimeException first = null;
            for (Map.Entry<CurrencyKey, BigDecimal> entry : before.entrySet()) {
                try {
                    economy.setBalance(player, entry.getKey(), entry.getValue());
                } catch (RuntimeException failure) {
                    if (first == null) first = failure;
                    else first.addSuppressed(failure);
                }
            }
            if (first != null) throw first;
        }
    }
}
