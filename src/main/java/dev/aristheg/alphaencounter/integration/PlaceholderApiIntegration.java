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
    private static final List<String> TARGET_KEYS = List.of("name","id","tier","state","hp","max_hp","hp_percent","species","level","aspects","dimension","biome","x","y","z","distance","participants","category","pokemon_uuid","entity_uuid");
    private final EncounterRuntime runtime;
    private final TextService textService;
    public PlaceholderApiIntegration(EncounterRuntime runtime, TextService textService) { this.runtime=runtime; this.textService=textService; register(); }
    private void register() {
        Placeholders.register(Identifier.of(AlphaEncounterMod.MOD_ID,"active_count"), (ctx,arg)->PlaceholderResult.value(Text.literal(Integer.toString(runtime.activeCount()))));
        Placeholders.register(Identifier.of(AlphaEncounterMod.MOD_ID,"cooldown_ticks"), (ctx,arg)-> arg==null||arg.isBlank()?PlaceholderResult.invalid("Expected encounter id"):PlaceholderResult.value(Text.literal(Long.toString(runtime.cooldownRemainingTicks(firstArg(arg))))));
        Placeholders.register(Identifier.of(AlphaEncounterMod.MOD_ID,"cooldown_seconds"), (ctx,arg)-> arg==null||arg.isBlank()?PlaceholderResult.invalid("Expected encounter id"):PlaceholderResult.value(Text.literal(Long.toString((runtime.cooldownRemainingTicks(firstArg(arg))+19L)/20L))));
        for(String key:TARGET_KEYS) Placeholders.register(Identifier.of(AlphaEncounterMod.MOD_ID,key),(ctx,arg)->targetValue(ctx,arg,key));
        AlphaEncounterMod.LOGGER.info("Registered {} Text Placeholder API placeholders.", TARGET_KEYS.size()+3);
    }
    private PlaceholderResult targetValue(PlaceholderContext ctx,String arg,String key){ ActiveEncounter active=runtime.resolveExternalTarget(ctx.source(),arg==null||arg.isBlank()?"nearest":firstArg(arg)); if(active==null)return PlaceholderResult.invalid("No matching Alpha-Encounter"); TextContext tc=runtime.textContext(ctx.server(),active,ctx.player()); return PlaceholderResult.value(Text.literal(textService.value(tc,key))); }
    private String firstArg(String arg){ String t=arg.trim(); int i=t.indexOf(' '); return i<0?t:t.substring(0,i); }
    public Text parse(Text input,TextContext context){ if(context==null||context.server()==null)return input; PlaceholderContext pc=context.player()!=null?PlaceholderContext.of(context.player()):PlaceholderContext.of(context.server()); return Placeholders.parseText(input,pc); }
    public boolean available(){return true;} public String status(){return "loaded";}
}
