package vn.svframe.svframeitems.registry;

import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframeitems.config.*;
import vn.svframe.svframeitems.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SVFrameItemsRegistry {
    private volatile Map<String,ItemType> types = Map.of();
    private volatile Map<String,ItemRarity> rarities = Map.of();
    private volatile Map<String,ItemDefinition> items = Map.of();
    private volatile Map<String,ItemSetDefinition> sets = Map.of();
    private volatile Map<String,UpgradeTemplate> upgrades = Map.of();
    private volatile Map<String,RecipeDefinition> recipes = Map.of();
    private volatile Map<String,LootTableDefinition> lootTables = Map.of();

    private final Map<String,ItemType> externalTypes = new ConcurrentHashMap<>();
    private final Map<String,ItemRarity> externalRarities = new ConcurrentHashMap<>();
    private final Map<String,ItemDefinition> externalItems = new ConcurrentHashMap<>();

    public synchronized void reload(Path root) throws IOException {
        Map<String,ItemType> nextTypes = parse(root.resolve("types.yml"), DefinitionParser::type);
        nextTypes.putAll(externalTypes);
        Map<String,ItemRarity> nextRarities = parse(root.resolve("rarities.yml"), DefinitionParser::rarity);
        nextRarities.putAll(externalRarities);
        Map<String,UpgradeTemplate> nextUpgrades = parse(root.resolve("upgrades.yml"), DefinitionParser::upgrade);
        Map<String,ItemSetDefinition> nextSets = parse(root.resolve("sets.yml"), DefinitionParser::set);
        Map<String,RecipeDefinition> nextRecipes = parse(root.resolve("recipes.yml"), DefinitionParser::recipe);
        Map<String,LootTableDefinition> nextLoot = parse(root.resolve("loot.yml"), DefinitionParser::loot);
        Map<String,ItemDefinition> nextItems = parseDirectory(root.resolve("items"), DefinitionParser::item);
        nextItems.putAll(externalItems);
        validate(nextTypes, nextRarities, nextItems, nextSets, nextUpgrades, nextRecipes, nextLoot);
        types = Map.copyOf(nextTypes); rarities = Map.copyOf(nextRarities); items = Map.copyOf(nextItems);
        sets = Map.copyOf(nextSets); upgrades = Map.copyOf(nextUpgrades); recipes = Map.copyOf(nextRecipes); lootTables = Map.copyOf(nextLoot);
    }

    public ItemType type(String id) { return types.get(ItemType.normalize(id)); }
    public ItemRarity rarity(String id) { return rarities.get(ItemType.normalize(id)); }
    public ItemDefinition item(String id) { return items.get(ItemType.normalize(id)); }
    public ItemSetDefinition set(String id) { return id == null ? null : sets.get(ItemType.normalize(id)); }
    public UpgradeTemplate upgrade(String id) { return id == null ? null : upgrades.get(ItemType.normalize(id)); }
    public RecipeDefinition recipe(String id) { return recipes.get(ItemType.normalize(id)); }
    public LootTableDefinition lootTable(String id) { return lootTables.get(ItemType.normalize(id)); }
    public Collection<ItemType> types() { return types.values(); }
    public Collection<ItemRarity> rarities() { return rarities.values(); }
    public Collection<ItemDefinition> items() { return items.values(); }
    public Collection<ItemSetDefinition> sets() { return sets.values(); }
    public Collection<UpgradeTemplate> upgrades() { return upgrades.values(); }
    public Collection<RecipeDefinition> recipes() { return recipes.values(); }
    public Collection<LootTableDefinition> lootTables() { return lootTables.values(); }
    public String summary() { return "types="+types.size()+",rarities="+rarities.size()+",items="+items.size()+",sets="+sets.size()+",upgrades="+upgrades.size()+",recipes="+recipes.size()+",lootTables="+lootTables.size(); }

    public void registerExternal(ItemType value) { externalTypes.put(value.id(), value); }
    public void registerExternal(ItemRarity value) { externalRarities.put(value.id(), value); }
    public void registerExternal(ItemDefinition value) { externalItems.put(value.id(), value); }

    private static void validate(Map<String,ItemType> types, Map<String,ItemRarity> rarities, Map<String,ItemDefinition> items,
                                 Map<String,ItemSetDefinition> sets, Map<String,UpgradeTemplate> upgrades,
                                 Map<String,RecipeDefinition> recipes, Map<String,LootTableDefinition> loot) {
        if (!rarities.containsKey("common")) throw new IllegalStateException("rarities.yml must define common");
        for (ItemDefinition item : items.values()) {
            if (!types.containsKey(item.typeId())) throw new IllegalStateException("Item " + item.id() + " references unknown type " + item.typeId());
            for (String rarity : item.rarityWeights().keySet()) if (!rarities.containsKey(rarity)) throw new IllegalStateException("Item " + item.id() + " references unknown rarity " + rarity);
            if (item.upgradeTemplateId() != null && !upgrades.containsKey(item.upgradeTemplateId())) throw new IllegalStateException("Item " + item.id() + " references unknown upgrade template " + item.upgradeTemplateId());
            if (item.setId() != null && !sets.containsKey(item.setId())) throw new IllegalStateException("Item " + item.id() + " references unknown set " + item.setId());
        }
        for (ItemSetDefinition set : sets.values()) for (String piece : set.pieces()) if (!items.containsKey(piece)) throw new IllegalStateException("Set " + set.id() + " references unknown item " + piece);
        for (RecipeDefinition recipe : recipes.values()) {
            if (!items.containsKey(recipe.outputItemId())) throw new IllegalStateException("Recipe " + recipe.id() + " output missing item " + recipe.outputItemId());
            for (RecipeDefinition.Ingredient ingredient : recipe.ingredients()) if (ingredient.kind() == RecipeDefinition.IngredientKind.SVFRAME_ITEM && !items.containsKey(ingredient.id())) throw new IllegalStateException("Recipe " + recipe.id() + " ingredient missing item " + ingredient.id());
        }
        for (LootTableDefinition table : loot.values()) for (LootTableDefinition.Entry entry : table.entries()) if (!items.containsKey(entry.itemId())) throw new IllegalStateException("Loot table " + table.id() + " references unknown item " + entry.itemId());
    }

    private interface Factory<T> { T create(String id, Map<String,Object> section); }
    private static <T> Map<String,T> parse(Path file, Factory<T> factory) throws IOException {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        return parseRoot(YamlLite.map(YamlLite.parse(file)), factory);
    }
    private static <T> Map<String,T> parseDirectory(Path directory, Factory<T> factory) throws IOException {
        Map<String,T> out = new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) return out;
        try (var stream = Files.walk(directory)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(SVFrameItemsRegistry::yaml).sorted().toList()) {
                Map<String,T> parsed = parse(file, factory);
                for (Map.Entry<String,T> entry : parsed.entrySet()) if (out.put(entry.getKey(), entry.getValue()) != null) throw new IllegalStateException("Duplicate definition " + entry.getKey());
            }
        }
        return out;
    }
    private static <T> Map<String,T> parseRoot(Map<String,Object> root, Factory<T> factory) {
        Map<String,T> out = new LinkedHashMap<>();
        for (Map.Entry<String,Object> entry : root.entrySet()) {
            T value = factory.create(entry.getKey(), ConfigValues.map(entry.getValue()));
            String id = ItemType.normalize(entry.getKey());
            if (out.put(id, value) != null) throw new IllegalStateException("Duplicate definition " + id);
        }
        return out;
    }
    private static boolean yaml(Path path) { String name=path.getFileName().toString().toLowerCase(Locale.ROOT); return name.endsWith(".yml") || name.endsWith(".yaml"); }
}
