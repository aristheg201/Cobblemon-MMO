package vn.svframe.svframemmo.placeholder;

import vn.svframe.svframelib.fabric.runtime.NativePlaceholderRegistry;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.experience.Profession;
import vn.svframe.svframemmo.pvp.PvpModeRuntime;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native public placeholder bridge for SVFrameMMO progression state. */
public final class SVFrameMMOPlaceholders {
    private static final String NAMESPACE = "svframemmo";
    private static final String LEGACY_NAMESPACE = "mmo" + "core";
    private static final Pattern PERCENT = Pattern.compile("%([A-Za-z0-9_.:\\-]+)%");
    private static volatile boolean installed;

    private SVFrameMMOPlaceholders() { }

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        NativePlaceholderRegistry.register(NAMESPACE, SVFrameMMOPlaceholders::resolve);
        NativePlaceholderRegistry.register(LEGACY_NAMESPACE, SVFrameMMOPlaceholders::resolve);
    }

    /** Resolves the provider argument without a namespace prefix. Unknown keys resolve to an empty fallback. */
    public static String resolve(UUID playerId, String argument) {
        if (playerId == null || argument == null || argument.isBlank()) return "";
        PlayerData data = SVFrameMMO.playerData().find(playerId);
        if (data == null) data = SVFrameMMO.playerData().get(playerId);
        String raw = argument.trim();
        String key = raw.toLowerCase(Locale.ROOT);

        return switch (key) {
            case "level" -> Integer.toString(data.getLevel());
            case "experience", "exp" -> number(data.getExperience());
            case "next_level", "exp_next_level" -> Long.toString(data.getLevelUpExperience());
            case "level_percent" -> percent(data.getExperience(), data.getLevelUpExperience());
            case "class" -> data.getProfess().getName();
            case "class_id" -> data.getProfess().getId();
            case "class_points" -> Integer.toString(data.getClassPoints());
            case "skill_points" -> Integer.toString(data.getSkillPoints());
            case "attribute_points" -> Integer.toString(data.getAttributePoints());
            case "attribute_reallocation_points" -> Integer.toString(data.getAttributeReallocationPoints());
            case "health" -> number(data.getHealth());
            case "max_health" -> number(data.getMaxResource(PlayerResource.HEALTH));
            case "health_bar" -> bar(data.getHealth(), data.getMaxResource(PlayerResource.HEALTH), "§c", "§4", "§8");
            case "mana" -> number(data.getMana());
            case "mana_bar" -> bar(data.getMana(), data.getMaxResource(PlayerResource.MANA), "§9", "§3", "§7");
            case "stamina" -> number(data.getStamina());
            case "stamina_bar" -> bar(data.getStamina(), data.getMaxResource(PlayerResource.STAMINA), "§a", "§2", "§8");
            case "stellium" -> number(data.getStellium());
            case "stellium_bar" -> bar(data.getStellium(), data.getMaxResource(PlayerResource.STELLIUM), "§9", "§b", "§f");
            case "is_casting" -> Boolean.toString(SVFrameMMO.skillBar().isCasting(playerId));
            case "in_combat" -> Boolean.toString(data.isInCombat());
            case "pvp_mode" -> Boolean.toString(data.isOnline() && PvpModeRuntime.instance().isEnabled(data.getPlayer()));
            default -> resolveParameterized(data, raw, key);
        };
    }

    /** Parses both native angle placeholders and the retained percent syntax used by existing configs. */
    public static String parse(UUID playerId, String input) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        Matcher matcher = PERCENT.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            String argument = namespacedArgument(token);
            if (argument == null) continue;
            matcher.appendReplacement(output, Matcher.quoteReplacement(resolve(playerId, argument)));
        }
        matcher.appendTail(output);
        return NativePlaceholderRegistry.parse(playerId, output.toString());
    }

    private static String resolveParameterized(PlayerData data, String raw, String key) {
        if (key.startsWith("skill_level_")) {
            String skillId = raw.substring("skill_level_".length());
            ClassSkill skill = data.getProfess().getSkill(skillId);
            return skill == null ? "0" : Integer.toString(data.getSkillLevel(skill.getSkill()));
        }
        if (key.startsWith("skill_tree_points_"))
            return Integer.toString(data.getSkillTrees().getPoints(raw.substring("skill_tree_points_".length())));
        if (key.startsWith("bound_skill_parameter_"))
            return boundSkillParameter(data, raw.substring("bound_skill_parameter_".length()));
        if (key.startsWith("skill_parameter_"))
            return skillParameter(data, raw.substring("skill_parameter_".length()));
        if (key.startsWith("skill_modifier_"))
            return skillParameter(data, raw.substring("skill_modifier_".length()));
        if (key.startsWith("attribute_points_spent_")) {
            String id = raw.substring("attribute_points_spent_".length()).replace('_', '-');
            return Integer.toString(data.getAttributes().getInstance(id).getBase());
        }
        if (key.startsWith("attribute_")) {
            String id = raw.substring("attribute_".length()).replace('_', '-');
            return Integer.toString(data.getAttributes().getAttribute(id));
        }
        if (key.startsWith("profession_experience_")) {
            Profession profession = profession(raw.substring("profession_experience_".length()));
            return profession == null ? "0" : number(data.getProfessions().getExperience(profession));
        }
        if (key.startsWith("profession_next_level_")) {
            Profession profession = profession(raw.substring("profession_next_level_".length()));
            return profession == null ? "0" : Long.toString(data.getProfessions().getLevelUpExperience(profession));
        }
        if (key.startsWith("profession_percent_")) {
            Profession profession = profession(raw.substring("profession_percent_".length()));
            return profession == null ? "0" : percent(data.getProfessions().getExperience(profession), data.getProfessions().getLevelUpExperience(profession));
        }
        if (key.startsWith("profession_")) {
            Profession profession = profession(raw.substring("profession_".length()));
            return profession == null ? "0" : Integer.toString(data.getProfessions().getLevel(profession));
        }
        if (key.startsWith("id_bound_")) {
            ClassSkill skill = bound(data, raw.substring("id_bound_".length()));
            return skill == null ? "" : skill.getSkill().getId();
        }
        if (key.startsWith("passive_bound_")) {
            ClassSkill skill = bound(data, raw.substring("passive_bound_".length()));
            return Boolean.toString(skill != null && skill.getTrigger().isPassive());
        }
        if (key.startsWith("cooldown_bound_")) {
            ClassSkill skill = bound(data, raw.substring("cooldown_bound_".length()));
            return skill == null ? "0" : number(data.getMMOPlayerData().getCooldownMap().getCooldown(skill.getCooldownPath()));
        }
        if (key.startsWith("bound_")) {
            ClassSkill skill = bound(data, raw.substring("bound_".length()));
            if (skill == null) return "";
            boolean cooldown = data.getMMOPlayerData().getCooldownMap().isOnCooldown(skill.getCooldownPath());
            return (cooldown ? "§c" : "§a") + skill.getSkill().getName();
        }
        if (key.startsWith("exp_multiplier_")) {
            String target = raw.substring("exp_multiplier_".length());
            return number(boosterMultiplier(target) * 100d);
        }
        if (key.startsWith("exp_boost_")) {
            String target = raw.substring("exp_boost_".length());
            return number((boosterMultiplier(target) - 1d) * 100d);
        }
        if (key.startsWith("stat_")) {
            String stat = enumName(raw.substring("stat_".length()));
            return number(data.getMMOPlayerData().getStatMap().getStat(stat));
        }
        return "";
    }

    private static String skillParameter(PlayerData data, String params) {
        String[] split = params.split(":", 2);
        if (split.length != 2) return "0";
        String parameter = split[0], skillId = split[1];
        ClassSkill skill = data.getProfess().getSkill(skillId);
        if (skill == null || !skill.getParameters().containsKey(parameter)) return "0";
        double base = skill.getParameter(parameter, data);
        return number(data.getMMOPlayerData().getSkillModifierMap().calculateValue(skill.getSkill(), base, parameter));
    }

    private static String boundSkillParameter(PlayerData data, String params) {
        String[] split = params.split(":", 2);
        if (split.length != 2) return "0";
        int slot = positiveInt(split[1]);
        ClassSkill skill = slot < 1 ? null : data.getBoundSkill(slot);
        if (skill == null || !skill.getParameters().containsKey(split[0])) return "0";
        double base = skill.getParameter(split[0], data);
        return number(data.getMMOPlayerData().getSkillModifierMap().calculateValue(skill.getSkill(), base, split[0]));
    }

    private static ClassSkill bound(PlayerData data, String rawSlot) {
        int slot = positiveInt(rawSlot);
        return slot < 1 ? null : data.getBoundSkill(slot);
    }

    private static Profession profession(String raw) {
        return SVFrameMMO.professions().get(kebab(raw));
    }

    private static double boosterMultiplier(String raw) {
        String normalized = kebab(raw);
        if (normalized.equals("main")) return SVFrameMMO.boosters().multiplier(null);
        Profession profession = SVFrameMMO.professions().get(normalized);
        return profession == null ? 1d : SVFrameMMO.boosters().multiplier(profession.getKey());
    }

    private static String namespacedArgument(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        String nativePrefix = NAMESPACE + "_";
        String legacyPrefix = LEGACY_NAMESPACE + "_";
        if (lower.startsWith(nativePrefix)) return token.substring(nativePrefix.length());
        if (lower.startsWith(legacyPrefix)) return token.substring(legacyPrefix.length());
        String nativeColon = NAMESPACE + ":";
        String legacyColon = LEGACY_NAMESPACE + ":";
        if (lower.startsWith(nativeColon)) return token.substring(nativeColon.length());
        if (lower.startsWith(legacyColon)) return token.substring(legacyColon.length());
        return null;
    }

    private static String bar(double current, double max, String full, String half, String empty) {
        double safeMax = Math.max(1.0E-5d, max);
        double ratio = 20d * Math.max(0d, Math.min(current, safeMax)) / safeMax;
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < 20; i++) result.append(ratio >= i ? full : ratio >= i - .5d ? half : empty).append('■');
        return result.toString();
    }

    private static String percent(double current, double next) {
        return next <= 0d ? "0" : number(current / next * 100d);
    }

    private static int positiveInt(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value < 1 ? -1 : value;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static String kebab(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private static String enumName(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) return "0";
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
