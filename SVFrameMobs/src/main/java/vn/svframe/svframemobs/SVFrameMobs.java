package vn.svframe.svframemobs;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframelib.fabric.NativeParticles;
import java.nio.file.*;
import java.util.*;

public final class SVFrameMobs implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("SVFrameMobs");
    private static final String TAG = "svframemobs:";
    private final Map<UUID, MobEntity> active = new LinkedHashMap<>();
    private Map<String, MobDefinition> definitions = Map.of();
    private Path folder;
    private int triggerDepth;

    @Override public void onInitialize() {
        folder = FabricLoader.getInstance().getConfigDir().resolve("SVFrameMobs/mobs");
        try {
            Files.createDirectories(folder);
            Path example = folder.resolve("examples.yml");
            if (!Files.exists(example)) try (var input = getClass().getResourceAsStream("/examples.yml")) { Files.copy(Objects.requireNonNull(input), example); }
        } catch (Exception error) { throw new IllegalStateException("Cannot load SVFrameMobs configuration", error); }
        ServerLifecycleEvents.SERVER_STARTING.register(server->{try{reload();LOG.info("Loaded {} native mob definitions",definitions.size());}catch(Exception failure){throw new IllegalStateException("Cannot load SVFrameMobs configuration",failure);}});
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> { if (entity instanceof MobEntity mob && id(mob) != null) active.put(mob.getUuid(), mob); });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> active.remove(entity.getUuid(), entity));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> active.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (MobEntity mob : List.copyOf(active.values())) {
                if (!mob.isAlive() || mob.isRemoved()) { active.remove(mob.getUuid(), mob); continue; }
                fire(mob, "ontimer", mob.getTarget());
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
            if (blocked || taken <= 0) return;
            if (victim instanceof MobEntity mob) fire(mob, "ondamaged", source.getAttacker());
            if (source.getAttacker() instanceof MobEntity mob) fire(mob, "onattack", victim);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (!(victim instanceof MobEntity mob)) return;
            MobDefinition definition = definition(mob); if (definition == null) return;
            fire(mob, "ondeath", source.getAttacker());
            for (MobDefinition.Drop drop : definition.drops()) if (mob.getRandom().nextDouble() < drop.chance()) mob.dropStack(stack(drop.item(), drop.amount()));
            active.remove(mob.getUuid(), mob);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(CommandManager.literal("svframemobs").requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("reload").executes(context -> {
                    try { reload(); context.getSource().sendFeedback(() -> Text.literal("Loaded " + definitions.size() + " mob definitions"), true); return definitions.size(); }
                    catch (Exception error) { context.getSource().sendError(Text.literal("Reload rejected; previous definitions retained: " + error.getMessage())); LOG.error("Mob reload rejected", error); return 0; }
                }))
                .then(CommandManager.literal("list").executes(context -> { context.getSource().sendFeedback(() -> Text.literal("Mobs: " + String.join(", ", definitions.keySet()) + "; loaded entities=" + active.size()), false); return definitions.size(); }))
                .then(CommandManager.literal("spawn").then(CommandManager.argument("id", StringArgumentType.word()).suggests((context, builder) -> { definitions.keySet().forEach(builder::suggest); return builder.buildFuture(); }).executes(context -> {
                    String key = StringArgumentType.getString(context,"id");
                    try { MobEntity mob = spawn(key, context.getSource().getWorld(), context.getSource().getPosition()); context.getSource().sendFeedback(() -> Text.literal("Spawned " + key + " uuid=" + mob.getUuid() + " health=" + mob.getHealth()), true); return 1; }
                    catch (IllegalArgumentException error) { context.getSource().sendError(Text.literal(error.getMessage())); return 0; }
                })) )));
        LOG.info("Loaded {} native mob definitions", definitions.size());
    }
    private void reload() throws Exception {
        Map<String, MobDefinition> parsed = new LinkedHashMap<>();
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")).sorted().toList()) {
                for (var entry : YamlLite.map(YamlLite.parse(file)).entrySet()) {
                    MobDefinition definition = MobDefinition.parse(entry.getKey(), YamlLite.map(entry.getValue()));
                    var factory=vn.svframe.svframelib.entity.NativeMobFactories.get(factoryId(definition.type()));
                    if(factory!=null)factory.validate(definition.pokemon());
                    else {if(!definition.pokemon().isBlank())throw new IllegalArgumentException("Missing native Pokemon entity integration");EntityType<?> type = type(definition.type());
                    if (type.getSpawnGroup() == SpawnGroup.MISC) throw new IllegalArgumentException("Type is not a mob: " + definition.type());}
                    for (String item : definition.equipment().values()) stack(item,1);
                    for (var drop : definition.drops()) stack(drop.item(),drop.amount());
                    for (SkillLine skill : definition.skills()) validateSkill(skill);
                    if (parsed.putIfAbsent(definition.id(),definition) != null) throw new IllegalArgumentException("Duplicate mob " + definition.id());
                }
            }
        }
        definitions = Collections.unmodifiableMap(parsed);
    }
    private static void validateSkill(SkillLine skill) {
        if (skill.mechanic().equals("effect:particles")) NativeParticles.effect(skill.value("particle",skill.value("p","end_rod")),color(skill),1,skill.value("material","stone"));
        if (skill.mechanic().equals("skill") && !SVFrameLibFabricMod.hasSkill(skill.value("s", skill.value("skill", "")))) throw new IllegalArgumentException("Unknown SVFrameLib skill in " + skill);
        if (skill.mechanic().equals("potion") && !Registries.STATUS_EFFECT.containsId(identifier(skill.value("type", "slowness")))) throw new IllegalArgumentException("Unknown potion in " + skill);
    }
    private MobEntity spawn(String key, ServerWorld world, Vec3d position) {
        MobDefinition definition = definitions.get(key); if (definition == null) throw new IllegalArgumentException("Unknown mob: " + key);
        if (!world.isChunkLoaded(net.minecraft.util.math.BlockPos.ofFloored(position)) || !world.getWorldBorder().contains(position.x,position.z)) throw new IllegalArgumentException("Spawn must be inside a loaded chunk and world border");
        var factory=vn.svframe.svframelib.entity.NativeMobFactories.get(factoryId(definition.type()));
        Entity created = factory==null?type(definition.type()).create(world):factory.create(world,definition.pokemon());
        if (!(created instanceof MobEntity mob)) { if (created != null) created.discard(); throw new IllegalArgumentException("Type is not a living mob: " + definition.type()); }
        mob.refreshPositionAndAngles(position.x,position.y,position.z,0,0);
        if (!world.isSpaceEmpty(mob)) throw new IllegalArgumentException("Spawn space is obstructed");
        mob.addCommandTag(TAG + key); mob.setPersistent(); mob.setAiDisabled(definition.noAi()); mob.setSilent(definition.silent());
        mob.setCustomName(Text.literal(colors(definition.display()))); mob.setCustomNameVisible(true);
        attribute(mob,EntityAttributes.GENERIC_MAX_HEALTH,definition.health(),factory!=null);
        attribute(mob,EntityAttributes.GENERIC_ATTACK_DAMAGE,definition.damage(),factory!=null);
        attribute(mob,EntityAttributes.GENERIC_ARMOR,definition.armor(),factory!=null);
        attribute(mob,EntityAttributes.GENERIC_MOVEMENT_SPEED,definition.speed(),factory!=null);
        mob.setHealth((float)definition.health());
        definition.equipment().forEach((slot,item) -> { EquipmentSlot equipmentSlot = switch(slot) { case "HEAD" -> EquipmentSlot.HEAD; case "CHEST" -> EquipmentSlot.CHEST; case "LEGS" -> EquipmentSlot.LEGS; case "FEET" -> EquipmentSlot.FEET; case "OFFHAND" -> EquipmentSlot.OFFHAND; default -> EquipmentSlot.MAINHAND; }; mob.equipStack(equipmentSlot,stack(item,1)); mob.setEquipmentDropChance(equipmentSlot,0); });
        if (!world.spawnEntity(mob)) throw new IllegalArgumentException("Entity spawn rejected");
        active.put(mob.getUuid(),mob); fire(mob,"onspawn",null); return mob;
    }
    private static void attribute(MobEntity mob, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> type, double value, boolean external) {
        var instance=mob.getAttributeInstance(type);if(instance==null)return;
        if(!external){instance.setBaseValue(value);return;}
        // External entity runtimes periodically recompute their own base stats.
        // Persist a separate encounter modifier instead of overwriting that base.
        Identifier key=Identifier.of("svframemobs","configured_"+net.minecraft.registry.Registries.ATTRIBUTE.getId(type.value()).getPath().replace('.','_'));
        instance.removeModifier(key);
        instance.addPersistentModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(key,value-instance.getValue(),net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE));
    }
    private void fire(MobEntity mob, String trigger, Entity eventEntity) {
        MobDefinition definition = definition(mob); if (definition == null || triggerDepth >= 4) return;
        triggerDepth++;
        try {
            for (SkillLine skill : definition.skills()) {
                if (!skill.trigger().equals(trigger) || (trigger.equals("ontimer") && mob.age % skill.interval() != 0) || mob.getRandom().nextDouble() >= skill.chance()) continue;
                for (Entity target : targets(mob, skill, eventEntity)) {
                    try { execute(mob,target,skill); } catch (Exception error) { LOG.warn("Skill failed for {}: {}",definition.id(),skill,error); }
                }
            }
        } finally { triggerDepth--; }
    }
    private List<? extends Entity> targets(MobEntity mob, SkillLine skill, Entity eventEntity) {
        return switch (skill.target()) {
            case "self" -> List.of(mob);
            case "target" -> mob.getTarget() == null ? List.of() : List.of(mob.getTarget());
            case "trigger" -> eventEntity == null ? List.of() : List.of(eventEntity);
            case "playersinradius", "pir" -> ((ServerWorld)mob.getWorld()).getPlayers(player -> player.isAlive() && !player.isSpectator() && !player.isCreative() && !mob.isTeammate(player) && player.squaredDistanceTo(mob) <= skill.radius()*skill.radius());
            case "entitiesinradius", "eir" -> mob.getWorld().getEntitiesByClass(LivingEntity.class,mob.getBoundingBox().expand(skill.radius()),target->target!=mob&&target.squaredDistanceTo(mob)<=skill.radius()*skill.radius()&&!mob.isTeammate(target)&&vn.svframe.svframelib.entity.RpgEntityAdapters.allows(mob,target,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.DAMAGE));
            default -> List.of();
        };
    }
    private void execute(MobEntity mob, Entity target, SkillLine skill) {
        if(target instanceof LivingEntity living&&!skill.mechanic().equals("message")&&!skill.mechanic().equals("effect:particles")
                &&!vn.svframe.svframelib.entity.RpgEntityAdapters.allows(mob,living,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.STATUS))return;
        switch (skill.mechanic()) {
            case "damage" -> { if (target instanceof LivingEntity living && !living.isSpectator() && !(living instanceof ServerPlayerEntity p && p.isCreative()) && !mob.isTeammate(living)) living.damage(living.getDamageSources().mobAttack(mob),(float)Math.max(0,skill.number("amount",skill.number("a",1)))); }
            case "velocity" -> { target.setVelocity(new Vec3d(bounded(skill.number("x",0),-3,3),bounded(skill.number("y",1),-3,3),bounded(skill.number("z",0),-3,3))); target.velocityModified = true; target.velocityDirty = true; if (target instanceof ServerPlayerEntity player) player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player)); }
            case "potion" -> { if (target instanceof LivingEntity living) Registries.STATUS_EFFECT.getEntry(identifier(skill.value("type","slowness"))).ifPresent(effect -> living.addStatusEffect(new StatusEffectInstance(effect,(int)bounded(skill.number("duration",60),1,72000),(int)bounded(skill.number("level",0),0,255)),mob)); }
            case "message" -> { if (target instanceof ServerPlayerEntity player) player.sendMessage(Text.literal(colors(skill.value("m",skill.value("message","")))),false); }
            case "effect:particles" -> ((ServerWorld)mob.getWorld()).spawnParticles(NativeParticles.effect(skill.value("particle",skill.value("p","end_rod")),color(skill),(float)skill.number("size",1),skill.value("material","stone")),target.getX(),target.getY()+1,target.getZ(),(int)bounded(skill.number("amount",10),1,100),0.3,0.3,0.3,0.02);
            case "skill" -> SVFrameLibFabricMod.castSkill(skill.value("s",skill.value("skill","")),mob.getUuid(),target.getUuid(),skill.parameters());
        }
    }
    private static int color(SkillLine skill) { String raw = skill.value("color", "a83cff").replace("#", ""); return Integer.parseInt(raw,16) & 0xffffff; }
    private static double bounded(double value,double min,double max) { return Math.max(min,Math.min(max,value)); }
    private MobDefinition definition(MobEntity mob) { String id = id(mob); return id == null ? null : definitions.get(id); }
    private static String id(MobEntity mob) { for (String tag : mob.getCommandTags()) if (tag.startsWith(TAG)) return tag.substring(TAG.length()); return null; }
    private static String colors(String text) { return text.replace('&','§'); }
    private static Identifier identifier(String input) { return Identifier.of(input.contains(":") ? input.toLowerCase(Locale.ROOT) : "minecraft:"+input.toLowerCase(Locale.ROOT)); }
    private static String factoryId(String type){return type.equalsIgnoreCase("POKEMON")?"cobblemon:pokemon":type.toLowerCase(Locale.ROOT);}
    private static EntityType<?> type(String name) { Identifier id = identifier(name); if (!Registries.ENTITY_TYPE.containsId(id)) throw new IllegalArgumentException("Unknown entity type: " + name); return Registries.ENTITY_TYPE.get(id); }
    private static ItemStack stack(String name,int amount) { Identifier id = identifier(name); if (!Registries.ITEM.containsId(id) || id.equals(Identifier.ofVanilla("air"))) throw new IllegalArgumentException("Unknown item: " + name); return new ItemStack(Registries.ITEM.get(id),amount); }
}
