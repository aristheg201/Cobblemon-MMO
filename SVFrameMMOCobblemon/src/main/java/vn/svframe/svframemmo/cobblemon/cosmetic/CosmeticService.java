package vn.svframe.svframemmo.cobblemon.cosmetic;

import com.cobblemon.mod.common.api.moves.Moves;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.event.skill.PlayerCastSkillEvent;
import vn.svframe.svframelib.api.event.skill.SkillCastEvent;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.integration.LuckPermsIntegration;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkill;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent cosmetic ownership/equip service with skill lifecycle VFX. */
public final class CosmeticService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SAVE_TYPE = new TypeToken<Map<String, SavedState>>(){}.getType();
    private final Map<String, CosmeticDefinition> definitions = new ConcurrentHashMap<>();
    private volatile Map<String, String> particleAliases = Map.of();
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final CosmeticRenderer renderer = new CosmeticRenderer();
    private volatile MinecraftServer server;
    private Path saveFile;
    private volatile boolean dirty;

    public void reloadDefinitions() throws java.io.IOException {
        particleAliases = loadParticleAliases();
        LinkedHashMap<String, CosmeticDefinition> next = new LinkedHashMap<>();
        if (Files.isDirectory(CosmeticDefaults.COSMETICS)) {
            try (var stream = Files.walk(CosmeticDefaults.COSMETICS)) {
                for (Path file : stream.filter(Files::isRegularFile).filter(CosmeticService::yaml).sorted().toList()) {
                    CosmeticDefinition definition = parse(file);
                    if (next.putIfAbsent(definition.id(), definition) != null) throw new java.io.IOException("Duplicate cosmetic " + definition.id());
                }
            }
        }
        definitions.clear();
        definitions.putAll(next);
    }

    public void start(MinecraftServer server) {
        this.server = server;
        this.saveFile = server.getSavePath(WorldSavePath.ROOT).resolve("svframemmo-cobblemon-cosmetics.json");
        load();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) onJoin(player);
    }

    public void stop() { save(); renderer.clear(); server = null; }

    public void tick(long tick, MinecraftServer server) {
        renderer.tick(tick, server, this);
        if (dirty && tick % 100L == 0L) save();
    }

    public void onJoin(ServerPlayerEntity player) {
        PlayerState state = state(player.getUuid());
        state.equipped().values().stream().distinct().forEach(id -> {
            CosmeticDefinition definition = definition(id);
            if (definition != null && state.owned().contains(id) && LuckPermsIntegration.has(player, definition.permission()))
                renderer.equip(player, definition);
        });
    }

    public void onDisconnect(ServerPlayerEntity player) { renderer.clearPlayer(player.getUuid()); }

    public void onSkillStart(PlayerCastSkillEvent event) {
        if (event.isCancelled() || event.getResult() == null || !event.getResult().isSuccessful(event.getMetadata())) return;
        triggerSkill(event.getPlayer(), event.getCast().getHandler(), CosmeticDefinition.Trigger.CAST_START,
                event.getMetadata().getTargetLocationOrNull());
    }

    public void onSkillSuccess(SkillCastEvent event) {
        if (event.getResult() == null || !event.getResult().isSuccessful(event.getMetadata())) return;
        Vec3d target = null;
        if (event.getMetadata().getTargetLivingEntityOrNull() != null)
            target = event.getMetadata().getTargetLivingEntityOrNull().getBoundingBox().getCenter();
        else if (event.getMetadata().getTargetLocationOrNull() != null)
            target = event.getMetadata().getTargetLocationOrNull();
        else if (event.getCast().getHandler() instanceof CobblemonMoveSkill)
            target = reacquireMoveTarget(event.getPlayer());
        triggerSkill(event.getPlayer(), event.getCast().getHandler(), CosmeticDefinition.Trigger.CAST_SUCCESS, target);
        if (target != null) triggerSkill(event.getPlayer(), event.getCast().getHandler(), CosmeticDefinition.Trigger.TARGET_HIT, target);
    }

    private static Vec3d reacquireMoveTarget(ServerPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1f).normalize();
        Box search = player.getBoundingBox().stretch(look.multiply(18d)).expand(2d);
        return player.getWorld().getOtherEntities(player, search, entity ->
                        entity instanceof LivingEntity && !entity.isRemoved()
                                && !SVFrameMMOCobblemon.fusions().isVisualEntityOf(player.getUuid(), entity.getUuid()))
                .stream().filter(entity -> {
                    Vec3d delta = entity.getBoundingBox().getCenter().subtract(eye);
                    return delta.lengthSquared() > 0.0001d && delta.normalize().dotProduct(look) >= 0.92d;
                }).min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player)))
                .map(Entity::getBoundingBox).map(Box::getCenter).orElse(null);
    }

    private void triggerSkill(ServerPlayerEntity player, vn.svframe.svframelib.skill.handler.SkillHandler<?> handler,
                              CosmeticDefinition.Trigger trigger, Vec3d target) {
        if (player == null || handler == null) return;
        PlayerState state = state(player.getUuid());
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(CosmeticDefinition.normalizeSkill(handler.getId()));
        if (handler instanceof CobblemonMoveSkill move) ids.add(move.canonicalSkillId());
        for (String skill : ids) {
            String cosmeticId = state.equipped().get(skill);
            CosmeticDefinition definition = definition(cosmeticId);
            if (definition != null && LuckPermsIntegration.has(player, definition.permission())) renderer.trigger(player, definition, trigger, target);
        }
    }

    public Collection<CosmeticDefinition> definitions() {
        return definitions.values().stream().sorted(Comparator.comparing(CosmeticDefinition::id)).toList();
    }
    public CosmeticDefinition definition(String id) { return id == null ? null : definitions.get(CosmeticDefinition.normalize(id)); }
    public int size() { return definitions.size(); }
    public int ownedCount(UUID player) { return state(player).owned().size(); }
    public Set<String> owned(UUID player) { return Set.copyOf(state(player).owned()); }
    public Map<String, String> equipped(UUID player) { return Map.copyOf(state(player).equipped()); }

    public boolean grant(UUID player, String cosmeticId) {
        CosmeticDefinition definition = definition(cosmeticId);
        if (definition == null) return false;
        boolean changed = state(player).owned().add(definition.id());
        if (changed) dirty = true;
        return changed;
    }

    public boolean revoke(UUID player, String cosmeticId) {
        CosmeticDefinition definition = definition(cosmeticId);
        if (definition == null) return false;
        PlayerState state = state(player);
        boolean changed = state.owned().remove(definition.id());
        if (!changed) return false;
        state.equipped().entrySet().removeIf(entry -> entry.getValue().equals(definition.id()));
        ServerPlayerEntity online = server == null ? null : server.getPlayerManager().getPlayer(player);
        renderer.clearPlayer(player);
        if (online != null) onJoin(online);
        dirty = true;
        return true;
    }

    public Result equip(ServerPlayerEntity player, String cosmeticId) {
        CosmeticDefinition definition = definition(cosmeticId);
        if (definition == null) return Result.fail("Unknown cosmetic.");
        PlayerState state = state(player.getUuid());
        if (!state.owned().contains(definition.id())) return Result.fail("You do not own this cosmetic.");
        if (!LuckPermsIntegration.has(player, definition.permission())) return Result.fail("You do not have permission to use this cosmetic.");
        if (!knownTargetSkill(definition.skillId())) return Result.fail("Unknown target skill: " + definition.skillId());
        String old = state.equipped().put(definition.skillId(), definition.id());
        if (definition.id().equals(old)) return Result.ok(definition);
        if (old != null) {
            CosmeticDefinition previous = definition(old);
            if (previous != null) renderer.unequip(player, previous);
        }
        renderer.equip(player, definition);
        dirty = true;
        return Result.ok(definition);
    }

    private static boolean knownTargetSkill(String skillId) {
        if (CobblemonMoveSkillAdapter.isCanonicalSkillId(skillId)) {
            String moveId = CobblemonMoveSkillAdapter.moveIdFromCanonical(skillId);
            return Moves.getByName(moveId) != null;
        }
        return SVFrameLib.inst().getSkills().getHandler(skillId) != null;
    }

    public boolean unequip(ServerPlayerEntity player, String skillId) {
        String skill = CosmeticDefinition.normalizeSkill(skillId);
        String old = state(player.getUuid()).equipped().remove(skill);
        if (old == null) return false;
        CosmeticDefinition definition = definition(old);
        if (definition != null) renderer.unequip(player, definition);
        dirty = true;
        return true;
    }

    public Result preview(ServerPlayerEntity player, String cosmeticId) {
        CosmeticDefinition definition = definition(cosmeticId);
        if (definition == null) return Result.fail("Unknown cosmetic.");
        if (!state(player.getUuid()).owned().contains(definition.id())) return Result.fail("You do not own this cosmetic.");
        if (!LuckPermsIntegration.has(player, definition.permission())) return Result.fail("You do not have permission to use this cosmetic.");
        renderer.trigger(player, definition, CosmeticDefinition.Trigger.PREVIEW, null);
        return Result.ok(definition);
    }

    public boolean isEquipped(UUID player, String cosmeticId) {
        return state(player).equipped().containsValue(CosmeticDefinition.normalize(cosmeticId));
    }

    private PlayerState state(UUID id) {
        return states.computeIfAbsent(id, ignored -> new PlayerState(new LinkedHashSet<>(), new LinkedHashMap<>()));
    }

    private synchronized void load() {
        states.clear();
        if (saveFile == null || !Files.exists(saveFile)) return;
        try (Reader reader = Files.newBufferedReader(saveFile)) {
            Map<String, SavedState> saved = GSON.fromJson(reader, SAVE_TYPE);
            if (saved == null) return;
            saved.forEach((id, value) -> {
                try {
                    LinkedHashSet<String> owned = new LinkedHashSet<>();
                    if (value.owned != null) value.owned.forEach(cosmetic -> owned.add(CosmeticDefinition.normalize(cosmetic)));
                    LinkedHashMap<String, String> equipped = new LinkedHashMap<>();
                    if (value.equipped != null) value.equipped.forEach((skill, cosmetic) ->
                            equipped.put(CosmeticDefinition.normalizeSkill(skill), CosmeticDefinition.normalize(cosmetic)));
                    states.put(UUID.fromString(id), new PlayerState(owned, equipped));
                } catch (RuntimeException ignored) { }
            });
        } catch (Exception error) { throw new IllegalStateException("Could not load cosmetic state", error); }
    }

    public synchronized void save() {
        if (saveFile == null) return;
        try {
            Files.createDirectories(saveFile.getParent());
            LinkedHashMap<String, SavedState> out = new LinkedHashMap<>();
            states.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> out.put(entry.getKey().toString(),
                    new SavedState(new ArrayList<>(entry.getValue().owned()), new LinkedHashMap<>(entry.getValue().equipped()))));
            Path tmp = saveFile.resolveSibling(saveFile.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(out));
            try { Files.move(tmp, saveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(tmp, saveFile, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        } catch (Exception error) { throw new IllegalStateException("Could not save cosmetic state", error); }
    }

    private CosmeticDefinition parse(Path file) throws java.io.IOException {
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        String id = string(root, "id", file.getFileName().toString().replaceFirst("\\.ya?ml$", ""));
        String skill = string(root, "skill", "");
        String name = string(root, "name", id);
        String particleRef = string(root, "particle", "");
        String particle = particleAliases.getOrDefault(particleRef, particleRef);
        String permission = string(root, "permission", "svframemmo.cobblemon.cosmetic.use." + id);
        boolean hide = bool(root.get("hide-without-resource-pack"), false);
        Object rawFallback = root.get("fallback");
        Map<String, Object> fallbackMap = rawFallback == null ? Map.of() : YamlLite.map(rawFallback);
        CosmeticDefinition.Fallback fallback = fallbackMap == null || fallbackMap.isEmpty() ? CosmeticDefinition.Fallback.none()
                : new CosmeticDefinition.Fallback(string(fallbackMap, "particle", ""), integer(fallbackMap.get("count"), 0),
                decimal(fallbackMap.get("spread"), 0.25d), decimal(fallbackMap.get("speed"), 0.01d));
        List<CosmeticDefinition.Phase> phases = new ArrayList<>();
        Map<String, Object> phaseMap = YamlLite.map(root.get("phases"));
        for (Map.Entry<String, Object> entry : phaseMap.entrySet()) {
            CosmeticDefinition.Trigger trigger = CosmeticDefinition.Trigger.parse(entry.getKey());
            Map<String, Object> p = YamlLite.map(entry.getValue());
            int interval = Math.max(1, integer(p.get("interval-ticks"), 20));
            int repetitions = integer(p.get("repetitions"), -1);
            if (repetitions < 1) {
                int duration = integer(p.get("duration-ticks"), 0);
                repetitions = duration > 0 ? Math.max(1, Math.min(32, (duration + interval - 1) / interval)) : 1;
            }
            CosmeticDefinition.Anchor anchor;
            try { anchor = CosmeticDefinition.Anchor.valueOf(string(p, "anchor", "CASTER").toUpperCase(Locale.ROOT).replace('-', '_')); }
            catch (IllegalArgumentException ignored) { anchor = CosmeticDefinition.Anchor.CASTER; }
            phases.add(new CosmeticDefinition.Phase(trigger, anchor, integer(p.get("delay-ticks"), 0), Math.min(32, repetitions), interval,
                    decimal(p.get("offset-x"), 0d), decimal(p.get("offset-y"), 1d), decimal(p.get("offset-z"), 0d),
                    Math.min(64d, decimal(p.get("broadcast-radius"), 32d)), Math.min(128, integer(p.get("max-viewers"), 48))));
        }
        return new CosmeticDefinition(id, skill, name, particle, phases, fallback, hide, permission);
    }

    private Map<String, String> loadParticleAliases() throws java.io.IOException {
        Path file = CosmeticDefaults.VFX.resolve("particles.yml");
        if (!Files.isRegularFile(file)) return Map.of();
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String, Object> aliases = YamlLite.map(root.get("particles"));
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : aliases.entrySet()) {
            Map<String, Object> section = YamlLite.map(entry.getValue());
            String particle = string(section, "particle", "");
            if (!particle.isBlank()) result.put(entry.getKey(), particle);
        }
        return Map.copyOf(result);
    }

    private static boolean yaml(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".yml") || n.endsWith(".yaml");
    }
    private static String string(Map<String, Object> map, String key, String fallback) { Object value = map.get(key); return value == null ? fallback : String.valueOf(value); }
    private static int integer(Object value, int fallback) { try { return value instanceof Number n ? n.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; } }
    private static double decimal(Object value, double fallback) { try { return value instanceof Number n ? n.doubleValue() : value == null ? fallback : Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return fallback; } }
    private static boolean bool(Object value, boolean fallback) { return value == null ? fallback : value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value)); }

    private record PlayerState(Set<String> owned, Map<String, String> equipped) { }
    private static final class SavedState {
        List<String> owned; Map<String, String> equipped;
        SavedState() {}
        SavedState(List<String> owned, Map<String, String> equipped) { this.owned = owned; this.equipped = equipped; }
    }
    public record Result(boolean success, String message, CosmeticDefinition definition) {
        static Result ok(CosmeticDefinition definition) { return new Result(true, null, definition); }
        static Result fail(String message) { return new Result(false, message, null); }
    }
}
