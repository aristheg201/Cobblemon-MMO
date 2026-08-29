package vn.svframe.svframemmo.trigger;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframelib.player.skillmod.SkillModifier;
import vn.svframe.svframelib.script.util.expression.bool.BooleanExpression;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.experience.EXPSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/** Parses the native line-trigger surface into server behavior. */
public final class NativeTriggerRegistry {
    private NativeTriggerRegistry() { }

    public static Trigger parse(String line) {
        if (line == null || line.isBlank()) throw new IllegalArgumentException("Trigger line cannot be empty");
        MMOLineConfig config = new MMOLineConfig(line);
        String type = norm(config.getKey());
        long delay = Math.max(0L, Math.round(config.getDouble("delay", 0d) * 20d));
        Trigger body = switch (type) {
            case "exp", "experience" -> experience(config);
            case "class-points", "classpoints" -> points(config, PointType.CLASS);
            case "skill-points", "skillpoints" -> points(config, PointType.SKILL);
            case "attribute-points", "attributepoints" -> points(config, PointType.ATTRIBUTE);
            case "reallocation-points", "reallocationpoints" -> points(config, PointType.REALLOCATION);
            case "mana" -> resource(config, ResourceType.MANA);
            case "stamina" -> resource(config, ResourceType.STAMINA);
            case "stellium" -> resource(config, ResourceType.STELLIUM);
            case "bindskill", "bind-skill" -> bindSkill(config);
            case "levelupskill", "level-skill", "levelup-skill" -> levelSkill(config);
            case "unlockskill", "unlock-skill" -> unlockSkill(config);
            case "unlockslot", "unlock-slot" -> unlockSlot(config);
            case "message" -> message(config);
            case "command" -> command(config);
            case "item", "vanilla" -> item(config);
            case "sound", "playsound", "play-sound" -> sound(config);
            case "skill-buff", "skill-modifier", "skillmodifier" -> skillModifier(config, line);
            case "stat", "statmodifier", "stat-modifier" -> stat(config, line);
            default -> throw new IllegalArgumentException("Unsupported native trigger type '" + config.getKey() + "'");
        };
        if (delay == 0L) return body;
        return new DelayedTrigger(body, delay);
    }

    public static List<Trigger> parseAll(Object raw) {
        if (!(raw instanceof Collection<?> collection)) return List.of();
        ArrayList<Trigger> result = new ArrayList<>(collection.size());
        for (Object value : collection) if (value != null) result.add(parse(String.valueOf(value)));
        return List.copyOf(result);
    }

    private enum PointType { CLASS, SKILL, ATTRIBUTE, REALLOCATION }
    private enum ResourceType { MANA, STAMINA, STELLIUM }

    private static Trigger points(MMOLineConfig config, PointType type) {
        int amount = (int) Math.round(roll(config.getString("amount", "1")));
        return removable(player -> changePoints(player, type, amount), player -> changePoints(player, type, -amount));
    }

    private static void changePoints(PlayerData player, PointType type, int amount) {
        switch (type) {
            case CLASS -> player.giveClassPoints(amount);
            case SKILL -> player.giveSkillPoints(amount);
            case ATTRIBUTE -> player.giveAttributePoints(amount);
            case REALLOCATION -> player.giveReallocationPoints(amount);
        }
    }

