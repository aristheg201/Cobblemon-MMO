package io.lumine.mythic.lib.version;

import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.Locale;

public class Sounds {
    public static final SoundEvent ENTITY_ENDERMAN_HURT = sound("entity.enderman.hurt");
    public static final SoundEvent ENTITY_ENDERMAN_DEATH = sound("entity.enderman.death");
    public static final SoundEvent ENTITY_ENDERMAN_TELEPORT = sound("entity.enderman.teleport");
    public static final SoundEvent ENTITY_FIREWORK_ROCKET_LARGE_BLAST = sound("entity.firework_rocket.large_blast");
    public static final SoundEvent ENTITY_FIREWORK_ROCKET_TWINKLE = sound("entity.firework_rocket.twinkle");
    public static final SoundEvent ENTITY_FIREWORK_ROCKET_BLAST = sound("entity.firework_rocket.blast");
    public static final SoundEvent ENTITY_ZOMBIE_PIGMAN_ANGRY = sound("entity.zombified_piglin.angry");
    public static final SoundEvent BLOCK_NOTE_BLOCK_HAT = sound("block.note_block.hat");
    public static final SoundEvent BLOCK_NOTE_BLOCK_PLING = sound("block.note_block.pling");
    public static final SoundEvent BLOCK_NOTE_BLOCK_BELL = sound("block.note_block.bell");
    public static final SoundEvent ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR = sound("entity.zombie.attack_wooden_door");
    public static final SoundEvent ENTITY_ENDER_DRAGON_GROWL = sound("entity.ender_dragon.growl");
    public static final SoundEvent ENTITY_ENDER_DRAGON_FLAP = sound("entity.ender_dragon.flap");
    public static final SoundEvent ENTITY_ZOMBIE_ATTACK_IRON_DOOR = sound("entity.zombie.attack_iron_door");
    public static final SoundEvent ENTITY_PLAYER_ATTACK_CRIT = sound("entity.player.attack.crit");
    public static final SoundEvent BLOCK_END_PORTAL_FRAME_FILL = sound("block.end_portal_frame.fill");
    public static final SoundEvent ENTITY_SNOWBALL_THROW = sound("entity.snowball.throw");
    public static final SoundEvent BLOCK_BREWING_STAND_BREW = sound("block.brewing_stand.brew");
    public static final SoundEvent BLOCK_GLASS_BREAK = sound("block.glass.break");
    public static final SoundEvent ENTITY_GENERIC_EXPLODE = sound("entity.generic.explode");
    public static final SoundEvent ENTITY_EXPERIENCE_ORB_PICKUP = sound("entity.experience_orb.pickup");
    public static final SoundEvent ENTITY_WITCH_DRINK = sound("entity.witch.drink");
    public static final SoundEvent BLOCK_FIRE_AMBIENT = sound("block.fire.ambient");
    public static final SoundEvent ENTITY_CHICKEN_EGG = sound("entity.chicken.egg");
    public static final SoundEvent ENTITY_PLAYER_ATTACK_SWEEP = sound("entity.player.attack.sweep");
    public static final SoundEvent ENTITY_BLAZE_HURT = sound("entity.blaze.hurt");
    public static final SoundEvent BLOCK_FIRE_EXTINGUISH = sound("block.fire.extinguish");
    public static final SoundEvent BLOCK_SNOW_BREAK = sound("block.snow.break");
    public static final SoundEvent ENTITY_PLAYER_LEVELUP = sound("entity.player.levelup");
    public static final SoundEvent BLOCK_GRAVEL_BREAK = sound("block.gravel.break");
    public static final SoundEvent ENTITY_ZOMBIE_HURT = sound("entity.zombie.hurt");
    public static final SoundEvent ENTITY_COW_HURT = sound("entity.cow.hurt");
    public static final SoundEvent ENTITY_PLAYER_ATTACK_KNOCKBACK = sound("entity.player.attack.knockback");
    public static final SoundEvent ENTITY_SHEEP_DEATH = sound("entity.sheep.death");
    public static final SoundEvent ENTITY_BLAZE_AMBIENT = sound("entity.blaze.ambient");
    public static final SoundEvent ENTITY_LLAMA_ANGRY = sound("entity.llama.angry");
    public static final SoundEvent ENTITY_WITHER_SHOOT = sound("entity.wither.shoot");
    public static final SoundEvent BLOCK_ANVIL_LAND = sound("block.anvil.land");
    public static final SoundEvent ENTITY_CHICKEN_HURT = sound("entity.chicken.hurt");
    public static final SoundEvent ENTITY_VILLAGER_NO = sound("entity.villager.no");
    public static final SoundEvent ENTITY_ITEM_BREAK = sound("entity.item.break");
    public static final SoundEvent UI_BUTTON_CLICK = sound("ui.button.click");
    public static final SoundEvent ENTITY_GENERIC_EAT = sound("entity.generic.eat");
    public static final SoundEvent BLOCK_IRON_DOOR_OPEN = sound("block.iron_door.open");

    public static SoundEvent fromName(String... names) {
        if (names == null) return null;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String normalized = name.toLowerCase(Locale.ROOT).replace('_', '.');
            SoundEvent found = sound(normalized);
            if (found != null) return found;
        }
        return null;
    }

    private static SoundEvent sound(String path) {
        Identifier id = Identifier.tryParse(path.contains(":") ? path : "minecraft:" + path);
        return id == null ? null : Registries.SOUND_EVENT.get(id);
    }
}
