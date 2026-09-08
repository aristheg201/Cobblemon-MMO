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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Locale;

public final class TextService {
    private static final List<String> KEYS=List.of("name","id","tier","state","hp","max_hp","hp_percent","species","level","aspects","dimension","biome","x","y","z","distance","participants","category","pokemon_uuid","entity_uuid","catch_seconds","player_name");
    private final AlphaEncounterConfigManager config;
    private final MiniMessage miniMessage=MiniMessage.miniMessage();
    private PlaceholderIntegration placeholders=PlaceholderIntegration.NONE;
    private FabricServerAudiences audiences;
    public TextService(AlphaEncounterConfigManager config){this.config=config;}
    public void initialize(EncounterRuntime runtime){ if(!FabricLoader.getInstance().isModLoaded("placeholder-api")){AlphaEncounterMod.LOGGER.info("Text Placeholder API not installed; integration disabled.");return;} try{Class<?> type=Class.forName("dev.aristheg.alphaencounter.integration.PlaceholderApiIntegration");Constructor<?> c=type.getConstructor(EncounterRuntime.class,TextService.class);placeholders=(PlaceholderIntegration)c.newInstance(runtime,this);}catch(Throwable t){AlphaEncounterMod.LOGGER.error("Could not initialize Text Placeholder API integration.",t);} }
    public void start(MinecraftServer server){audiences=FabricServerAudiences.of(server);} public void stop(){if(audiences!=null){try{audiences.close();}catch(Throwable ignored){}audiences=null;}}
    public Text render(String raw,TextContext context){if(raw==null||raw.isBlank())return Text.literal("");try{Component component=miniMessage.deserialize(raw,resolver(context));Text nativeText=audiences==null?Text.literal(raw):audiences.toNative(component);if(config.general().usePlaceholderApi&&placeholders.available())nativeText=placeholders.parse(nativeText,context);return nativeText;}catch(Throwable t){AlphaEncounterMod.LOGGER.warn("MiniMessage render failed: {}",raw,t);return Text.literal(raw);}}
    public void broadcast(MinecraftServer server,String raw,TextContext context){if(server!=null&&raw!=null&&!raw.isBlank())server.getPlayerManager().broadcast(render(raw,context),false);}
    public String integrationStatus(){return "MiniMessage=enabled, PlaceholderAPI="+placeholders.status()+", externalParsing="+(config.general().usePlaceholderApi?"enabled":"disabled");}
    public String value(TextContext c,String key){if(c==null||key==null)return "";ActiveEncounter a=c.encounter();var d=c.definition();var p=c.pokemon();var e=c.entity();return switch(key){case"name"->d==null?(a==null?"":a.definitionId):d.displayName;case"id"->a==null?(d==null?"":d.id):a.definitionId;case"tier"->a==null?(d==null?"":d.tier):a.tierId;case"state"->a==null?"":a.state.name().toLowerCase(Locale.ROOT);case"hp"->a==null?"0":Integer.toString(Math.max(0,Math.round(a.hp)));case"max_hp"->a==null?"0":Integer.toString(Math.max(0,Math.round(a.maxHp)));case"hp_percent"->a==null||a.maxHp<=0?"0.0":one(a.hp*100.0/a.maxHp);case"species"->p==null?"":p.getSpecies().getName();case"level"->p==null?"0":Integer.toString(p.getLevel());case"aspects"->p==null?"":String.join(",",p.getAspects());case"dimension"->a==null?"":a.dimension;case"biome"->c.biome()==null?"":c.biome();case"x"->a==null?"0":one(a.x);case"y"->a==null?"0":one(a.y);case"z"->a==null?"0":one(a.z);case"distance"->c.player()==null||e==null?"":one(Math.sqrt(e.squaredDistanceTo(c.player())));case"participants"->a==null?"0":Integer.toString(a.participants.size());case"category"->d==null||d.categoryId==null?"":d.categoryId;case"pokemon_uuid"->a==null||a.pokemonId==null?"":a.pokemonId.toString();case"entity_uuid"->a==null||a.entityId==null?"":a.entityId.toString();case"catch_seconds"->d==null?"0":Integer.toString(d.catchPhaseSeconds);case"player_name"->c.player()==null?"":c.player().getName().getString();default->"";};}
    private TagResolver resolver(TextContext c){TagResolver.Builder b=TagResolver.builder();for(String key:KEYS){String tag=key.equals("player_name")?key:"ae_"+key;b.resolver(Placeholder.unparsed(tag,value(c,key)));}return b.build();}
    private String one(double v){return String.format(Locale.ROOT,"%.1f",v);}
}