    private static Trigger experience(MMOLineConfig config) {
        String amount = config.getString("amount", "0");
        String profession = config.getString("profession", null);
        EXPSource source;
        try { source = EXPSource.valueOf(config.getString("source", "API").trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unknown EXP source", exception); }
        EXPSource finalSource = source;
        return simple(player -> {
            double value = roll(amount);
            if (profession == null || profession.isBlank()) player.giveExperience(value, finalSource);
            else player.getProfessions().giveExperience(SVFrameMMO.professions().getOrThrow(profession), value, finalSource);
        });
    }

    private static Trigger resource(MMOLineConfig config, ResourceType type) {
        String amount = config.getString("amount", "0");
        String op = norm(config.getString("operation", "give"));
        return simple(player -> {
            double value = roll(amount);
            switch (type) {
                case MANA -> applyResource(op, value, player::setMana, player::giveMana);
                case STAMINA -> applyResource(op, value, player::setStamina, player::giveStamina);
                case STELLIUM -> applyResource(op, value, player::setStellium, player::giveStellium);
            }
        });
    }

    @FunctionalInterface private interface ResourceSetter { boolean apply(double value, ResourceUpdateReason reason); }
    private static void applyResource(String operation, double value, ResourceSetter setter, ResourceSetter giver) {
        switch (operation) {
            case "set" -> setter.apply(value, ResourceUpdateReason.MECHANIC);
            case "take" -> giver.apply(-value, ResourceUpdateReason.MECHANIC);
            case "give" -> giver.apply(value, ResourceUpdateReason.MECHANIC);
            default -> throw new IllegalArgumentException("Unknown resource trigger operation '" + operation + "'");
        }
    }

    private static Trigger bindSkill(MMOLineConfig config) {
        int slot = config.getInt("slot");
        String skill = config.getString("skill");
        return removable(player -> player.bindSkill(slot, skill), player -> player.unbindSkill(slot));
    }

    private static Trigger levelSkill(MMOLineConfig config) {
        String skill = config.getString("skill");
        int amount = config.getInt("amount");
        return removable(player -> player.setSkillLevel(skill, player.getSkillLevel(skill) + amount),
                player -> player.setSkillLevel(skill, Math.max(1, player.getSkillLevel(skill) - amount)));
    }

    private static Trigger unlockSkill(MMOLineConfig config) {
        String skill = config.getString("skill");
        return removable(player -> player.unlock("skill:" + norm(skill)), player -> player.lock("skill:" + norm(skill)));
    }

    private static Trigger unlockSlot(MMOLineConfig config) {
        int slot = config.getInt("slot");
        return removable(player -> player.unlock("slot:" + slot), player -> player.lock("slot:" + slot));
    }

    private static Trigger message(MMOLineConfig config) {
        String format = config.getString("format");
        return simple(player -> {
            if (player.getPlayer() != null) player.getPlayer().sendMessage(Text.literal(format(player, format)));
        });
    }

    private static Trigger command(MMOLineConfig config) {
        String format = config.getString("format");
        return simple(player -> {
            var online = player.getPlayer();
            if (online == null || online.getServer() == null) return;
            String parsed = format(player, format);
            if (parsed.startsWith("/")) parsed = parsed.substring(1);
            online.getServer().getCommandManager().executeWithPrefix(online.getServer().getCommandSource(), parsed);
        });
    }

    private static Trigger item(MMOLineConfig config) {
        String type = config.getString("type").trim().toLowerCase(Locale.ROOT);
        if (!type.contains(":")) type = "minecraft:" + type;
        Identifier id = Identifier.of(type);
        var item = Registries.ITEM.get(id);
        if (item == null || Registries.ITEM.getId(item).toString().equals("minecraft:air") && !id.toString().equals("minecraft:air"))
            throw new IllegalArgumentException("Unknown item '" + id + "'");
        int amount = Math.max(1, config.getInt("amount", 1));
        return simple(player -> {
            if (player.getPlayer() == null) return;
            ItemStack stack = new ItemStack(item, amount);
            if (!player.getPlayer().giveItemStack(stack) && !stack.isEmpty()) player.getPlayer().dropItem(stack, false);
        });
    }

    private static Trigger sound(MMOLineConfig config) {
        Identifier sound = resolveSoundId(config.getString("sound"));
        if (sound == null) throw new IllegalArgumentException("Unknown sound '" + config.getString("sound") + "'");
        float volume = (float) config.getDouble("volume", 1d);
        float pitch = (float) config.getDouble("pitch", 1d);
        return simple(player -> {
            var online = player.getPlayer();
            if (online == null) return;
            Registries.SOUND_EVENT.getEntry(sound).ifPresent(entry -> online.getServerWorld().playSound(
                    null, online.getX(), online.getY(), online.getZ(), entry, SoundCategory.PLAYERS, volume, pitch));
        });
    }

    private static Identifier resolveSoundId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.indexOf(':') >= 0) {
            Identifier direct = Identifier.tryParse(raw.toLowerCase(Locale.ROOT));
            return direct != null && Registries.SOUND_EVENT.containsId(direct) ? direct : null;
        }
        String legacy = raw.trim().toUpperCase(Locale.ROOT);
        for (Identifier candidate : Registries.SOUND_EVENT.getIds()) {
            String normalized = candidate.getPath().replace('.', '_').replace('/', '_').toUpperCase(Locale.ROOT);
            if (normalized.equals(legacy)) return candidate;
        }
        Identifier direct = Identifier.tryParse("minecraft:" + raw.toLowerCase(Locale.ROOT));
        return direct != null && Registries.SOUND_EVENT.containsId(direct) ? direct : null;
    }

