package vn.svframe.svframelib.fabric;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import java.util.Locale;
import java.util.Map;

/** Native 1.21.1 packet particle types, including Bukkit names and parameterized dust/block/item effects. */
public final class NativeParticles {
    private NativeParticles() { }
    private static final Map<String,String> ALIASES = Map.ofEntries(
            Map.entry("REDSTONE","dust"), Map.entry("SPELL_WITCH","witch"), Map.entry("SPELL_INSTANT","instant_effect"),
            Map.entry("SPELL_MOB","entity_effect"), Map.entry("SPELL","effect"), Map.entry("SMOKE_NORMAL","smoke"),
            Map.entry("SMOKE_LARGE","large_smoke"), Map.entry("EXPLOSION_NORMAL","poof"), Map.entry("EXPLOSION_LARGE","explosion"),
            Map.entry("EXPLOSION_HUGE","explosion_emitter"), Map.entry("CRIT_MAGIC","enchanted_hit"), Map.entry("ENCHANTMENT_TABLE","enchant"),
            Map.entry("VILLAGER_HAPPY","happy_villager"), Map.entry("VILLAGER_ANGRY","angry_villager"), Map.entry("TOTEM","totem_of_undying"),
            Map.entry("SNOWBALL","item_snowball"), Map.entry("SNOW_SHOVEL","snowflake"), Map.entry("BLOCK_CRACK","block"),
            Map.entry("BLOCK_DUST","falling_dust"), Map.entry("ITEM_CRACK","item"), Map.entry("FIREWORKS_SPARK","firework"));
    public static ParticleEffect effect(String name, int rgb, float size, String material) {
        String raw = name == null ? "end_rod" : name.trim();
        String mapped = ALIASES.getOrDefault(raw.toUpperCase(Locale.ROOT),raw.toLowerCase(Locale.ROOT));
        Identifier id = Identifier.of(mapped.contains(":") ? mapped : "minecraft:"+mapped);
        if (!Registries.PARTICLE_TYPE.containsId(id)) throw new IllegalArgumentException("Unknown particle: " + name);
        var type = Registries.PARTICLE_TYPE.get(id);
        if (type instanceof SimpleParticleType simple) return simple;
        Vector3f color = new Vector3f(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);
        float scale = Float.isFinite(size) ? Math.max(0.01f,Math.min(4,size)) : 1;
        if (type == ParticleTypes.DUST) return new DustParticleEffect(color,scale);
        if (type == ParticleTypes.DUST_COLOR_TRANSITION) return new DustColorTransitionParticleEffect(color,new Vector3f(1,1,1),scale);
        if (type == ParticleTypes.ENTITY_EFFECT) return EntityEffectParticleEffect.create(ParticleTypes.ENTITY_EFFECT,0xff000000 | (rgb&0xffffff));
        Identifier materialId = Identifier.of(material == null || material.isBlank() ? "minecraft:stone" : material.contains(":") ? material.toLowerCase(Locale.ROOT) : "minecraft:"+material.toLowerCase(Locale.ROOT));
        if (type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_MARKER || type == ParticleTypes.FALLING_DUST || type == ParticleTypes.DUST_PILLAR) {
            if (!Registries.BLOCK.containsId(materialId)) throw new IllegalArgumentException("Unknown particle block: " + material);
            ParticleType<BlockStateParticleEffect> blockType = type == ParticleTypes.BLOCK ? ParticleTypes.BLOCK : type == ParticleTypes.BLOCK_MARKER ? ParticleTypes.BLOCK_MARKER : type == ParticleTypes.FALLING_DUST ? ParticleTypes.FALLING_DUST : ParticleTypes.DUST_PILLAR;
            return new BlockStateParticleEffect(blockType,Registries.BLOCK.get(materialId).getDefaultState());
        }
        if (type == ParticleTypes.ITEM) {
            if (!Registries.ITEM.containsId(materialId)) throw new IllegalArgumentException("Unknown particle item: " + material);
            return new ItemStackParticleEffect(ParticleTypes.ITEM,new ItemStack(Registries.ITEM.get(materialId)));
        }
        throw new IllegalArgumentException("Particle requires additional parameters: " + name);
    }
}
