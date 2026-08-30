package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Resolves real Cobblemon/Mega Showdown items for Pokemon-skill presentation. */
public final class PokemonSkillIconResolver {
    private PokemonSkillIconResolver() { }

    public static Identifier resolve(MoveTemplate move) {
        String type = token(move == null ? "normal" : move.getElementalType().getName());
        boolean status = move != null && "status".equalsIgnoreCase(move.getDamageCategory().getName());
        Identifier mega = Identifier.tryParse("mega_showdown:" + type + "_tera_shard");
        Identifier cobblemon = Identifier.tryParse("cobblemon:" + type + "_gem");

        if (status) {
            if (exists(cobblemon)) return cobblemon;
            if (exists(mega)) return mega;
        } else {
            if (exists(mega)) return mega;
            if (exists(cobblemon)) return cobblemon;
        }

        Identifier normalGem = Identifier.tryParse("cobblemon:normal_gem");
        if (exists(normalGem)) return normalGem;
        return Registries.ITEM.getId(Items.AMETHYST_SHARD);
    }

    public static ItemStack stack(String moveId) {
        return stack(Moves.getByNameOrDummy(moveId));
    }

    public static ItemStack stack(MoveTemplate move) {
        return new ItemStack(Registries.ITEM.get(resolve(move)));
    }

    /** IconOptions requires a map for namespaced item IDs on 1.21.1. */
    public static Map<String, Object> iconConfig(MoveTemplate move) {
        LinkedHashMap<String, Object> icon = new LinkedHashMap<>();
        icon.put("item", resolve(move).toString());
        return icon;
    }

    public static String source(MoveTemplate move) {
        return resolve(move).getNamespace();
    }

    private static boolean exists(Identifier id) {
        return id != null && Registries.ITEM.containsId(id);
    }

    private static String token(String raw) {
        String value = raw == null ? "normal" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return value.isBlank() ? "normal" : value;
    }
}