    private static Trigger skillModifier(MMOLineConfig config, String sourceLine) {
        String parameter = config.getString("modifier");
        double amount = config.getDouble("amount");
        String formula = config.getString("formula", "true");
        ModifierType type = ModifierType.valueOf(UtilityMethods.enumName(config.getString("type", "FLAT")));
        List<SkillHandler<?>> targets = SVFrameLib.inst().getSkills().getHandlers().stream()
                .filter(skill -> evaluateSkillFormula(skill, formula)).toList();
        UUID modifierId = UUID.nameUUIDFromBytes(("svframemmo:skill-trigger:" + sourceLine).getBytes(StandardCharsets.UTF_8));
        return new Trigger() {
            private SkillModifier modifier() {
                return new SkillModifier(modifierId, "svframemmo_skill_trigger", parameter, targets, amount, type,
                        EquipmentSlot.OTHER, ModifierSource.OTHER);
            }
            @Override public void apply(PlayerData player) { modifier().register(player.getMMOPlayerData()); }
            @Override public void remove(PlayerData player) { modifier().unregister(player.getMMOPlayerData()); }
            @Override public boolean removable() { return true; }
            @Override public boolean temporary() { return true; }
        };
    }

    private static boolean evaluateSkillFormula(SkillHandler<?> skill, String formula) {
        String parsed = formula;
        for (String category : skill.getCategories()) parsed = parsed.replace("<" + category + ">", "true");
        parsed = parsed.replaceAll("<.*?>", "false");
        return BooleanExpression.eval(parsed);
    }

    private static Trigger stat(MMOLineConfig config, String sourceLine) {
        String stat = UtilityMethods.enumName(config.getString("stat"));
        double amount = config.getDouble("amount");
        ModifierType type = ModifierType.valueOf(UtilityMethods.enumName(config.getString("type", "FLAT")));
        UUID modifierId = UUID.nameUUIDFromBytes(("svframemmo:trigger:" + sourceLine).getBytes(StandardCharsets.UTF_8));
        Function<PlayerData, StatModifier> modifier = player -> new StatModifier(modifierId, "svframemmo_trigger", stat, amount,
                type, EquipmentSlot.OTHER, ModifierSource.OTHER);
        return new Trigger() {
            @Override public void apply(PlayerData player) {
                var instance = player.getMMOPlayerData().getStatMap().getInstance(stat);
                StatModifier old = instance.getModifier(modifierId);
                if (old == null) instance.registerModifier(modifier.apply(player));
                else instance.registerModifier(old.add(amount));
            }
            @Override public void remove(PlayerData player) { player.getMMOPlayerData().getStatMap().getInstance(stat).removeModifier(modifierId); }
            @Override public boolean removable() { return true; }
            @Override public boolean temporary() { return true; }
        };
    }

    private static String format(PlayerData player, String input) {
        String name = player.getPlayer() == null ? player.getUniqueId().toString() : player.getPlayer().getName().getString();
        return input.replace("%player%", name).replace("%player_name%", name).replace("<player>", name)
                .replace("{player}", name).replace("{level}", Integer.toString(player.getLevel()))
                .replace("{class}", player.getClassId());
    }

    private static Trigger simple(java.util.function.Consumer<PlayerData> apply) {
        return new Trigger() { @Override public void apply(PlayerData player) { apply.accept(player); } };
    }

    private static Trigger removable(java.util.function.Consumer<PlayerData> apply, java.util.function.Consumer<PlayerData> remove) {
        return new Trigger() {
            @Override public void apply(PlayerData player) { apply.accept(player); }
            @Override public void remove(PlayerData player) { remove.accept(player); }
            @Override public boolean removable() { return true; }
        };
    }

    private record DelayedTrigger(Trigger delegate, long delayTicks) implements Trigger {
        DelayedTrigger { Objects.requireNonNull(delegate, "delegate"); }
        @Override public void apply(PlayerData player) {
            SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + delayTicks, () -> delegate.apply(player));
        }
        @Override public void remove(PlayerData player) { delegate.remove(player); }
        @Override public boolean removable() { return delegate.removable(); }
        @Override public boolean temporary() { return delegate.temporary(); }
        @Override public long delayTicks() { return 0L; }
    }

    /** Random amount syntax: one number or inclusive min-max. */
    public static double roll(String raw) {
        String value = raw == null ? "0" : raw.trim();
        int split = findRangeDash(value);
        if (split < 1) return Double.parseDouble(value);
        double min = Double.parseDouble(value.substring(0, split).trim());
        double max = Double.parseDouble(value.substring(split + 1).trim());
        if (max < min) { double swap = min; min = max; max = swap; }
        return min == max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }

    private static int findRangeDash(String value) {
        for (int index = 1; index < value.length(); index++) {
            if (value.charAt(index) == '-' && value.charAt(index - 1) != 'e' && value.charAt(index - 1) != 'E') return index;
        }
        return -1;
    }

    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'); }
}
