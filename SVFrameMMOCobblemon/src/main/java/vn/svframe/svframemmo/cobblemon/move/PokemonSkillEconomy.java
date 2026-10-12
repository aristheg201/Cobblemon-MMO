package vn.svframe.svframemmo.cobblemon.move;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Optional, fail-closed economy bridge used only by the Pokemon skill shop. */
public final class PokemonSkillEconomy {

    public CompletableFuture<ChargeResult> withdraw(ServerPlayerEntity player, String provider, String currency, BigDecimal amount) {
        if (player == null) return CompletableFuture.completedFuture(ChargeResult.failed("Player is not available."));
        if (amount == null || amount.signum() <= 0) return CompletableFuture.completedFuture(ChargeResult.failed("Configured skill price is invalid."));
        String normalized = normalize(provider);
        try {
            return switch (normalized) {
                case "cobbledollars" -> CompletableFuture.completedFuture(withdrawCobbleDollars(player, amount));
                case "beconomy" -> CompletableFuture.completedFuture(withdrawNativeBEconomy(player, currency, amount));
                case "impactor" -> withdrawImpactor(player.getUuid(), currency, amount);
                default -> CompletableFuture.completedFuture(ChargeResult.failed("Unsupported economy provider: " + normalized));
            };
        } catch (Throwable error) {
            SVFrameMMOCobblemon.LOG.error("Pokemon skill economy withdrawal failed for provider {}", normalized, error);
            return CompletableFuture.completedFuture(ChargeResult.failed("Economy transaction failed. No skill was granted."));
        }
    }

    public CompletableFuture<Boolean> refund(ServerPlayerEntity player, String provider, String currency, BigDecimal amount) {
        if (player == null || amount == null || amount.signum() <= 0) return CompletableFuture.completedFuture(false);
        String normalized = normalize(provider);
        try {
            return switch (normalized) {
                case "cobbledollars" -> CompletableFuture.completedFuture(refundCobbleDollars(player, amount));
                case "beconomy" -> CompletableFuture.completedFuture(refundNativeBEconomy(player, currency, amount));
                case "impactor" -> refundImpactor(player.getUuid(), currency, amount);
                default -> CompletableFuture.completedFuture(false);
            };
        } catch (Throwable error) {
            SVFrameMMOCobblemon.LOG.error("Pokemon skill economy refund failed for provider {}", normalized, error);
            return CompletableFuture.completedFuture(false);
        }
    }

    public String displayCurrency(String provider, String currency) {
        String normalized = normalize(provider);
        if (normalized.equals("cobbledollars")) return "CobbleDollars";
        if (normalized.equals("beconomy")&&"secondary".equalsIgnoreCase(currency))return "beastcoin";
        String value = currency == null ? "" : currency.trim();
        return value.isBlank() ? normalized : value;
    }

