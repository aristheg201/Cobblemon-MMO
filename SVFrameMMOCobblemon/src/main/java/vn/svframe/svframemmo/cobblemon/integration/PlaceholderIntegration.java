package vn.svframe.svframemmo.cobblemon.integration;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.fusion.FusionSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;

/** Optional Placeholder API surface for HUDs, scoreboards and server-authored UI. */
public final class PlaceholderIntegration {
    private static final String NAMESPACE = "svframemmo_cobblemon";
    private static final List<Identifier> IDS = new ArrayList<>();
    private PlaceholderIntegration() { }

    public static void registerIfPresent() {
        if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) return;
        add("fused", (id, s) -> Boolean.toString(active(s)));
        add("fusion_type", (id, s) -> active(s) ? s.typeName() : "none");
        add("tier", (id, s) -> active(s) ? s.tier().name().toLowerCase(Locale.ROOT) : "none");
        add("pokemon", (id, s) -> active(s) ? s.pokemonName() : "");
        add("species", (id, s) -> active(s) ? s.speciesId() : "");
        add("bonus_percent", (id, s) -> active(s) ? Integer.toString((int) Math.round(s.tier().multiplier() * 100d)) : "0");
        add("fusion_seconds", (id, s) -> !active(s) || !s.expires() ? "-1" : Long.toString((s.remainingTicks(SVFrameMMO.currentTick()) + 19L) / 20L));
        add("dance_cooldown", (id, s) -> Long.toString((SVFrameMMOCobblemon.fusions().cooldowns().danceRemainingMillis(id) + 999L) / 1000L));
        add("potara_cooldown", (id, s) -> Long.toString((SVFrameMMOCobblemon.fusions().cooldowns().potaraRemainingMillis(id) + 999L) / 1000L));
        for (int slot = 0; slot < 4; slot++) {
            final int index = slot;
            add("move_" + (slot + 1), (id, s) -> !active(s) || index >= s.moveIds().size() ? "" : s.moveIds().get(index));
        }
        SVFrameMMOCobblemon.LOG.info("Placeholder API fusion bridge enabled with {} placeholders", IDS.size());
    }

    private static boolean active(FusionSession session) { return session != null && session.activated(); }

    private static void add(String path, BiFunction<UUID, FusionSession, String> resolver) {
        Identifier id = Identifier.of(NAMESPACE, path);
        IDS.add(id);
        Placeholders.register(id, (context, argument) -> {
            if (!context.hasGameProfile()) return PlaceholderResult.invalid();
            UUID playerId = context.gameProfile().getId();
            return PlaceholderResult.value(resolver.apply(playerId, SVFrameMMOCobblemon.fusions().session(playerId)));
        });
    }
}
