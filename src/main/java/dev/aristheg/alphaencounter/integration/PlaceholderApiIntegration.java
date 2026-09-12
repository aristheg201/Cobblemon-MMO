package dev.aristheg.alphaencounter.integration;

import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import dev.aristheg.alphaencounter.runtime.EncounterRuntime;
import dev.aristheg.alphaencounter.text.TextContext;
import dev.aristheg.alphaencounter.text.TextService;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class PlaceholderApiIntegration implements PlaceholderIntegration {
    private static final List<String> TARGET_KEYS = List.of(
        "name", "id", "tier", "state", "hp", "max_hp", "hp_percent", "species", "level", "aspects",
        "dimension", "biome", "x", "y", "z", "distance", "participants", "category", "pokemon_uuid", "entity_uuid"
    );

    private final EncounterRuntime runtime;
    private final TextService textService;

    public PlaceholderApiIntegration(EncounterRuntime runtime, TextService textService) {
        this.runtime = runtime;
        this.textService = textService;
        register();
    }

    private void register() {
        Placeholders.register(
            Identifier.of(AlphaEncounterMod.MOD_ID, "active_count"),
            (context, argument) -> PlaceholderResult.value(Text.literal(Integer.toString(runtime.activeCount())))
        );
        Placeholders.register(
            Identifier.of(AlphaEncounterMod.MOD_ID, "cooldown_ticks"),
            (context, argument) -> missingArgument(argument)
                ? PlaceholderResult.invalid(AlphaEncounterMod.UI.text("placeholder.error.expected_encounter_id"))
                : PlaceholderResult.value(Text.literal(Long.toString(runtime.cooldownRemainingTicks(firstArg(argument)))))
        );
        Placeholders.register(
            Identifier.of(AlphaEncounterMod.MOD_ID, "cooldown_seconds"),
            (context, argument) -> missingArgument(argument)
                ? PlaceholderResult.invalid(AlphaEncounterMod.UI.text("placeholder.error.expected_encounter_id"))
                : PlaceholderResult.value(Text.literal(Long.toString((runtime.cooldownRemainingTicks(firstArg(argument)) + 19L) / 20L)))
        );
        for (String key : TARGET_KEYS) {
            Placeholders.register(
                Identifier.of(AlphaEncounterMod.MOD_ID, key),
                (context, argument) -> targetValue(context, argument, key)
            );
        }
        AlphaEncounterMod.LOGGER.info("Registered {} Text Placeholder API placeholders.", TARGET_KEYS.size() + 3);
    }

    private PlaceholderResult targetValue(PlaceholderContext context, String argument, String key) {
        ActiveEncounter active = runtime.resolveExternalTarget(
            context.source(),
            missingArgument(argument) ? "nearest" : firstArg(argument)
        );
        if (active == null) {
            return PlaceholderResult.invalid(AlphaEncounterMod.UI.text("placeholder.error.no_matching_encounter"));
        }
        TextContext textContext = runtime.textContext(context.server(), active, context.player());
        return PlaceholderResult.value(Text.literal(textService.value(textContext, key)));
    }

    private boolean missingArgument(String argument) {
        return argument == null || argument.isBlank();
    }

    private String firstArg(String argument) {
        String trimmed = argument.trim();
        int separator = trimmed.indexOf(' ');
        return separator < 0 ? trimmed : trimmed.substring(0, separator);
    }

    @Override
    public Text parse(Text input, TextContext context) {
        if (context == null || context.server() == null) return input;
        PlaceholderContext placeholderContext = context.player() != null
            ? PlaceholderContext.of(context.player())
            : PlaceholderContext.of(context.server());
        return Placeholders.parseText(input, placeholderContext);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String status() {
        return "loaded";
    }
}
