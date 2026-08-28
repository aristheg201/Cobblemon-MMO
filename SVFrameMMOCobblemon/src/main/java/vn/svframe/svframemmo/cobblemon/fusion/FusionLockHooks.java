package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.api.events.CobblemonEvents;

/** Cancellable Cobblemon event guards for Pokemon locked by an active fusion. */
public final class FusionLockHooks {
    private FusionLockHooks() { }

    public static void register(FusionService fusions) {
        CobblemonEvents.POKEMON_RECALL_PRE.subscribe(event -> {
            if (fusions.isPokemonLocked(event.getPokemon().getUuid())) event.cancel();
        });
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(event -> {
            if (fusions.isPokemonLocked(event.getPokemon().getUuid())) event.cancel();
        });
        CobblemonEvents.POKEMON_RELEASED_EVENT_PRE.subscribe(event -> {
            if (fusions.isPokemonLocked(event.getPokemon().getUuid())) event.cancel();
        });
        CobblemonEvents.TRADE_EVENT_PRE.subscribe(event -> {
            if (fusions.isPokemonLocked(event.getTradeParticipant1Pokemon().getUuid())
                    || fusions.isPokemonLocked(event.getTradeParticipant2Pokemon().getUuid())) event.cancel();
        });
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {
            boolean locked = false;
            outer:
            for (var actor : event.getBattle().getActors()) {
                for (var pokemon : actor.getPokemonList()) {
                    if (fusions.isPokemonLocked(pokemon.getUuid())) {
                        locked = true;
                        break outer;
                    }
                }
            }
            if (locked) event.cancel();
        });
    }
}
