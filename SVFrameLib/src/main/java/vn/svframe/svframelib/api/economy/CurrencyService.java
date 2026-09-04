package vn.svframe.svframelib.api.economy;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.fabric.integration.economy.BEconomyCurrencyBackend;
import vn.svframe.svframelib.fabric.integration.economy.CobbleDollarsCurrencyBackend;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared optional economy bridge for SVFrameLib, SVFrameMMO and SVFrameItems. */
public final class CurrencyService {
    private static final CurrencyService INSTANCE = new CurrencyService();
    private final ConcurrentMap<String, CurrencyBackend> backends = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean();

    private CurrencyService() {}

    public static CurrencyService get() {
        return INSTANCE;
    }

    /** Detects installed economy mods without making either one a required runtime dependency. */
    public void initialize() {
        if (!initialized.compareAndSet(false, true)) return;
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded(CurrencyKey.BECONOMY)) registerBuiltIn(new BEconomyCurrencyBackend());
        if (loader.isModLoaded(CurrencyKey.COBBLEDOLLARS)) registerBuiltIn(new CobbleDollarsCurrencyBackend());
    }

    public AutoCloseable register(CurrencyBackend backend) {
        Objects.requireNonNull(backend, "backend");
        String id = new CurrencyKey(backend.id(), "default").provider();
        CurrencyBackend previous = backends.putIfAbsent(id, backend);
        if (previous != null) throw new IllegalStateException("Currency backend already registered: " + id);
        return () -> backends.remove(id, backend);
    }

    public Optional<CurrencyBackend> backend(String id) {
        initialize();
        String normalized = new CurrencyKey(id, "default").provider();
        return Optional.ofNullable(backends.get(normalized));
    }

    public Set<String> providerIds() {
        initialize();
        return Set.copyOf(new LinkedHashSet<>(backends.keySet()));
    }

    public boolean isAvailable(CurrencyKey key) {
        Objects.requireNonNull(key, "key");
        CurrencyBackend backend = backend(key.provider()).orElse(null);
        return backend != null && backend.ready() && backend.supports(key.currency());
    }

    public BigDecimal balance(ServerPlayerEntity player, CurrencyKey key) {
        CurrencyBackend backend = require(player, key);
        return backend.balance(player, key.currency());
    }

    public boolean has(ServerPlayerEntity player, CurrencyKey key, BigDecimal amount) {
        validateAmount(amount);
        return balance(player, key).compareTo(amount) >= 0;
    }

    public void setBalance(ServerPlayerEntity player, CurrencyKey key, BigDecimal amount) {
        validateAmount(amount);
        require(player, key).setBalance(player, key.currency(), amount);
    }

    public void deposit(ServerPlayerEntity player, CurrencyKey key, BigDecimal amount) {
        validateAmount(amount);
        if (amount.signum() == 0) return;
        require(player, key).deposit(player, key.currency(), amount);
    }

    public boolean withdraw(ServerPlayerEntity player, CurrencyKey key, BigDecimal amount) {
        validateAmount(amount);
        if (amount.signum() == 0) return true;
        return require(player, key).withdraw(player, key.currency(), amount);
    }

    public boolean transfer(ServerPlayerEntity from, ServerPlayerEntity to, CurrencyKey key, BigDecimal amount) {
        Objects.requireNonNull(to, "to");
        validateAmount(amount);
        return require(from, key).transfer(from, to, key.currency(), amount);
    }

    public String symbol(CurrencyKey key) {
        Objects.requireNonNull(key, "key");
        CurrencyBackend backend = backend(key.provider()).orElseThrow(() -> unavailable(key));
        if (!backend.ready() || !backend.supports(key.currency())) throw unavailable(key);
        return backend.symbol(key.currency());
    }

    private CurrencyBackend require(ServerPlayerEntity player, CurrencyKey key) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        CurrencyBackend backend = backend(key.provider()).orElseThrow(() -> unavailable(key));
        if (!backend.ready() || !backend.supports(key.currency())) throw unavailable(key);
        return backend;
    }

    private static void validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) throw new IllegalArgumentException("Currency amount cannot be negative");
    }

    private static IllegalStateException unavailable(CurrencyKey key) {
        return new IllegalStateException("Currency is not available: " + key.serialized());
    }

    private void registerBuiltIn(CurrencyBackend backend) {
        CurrencyBackend previous = backends.putIfAbsent(backend.id(), backend);
        if (previous != null && previous.getClass() != backend.getClass())
            throw new IllegalStateException("Currency backend already registered: " + backend.id());
    }
}