    private static ChargeResult withdrawCobbleDollars(ServerPlayerEntity player, BigDecimal amount) throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("cobbledollars")) return ChargeResult.failed("CobbleDollars is not installed.");
        BigInteger whole = exactWhole(amount, "CobbleDollars");
        Class<?> extension = Class.forName("fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt", true, PokemonSkillEconomy.class.getClassLoader());
        Method get = extension.getMethod("getCobbleDollars", net.minecraft.entity.player.PlayerEntity.class);
        Method set = extension.getMethod("setCobbleDollars", net.minecraft.entity.player.PlayerEntity.class, BigInteger.class);
        BigInteger balance = (BigInteger) get.invoke(null, player);
        if (balance == null || balance.compareTo(whole) < 0) return ChargeResult.insufficient("Not enough CobbleDollars.");
        set.invoke(null, player, balance.subtract(whole));
        return ChargeResult.success();
    }

    private static boolean refundCobbleDollars(ServerPlayerEntity player, BigDecimal amount) throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("cobbledollars")) return false;
        BigInteger whole = exactWhole(amount, "CobbleDollars");
        Class<?> extension = Class.forName("fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt", true, PokemonSkillEconomy.class.getClassLoader());
        Method get = extension.getMethod("getCobbleDollars", net.minecraft.entity.player.PlayerEntity.class);
        Method set = extension.getMethod("setCobbleDollars", net.minecraft.entity.player.PlayerEntity.class, BigInteger.class);
        BigInteger balance = (BigInteger) get.invoke(null, player);
        set.invoke(null, player, (balance == null ? BigInteger.ZERO : balance).add(whole));
        return true;
    }

    private static ChargeResult withdrawNativeBEconomy(ServerPlayerEntity player,String currency,BigDecimal amount) {
        var key=vn.svframe.svframelib.api.economy.CurrencyKey.beconomy(requireCurrency(currency));
        var service=vn.svframe.svframelib.api.economy.CurrencyService.get();
        if(!service.isAvailable(key))return ChargeResult.failed("BEconomy currency unavailable: "+currency);
        return service.withdraw(player,key,amount)?ChargeResult.success():ChargeResult.insufficient("Not enough "+currency+".");
    }
    private static boolean refundNativeBEconomy(ServerPlayerEntity player,String currency,BigDecimal amount) {
        var key=vn.svframe.svframelib.api.economy.CurrencyKey.beconomy(requireCurrency(currency));
        var service=vn.svframe.svframelib.api.economy.CurrencyService.get();
        if(!service.isAvailable(key))return false;
        service.deposit(player,key,amount);return true;
    }
    public boolean available(String provider,String currency) {
        if(normalize(provider).equals("beconomy"))return vn.svframe.svframelib.api.economy.CurrencyService.get().isAvailable(vn.svframe.svframelib.api.economy.CurrencyKey.beconomy(currency));
        return normalize(provider).equals("cobbledollars")&&FabricLoader.getInstance().isModLoaded("cobbledollars");
    }

    private static CompletableFuture<ChargeResult> withdrawImpactor(UUID player, String configuredCurrency, BigDecimal amount) throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("impactor")) return CompletableFuture.completedFuture(ChargeResult.failed("Impactor is not installed."));
        ImpactorContext context = impactorContext(configuredCurrency);
        @SuppressWarnings("unchecked")
        CompletableFuture<Object> accountFuture = (CompletableFuture<Object>) context.accountMethod.invoke(context.service, context.currency, player);
        return accountFuture.thenApply(account -> {
            try {
                Object transaction = account.getClass().getMethod("withdraw", BigDecimal.class).invoke(account, amount);
                boolean successful = (Boolean) transaction.getClass().getMethod("successful").invoke(transaction);
                return successful ? ChargeResult.success() : ChargeResult.insufficient("Not enough " + context.currencyKey + ".");
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Impactor withdrawal failed", error);
            }
        }).exceptionally(error -> {
            SVFrameMMOCobblemon.LOG.error("Impactor Pokemon-skill withdrawal failed", error);
            return ChargeResult.failed("Impactor transaction failed. No skill was granted.");
        });
    }

    private static CompletableFuture<Boolean> refundImpactor(UUID player, String configuredCurrency, BigDecimal amount) throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("impactor")) return CompletableFuture.completedFuture(false);
        ImpactorContext context = impactorContext(configuredCurrency);
        @SuppressWarnings("unchecked")
        CompletableFuture<Object> accountFuture = (CompletableFuture<Object>) context.accountMethod.invoke(context.service, context.currency, player);
        return accountFuture.thenApply(account -> {
            try {
                Object transaction = account.getClass().getMethod("deposit", BigDecimal.class).invoke(account, amount);
                return (Boolean) transaction.getClass().getMethod("successful").invoke(transaction);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Impactor refund failed", error);
            }
        }).exceptionally(error -> {
            SVFrameMMOCobblemon.LOG.error("Impactor Pokemon-skill refund failed", error);
            return false;
        });
    }

    private static ImpactorContext impactorContext(String configuredCurrency) throws ReflectiveOperationException {
        ClassLoader loader = PokemonSkillEconomy.class.getClassLoader();
        Class<?> serviceType = Class.forName("net.impactdev.impactor.api.economy.EconomyService", true, loader);
        Class<?> currencyType = Class.forName("net.impactdev.impactor.api.economy.currency.Currency", true, loader);
        Object service = serviceType.getMethod("instance").invoke(null);
        Object currencies = serviceType.getMethod("currencies").invoke(service);
        String requested = requireCurrency(configuredCurrency);
        Object currency;
        String key;
        if (requested.equalsIgnoreCase("primary")) {
            currency = currencies.getClass().getMethod("primary").invoke(currencies);
            key = currencyKey(currency);
        } else {
            @SuppressWarnings("unchecked")
            Collection<Object> registered = (Collection<Object>) currencies.getClass().getMethod("registered").invoke(currencies);
            currency = null;
            key = requested;
            for (Object candidate : registered) {
                String candidateKey = currencyKey(candidate);
                String candidateValue = candidateKey.contains(":") ? candidateKey.substring(candidateKey.indexOf(':') + 1) : candidateKey;
                if (candidateKey.equalsIgnoreCase(requested) || candidateValue.equalsIgnoreCase(requested)) {
                    currency = candidate;
                    key = candidateKey;
                    break;
                }
            }
            if (currency == null) throw new IllegalArgumentException("Impactor currency is not registered: " + requested);
        }
        Method account = serviceType.getMethod("account", currencyType, UUID.class);
        return new ImpactorContext(service, currency, key, account);
    }

    private static String currencyKey(Object currency) throws ReflectiveOperationException {
        Object key = currency.getClass().getMethod("key").invoke(currency);
        return String.valueOf(key);
    }

    private static BigInteger exactWhole(BigDecimal amount, String provider) {
        try { return amount.stripTrailingZeros().toBigIntegerExact(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException(provider + " only supports whole-number Pokemon skill prices", error); }
    }

    private static String requireCurrency(String currency) {
        String value = currency == null ? "" : currency.trim();
        if (value.isBlank()) throw new IllegalArgumentException("Configured economy currency is blank");
        return value;
    }

    private static String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private record ImpactorContext(Object service, Object currency, String currencyKey, Method accountMethod) { }

    public record ChargeResult(boolean charged, boolean insufficient, String message) {
        public static ChargeResult success() { return new ChargeResult(true, false, ""); }
        public static ChargeResult insufficient(String message) { return new ChargeResult(false, true, message); }
        public static ChargeResult failed(String message) { return new ChargeResult(false, false, message); }
    }
}
