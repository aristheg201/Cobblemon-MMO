import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import vn.guild.core.*;
import vn.svframe.compat.*;

public final class CompatibilityRegression {
    private static final Map<String,Boolean> RESULTS=new LinkedHashMap<>();
    private static void check(String key, boolean result) { RESULTS.put(key,result); if(!result)throw new AssertionError(key); }
    public static void main(String[] args) throws Exception {
        Path temporary=Files.createTempDirectory("svframe-compat-tests-");UUID player=UUID.randomUUID();Gson gson=new Gson();
        Path quest=temporary.resolve("missing/quests");
        check("quest_missing_directory_recreated",SafeQuestStore.save(gson,quest,player,Map.of("progress",3)));
        check("quest_progress_written",JsonParser.parseString(Files.readString(quest.resolve(player+".json"))).getAsJsonObject().get("progress").getAsInt()==3);
        Files.delete(quest.resolve(player+".json"));Files.delete(quest);
        check("quest_directory_removed_while_online_recreated",SafeQuestStore.save(gson,quest,player,Map.of("progress",4)));
        Path blocked=temporary.resolve("blocked");Files.writeString(blocked,"do not overwrite");
        check("quest_storage_failure_returns_without_tick_exception",!SafeQuestStore.save(gson,blocked,player,Map.of("progress",9)));
        check("quest_existing_file_preserved",Files.readString(blocked).equals("do not overwrite"));
        Files.delete(blocked);
        check("quest_failed_write_can_retry",SafeQuestStore.save(gson,blocked,player,Map.of("progress",9)));
        try(var paths=Files.walk(temporary)){check("quest_no_orphan_temporary_files",paths.noneMatch(f->f.toString().endsWith(".tmp")));}
        for(String ore:List.of("coal","copper","iron","gold","lapis","redstone","emerald","diamond")) {
            JsonObject filter=new JsonObject();filter.addProperty("tags","minecraft:"+ore+"_ore");
            check(ore+"_legacy_tag_matches",GuildEngine.matches(filter,Map.of("tags",List.of("minecraft:"+ore+"_ores"))));
        }
        JsonObject filter=JsonParser.parseString("{\"block\":\"minecraft:redstone_ore\"}").getAsJsonObject();
        check("block_id_kept_singular",GuildEngine.matches(filter,Map.of("block","minecraft:redstone_ore")));
        check("invalid_plural_block_id_rejected",!GuildEngine.matches(filter,Map.of("block","minecraft:redstone_ores")));
        filter=JsonParser.parseString("{\"tags\":[\"minecraft:coal_ore\",\"minecraft:redstone_ore\"],\"level\":{\"min\":2,\"max\":5}}").getAsJsonObject();
        check("tag_arrays_and_numeric_filters_preserved",GuildEngine.matches(filter,Map.of("tags",List.of("minecraft:redstone_ores"),"level",3)));
        check("wrong_ore_not_credited",!GuildEngine.matches(filter,Map.of("tags",List.of("minecraft:diamond_ores"),"level",3)));
        check("other_filter_limits_preserved",!GuildEngine.matches(filter,Map.of("tags",List.of("minecraft:redstone_ores"),"level",9)));
        check("currency_namespace_resolves_api_id",GuildCurrencyIds.resolve("BEconomy:secondary").equals("secondary"));
        check("custom_currency_is_not_replaced",GuildCurrencyIds.resolve("customcoin").equals("customcoin"));
        Definitions definitions=gson.fromJson(Files.readString(Path.of(args[0])),Definitions.class);
        // Minimal real contract derived from the supplied redstone template; no unsupported integrations.
        var template=definitions.templates.stream().filter(t->t.id.equals("resource_redstone")).findFirst().orElseThrow();
        Definitions minimal=new Definitions();minimal.ranks.add(definitions.ranks.getFirst());minimal.templates.add(template);
        template.required_rank=minimal.ranks.getFirst().rank;template.minimum_cap=1;
        // Set only the real old tag in this active contract, proving persisted pre-fix contracts migrate behavior.
        template.objectives.getFirst().filters.addProperty("tags","minecraft:redstone_ore");
        GuildEngine engine=new GuildEngine(temporary.resolve("guild-players"),minimal,Set.of(),s->{});
        GuildEngine.Delivery delivery=new GuildEngine.Delivery(){
            public boolean deliver(UUID id,String key,Definitions.Template t,PlayerState.Contract c){return false;}
            public boolean ownsSkill(UUID id,String skill){return false;}
            public String classId(UUID id){return "";}
        };
        var offers=engine.offers(player,delivery);
        check("actual_guild_engine_generates_offer",!offers.isEmpty());
        engine.accept(player,offers.getFirst().id,delivery,false);
        var contract=engine.state(player).active.values().iterator().next();
        engine.publish(new GuildEngine.Signal(player,"resource","overworld:1",System.currentTimeMillis()+10,1,Map.of("tags",List.of("minecraft:redstone_ores"),"position","overworld:1")));
        check("actual_guild_engine_credits_legacy_mining_contract",contract.progress[0]==1);
        engine.publish(new GuildEngine.Signal(player,"resource","overworld:1",System.currentTimeMillis()+10,1,Map.of("tags",List.of("minecraft:redstone_ores"),"position","overworld:1")));
        check("duplicate_mining_event_not_credited_twice",contract.progress[0]==1);
        engine.save(player);
        GuildEngine reopened=new GuildEngine(temporary.resolve("guild-players"),minimal,Set.of(),s->{});
        check("mining_progress_survives_reload",reopened.state(player).active.values().iterator().next().progress[0]==1);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS)+"\n");
        System.out.println("Compatibility regression: "+RESULTS.size()+" PASS");
    }
}
