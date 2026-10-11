package vn.svframe.svframeitems.runtime;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.fabric.runtime.RpgProfileRegistry;
import vn.svframe.svframeitems.item.ItemCodec;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.model.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Imported item restrictions and presentation; canonical class state comes from the registered RPG owner. */
public final class SourceItemRules {
    private static Map<String,Map<String,Object>> rules=Map.of();
    private SourceItemRules(){}
    public static Map<String,Map<String,Object>> snapshot(){return rules;}
    public static void restore(Map<String,Map<String,Object>> old){rules=old;}
    public static void reload(Path root) throws IOException {
        Map<String,Map<String,Object>> next=new LinkedHashMap<>();Path folder=root.resolve("rules");
        if(Files.isDirectory(folder))try(var paths=Files.walk(folder)){
            for(Path file:paths.filter(p->Files.isRegularFile(p)&&p.toString().endsWith(".yml")).sorted().toList())for(var row:YamlLite.map(YamlLite.parse(file)).entrySet()){
                if(next.putIfAbsent(ItemType.normalize(row.getKey()),Map.copyOf(YamlLite.map(row.getValue())))!=null)throw new IOException("Duplicate source item policy: "+row.getKey());
            }
        }
        rules=Map.copyOf(next);
    }
    public static Map<String,Object> rule(String id){return rules.getOrDefault(ItemType.normalize(id),Map.of());}
    public static Map<String,Object> rule(ItemStack stack){return ItemCodec.read(stack).map(i->rule(i.definitionId())).orElse(Map.of());}
    public static boolean flag(ItemStack stack,String key){var rules=rule(stack);if(rules.containsKey(key))return Boolean.TRUE.equals(rules.get(key));var nbt=stack.get(DataComponentTypes.CUSTOM_DATA);return nbt!=null&&nbt.copyNbt().getCompound("SVFRAME_ITEM_FLAGS").getBoolean(key);}
    public static String profileKey(ServerPlayerEntity player){return RpgProfileRegistry.resolve(player.getUuid()).map(p->p.playerClass()+":"+p.level()).orElse("");}
    public static boolean eligible(ServerPlayerEntity player,ItemDefinition item){
        var raw=rule(item.id()).get("required-class");if(!(raw instanceof List<?> required)||required.isEmpty())return true;
        var profile=RpgProfileRegistry.resolve(player.getUuid());
        return profile.isPresent()&&required.stream().map(String::valueOf).anyMatch(id->id.equalsIgnoreCase(profile.get().playerClass()));
    }
    public static boolean eligible(ServerPlayerEntity player,ItemStack stack){var raw=rule(stack).get("required-class");if(!(raw instanceof List<?> required)||required.isEmpty())return true;var profile=RpgProfileRegistry.resolve(player.getUuid());return profile.isPresent()&&required.stream().map(String::valueOf).anyMatch(id->id.equalsIgnoreCase(profile.get().playerClass()));}
    public static void present(ItemStack stack,ItemDefinition definition,ItemInstance instance) {
        var rule=rule(definition.id());if(rule.isEmpty())return;
        if(Boolean.TRUE.equals(rule.get("unbreakable")))stack.set(DataComponentTypes.UNBREAKABLE,new UnbreakableComponent(false));
        stack.set(DataComponentTypes.CUSTOM_NAME,legacy(definition.displayName()));
        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS,AttributeModifiersComponent.DEFAULT.withShowInTooltip(false));
        if(rule.get("custom-model-data") instanceof Number n)stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelDataComponent(n.intValue()));
        if(rule.get("dye-color") instanceof Number n)stack.set(DataComponentTypes.DYED_COLOR,new DyedColorComponent(n.intValue(),!Boolean.TRUE.equals(rule.get("hide-dye"))));
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA,stack,nbt->{
            net.minecraft.nbt.NbtCompound flags=new net.minecraft.nbt.NbtCompound();for(String key:java.util.List.of("disable-droping","disable-death-drop","disable-enchanting","disable-repairing","disable-crafting","disable-smelting","disable-smithing"))flags.putBoolean(key,Boolean.TRUE.equals(rule.get(key)));nbt.put("SVFRAME_ITEM_FLAGS",flags);
            if(rule.get("armor-texture") instanceof String texture)nbt.putString("SVFRAME_ARMOR_TEXTURE",texture);
            if(rule.get("item-model") instanceof String model)nbt.putString("SVFRAME_ITEM_MODEL",model);
        });
        List<Text> lore=new ArrayList<>();if(rule.get("lore") instanceof List<?> lines)for(Object line:lines)lore.add(legacy(String.valueOf(line)));
        lore.add(Text.translatable("svframeitems.item.level",instance.itemLevel()).formatted(net.minecraft.util.Formatting.GRAY).styled(s->s.withItalic(false)));
        if(definition.setId()!=null)lore.add(Text.translatable("svframeitems.item.set",SVFrameItems.registry().set(definition.setId())==null?definition.setId():legacy(SVFrameItems.registry().set(definition.setId()).displayName())).formatted(net.minecraft.util.Formatting.LIGHT_PURPLE).styled(s->s.withItalic(false)));
        for(var stat:upgrades(instance,instance.stats()))lore.add(Text.translatable("svframeitems.item.stat",Text.translatable("svframeitems.stat."+stat.stat().toLowerCase(java.util.Locale.ROOT)),java.math.BigDecimal.valueOf(stat.value()).stripTrailingZeros().toPlainString()).formatted(net.minecraft.util.Formatting.GREEN).styled(s->s.withItalic(false)));
        stack.set(DataComponentTypes.LORE,new LoreComponent(lore));
    }
    private static Text legacy(String value){
        var result=Text.empty();var style=net.minecraft.text.Style.EMPTY.withItalic(false);StringBuilder part=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);var format=(c=='&'||c=='§')&&i+1<value.length()?net.minecraft.util.Formatting.byCode(Character.toLowerCase(value.charAt(i+1))):null;
            if(format==null){part.append(c);continue;}
            if(!part.isEmpty()){result.append(Text.literal(part.toString()).setStyle(style));part.setLength(0);}
            i++;style=format==net.minecraft.util.Formatting.RESET?net.minecraft.text.Style.EMPTY.withItalic(false):format.isColor()?style.withExclusiveFormatting(format).withItalic(false):style.withFormatting(format);
        }
        if(!part.isEmpty())result.append(Text.literal(part.toString()).setStyle(style));return result;
    }
    /** Authored stats are supplied by EquipmentRuntime, rather than vanilla material defaults. */
    public static boolean ownsNativeAttributes(ItemStack stack){var data=stack.get(DataComponentTypes.CUSTOM_DATA);return data!=null&&data.copyNbt().contains("SVFRAME_ITEM_FLAGS",net.minecraft.nbt.NbtElement.COMPOUND_TYPE);}
    /** The original per-stat template uses independent percentage or additive upgrades. */
    public static List<ItemStat> upgrades(ItemInstance instance,List<ItemStat> stats) {
        Object rawUpgrade=rule(instance.definitionId()).get("upgrade-stats");if(!(rawUpgrade instanceof Map<?,?>))return stats;
        var upgrade=YamlLite.map(rawUpgrade);if(upgrade.isEmpty()||instance.upgradeLevel()==0)return stats;
        List<ItemStat> result=new ArrayList<>(stats.size());
        for(ItemStat stat:stats){Object modifier=upgrade.get(stat.stat());double value=stat.value();if(modifier!=null){String raw=String.valueOf(modifier);value=raw.endsWith("%")?value*(1+Double.parseDouble(raw.substring(0,raw.length()-1))/100*instance.upgradeLevel()):value+Double.parseDouble(raw)*instance.upgradeLevel();}result.add(new ItemStat(stat.stat(),value,stat.type()));}
        // Templates may add a stat not present on the original item.
        Set<String> present=new HashSet<>();stats.forEach(s->present.add(s.stat()));
        for(var entry:upgrade.entrySet())if(!present.contains(entry.getKey())&&!String.valueOf(entry.getValue()).endsWith("%"))result.add(new ItemStat(entry.getKey(),Double.parseDouble(String.valueOf(entry.getValue()))*instance.upgradeLevel(),vn.svframe.svframelib.fabric.runtime.NativeStatEngine.ModifierType.FLAT));
        return List.copyOf(result);
    }
}
