package vn.svframe.svframelib.api.economy;

import java.util.Locale;
import java.util.Objects;

/** Identifies one currency exposed by an economy backend. */
public record CurrencyKey(String provider, String currency) {
    public static final String BECONOMY = "beconomy";
    public static final String COBBLEDOLLARS = "cobbledollars";

    public CurrencyKey {
        provider = normalizeProvider(provider);
        currency = normalizeCurrency(currency);
    }

    public static CurrencyKey beconomyPrimary() {
        return new CurrencyKey(BECONOMY, "primary");
    }

    public static CurrencyKey beconomy(String currencyType) {
        return new CurrencyKey(BECONOMY, currencyType);
    }

    public static CurrencyKey cobbleDollars() {
        return new CurrencyKey(COBBLEDOLLARS, COBBLEDOLLARS);
    }

    /**
     * Parses provider[:currency]. Bare "beconomy" selects its primary currency;
     * bare "cobbledollars" selects the single CobbleDollars balance.
     */
    public static CurrencyKey parse(String value) {
        String raw = Objects.requireNonNull(value, "currency key").trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("Currency key cannot be empty");
        int separator = raw.indexOf(':');
        if (separator < 0) {
            String provider = normalizeProvider(raw);
            if (BECONOMY.equals(provider)) return beconomyPrimary();
            if (COBBLEDOLLARS.equals(provider)) return cobbleDollars();
            return new CurrencyKey(provider, "default");
        }
        if (separator == 0 || separator == raw.length() - 1)
            throw new IllegalArgumentException("Currency key must be provider:currency: " + value);
        return new CurrencyKey(raw.substring(0, separator), raw.substring(separator + 1));
    }

    public String serialized() {
        return provider + ':' + currency;
    }

    private static String normalizeProvider(String value) {
        String normalized = Objects.requireNonNull(value, "provider").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Currency provider cannot be empty");
        return normalized;
    }

    private static String normalizeCurrency(String value) {
        String normalized = Objects.requireNonNull(value, "currency").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Currency id cannot be empty");
        return normalized;
    }
}
