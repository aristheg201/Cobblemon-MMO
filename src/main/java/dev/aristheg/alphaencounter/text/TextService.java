package dev.aristheg.alphaencounter.text;

import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.config.AlphaEncounterConfigManager;
import dev.aristheg.alphaencounter.integration.PlaceholderIntegration;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import dev.aristheg.alphaencounter.runtime.EncounterRuntime;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TextService {
    private static final List<String> KEYS = List.of(
        "name",
        "id",
        "tier",
        "state",
        "hp",
        "max_hp",
        "hp_percent",
        "species",
        "level",
        "aspects",
        "entity_aspects",
        "pokemon_properties",
        "dimension",
        "biome",
        "x",
        "y",
        "z",
        "distance",
        "participants",
        "category",
        "pokemon_uuid",
        "entity_uuid",
        "catch_seconds",
        "player_name"
    );

    private final AlphaEncounterConfigManager config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private PlaceholderIntegration placeholders = PlaceholderIntegration.NONE;
    private FabricServerAudiences audiences;

    public TextService(AlphaEncounterConfigManager config) {
        this.config = config;
    }

    public void initialize(EncounterRuntime runtime) {
        if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            AlphaEncounterMod.LOGGER.info("Text Placeholder API not installed; integration disabled.");
            return;
        }
        try {
            Class<?> type = Class.forName("dev.aristheg.alphaencounter.integration.PlaceholderApiIntegration");
            Constructor<?> constructor = type.getConstructor(EncounterRuntime.class, TextService.class);
            placeholders = (PlaceholderIntegration) constructor.newInstance(runtime, this);
        } catch (Throwable t) {
            AlphaEncounterMod.LOGGER.error("Could not initialize Text Placeholder API integration.", t);
        }
    }

    public void start(MinecraftServer server) {
        audiences = FabricServerAudiences.of(server);
    }

    public void stop() {
        if (audiences == null) return;
        try {
            audiences.close();
        } catch (Throwable ignored) {
        }
        audiences = null;
    }

    public Text message(String key, TextContext context) {
        return message(key, context, Map.of());
    }

    public Text message(String key, TextContext context, Map<String, String> arguments) {
        return render(AlphaEncounterMod.UI.text(key), context, arguments);
    }

    public Text render(String raw, TextContext context) {
        return render(raw, context, Map.of());
    }

    public Text render(String raw, TextContext context, Map<String, String> arguments) {
        if (raw == null || raw.isBlank()) return Text.literal("");
        try {
            Component component = miniMessage.deserialize(raw, resolver(context, arguments));
            Text nativeText = audiences == null ? Text.literal(raw) : audiences.toNative(component);
            if (config.general().usePlaceholderApi && placeholders.available()) {
                nativeText = placeholders.parse(nativeText, context);
            }
            return nativeText;
        } catch (Throwable t) {
            AlphaEncounterMod.LOGGER.warn("MiniMessage render failed: {}", raw, t);
            return Text.literal(raw);
        }
    }

    public void broadcast(MinecraftServer server, String raw, TextContext context) {
        if (server != null && raw != null && !raw.isBlank()) {
            server.getPlayerManager().broadcast(render(raw, context), false);
        }
    }

    public String integrationStatus() {
        return "MiniMessage=enabled, PlaceholderAPI=" + placeholders.status()
            + ", externalParsing=" + (config.general().usePlaceholderApi ? "enabled" : "disabled");
    }

    public String value(TextContext context, String key) {
        if (context == null || key == null) return "";
        ActiveEncounter active = context.encounter();
        var definition = context.definition();
        var pokemon = context.pokemon();
        var entity = context.entity();
        return switch (key) {
            case "name" -> definition == null ? (active == null ? "" : active.definitionId) : definition.displayName;
            case "id" -> active == null ? (definition == null ? "" : definition.id) : active.definitionId;
            case "tier" -> active == null ? (definition == null ? "" : definition.tier) : active.tierId;
            case "state" -> active == null ? "" : active.state.name().toLowerCase(Locale.ROOT);
            case "hp" -> active == null ? "0" : Integer.toString(Math.max(0, Math.round(active.hp)));
            case "max_hp" -> active == null ? "0" : Integer.toString(Math.max(0, Math.round(active.maxHp)));
            case "hp_percent" -> active == null || active.maxHp <= 0 ? "0.0" : one(active.hp * 100.0 / active.maxHp);
            case "species" -> pokemon == null ? "" : pokemon.getSpecies().getName();
            case "level" -> pokemon == null ? "0" : Integer.toString(pokemon.getLevel());
            case "aspects" -> pokemon == null ? "" : String.join(",", pokemon.getAspects());
            case "entity_aspects" -> entity == null ? "" : String.join(",", entity.getAspects());
            case "pokemon_properties" -> definition == null || definition.pokemon == null ? "" : definition.pokemon;
            case "dimension" -> active == null ? "" : active.dimension;
            case "biome" -> context.biome() == null ? "" : context.biome();
            case "x" -> active == null ? "0" : one(active.x);
            case "y" -> active == null ? "0" : one(active.y);
            case "z" -> active == null ? "0" : one(active.z);
            case "distance" -> context.player() == null || entity == null ? "" : one(Math.sqrt(entity.squaredDistanceTo(context.player())));
            case "participants" -> active == null ? "0" : Integer.toString(active.participants.size());
            case "category" -> definition == null || definition.categoryId == null ? "" : definition.categoryId;
            case "pokemon_uuid" -> active == null || active.pokemonId == null ? "" : active.pokemonId.toString();
            case "entity_uuid" -> active == null || active.entityId == null ? "" : active.entityId.toString();
            case "catch_seconds" -> definition == null ? "0" : Integer.toString(definition.catchPhaseSeconds);
            case "player_name" -> context.player() == null ? "" : context.player().getName().getString();
            default -> "";
        };
    }

    private TagResolver resolver(TextContext context, Map<String, String> arguments) {
        TagResolver.Builder builder = TagResolver.builder();
        for (String key : KEYS) {
            String tag = key.equals("player_name") ? key : "ae_" + key;
            builder.resolver(Placeholder.unparsed(tag, value(context, key)));
        }
        if (arguments != null) {
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                builder.resolver(Placeholder.unparsed(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()));
            }
        }
        return builder.build();
    }

    private String one(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
