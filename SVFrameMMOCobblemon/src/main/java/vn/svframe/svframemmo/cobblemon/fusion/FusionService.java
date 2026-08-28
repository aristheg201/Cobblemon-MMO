package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.integration.MegaShowdownEffects;
import vn.svframe.svframemmo.cobblemon.move.BattleStat;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;
import vn.svframe.svframemmo.cobblemon.move.MoveSemantic;
import vn.svframe.svframemmo.cobblemon.move.MoveSemanticRegistry;
import vn.svframe.svframemmo.cobblemon.move.RealtimeBattleState;
import vn.svframe.svframemmo.skill.ClassSkill;
import vn.svframe.svframemmo.skill.runtime.TemporarySkillOverlayRuntime;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Event/state-driven fusion runtime. Only active fusion sessions are ticked. */
public final class FusionService {
    public static final long DEFAULT_DANCE_DURATION_TICKS = 10L * 60L * 20L;
    private static final long STAGE_DURATION_TICKS = 30L * 20L;
    private static final long PROTECT_DURATION_TICKS = 20L;
    private static final String OVERLAY_OWNER = "svframemmo_cobblemon:fusion";

    private final DeployedPartyPokemonResolver resolver = new DeployedPartyPokemonResolver();
    private final FusionEligibility eligibility = new FusionEligibility();
    private final MoveSemanticRegistry semantics = new MoveSemanticRegistry();
    private final RealtimeBattleState realtime = new RealtimeBattleState();
    private final CobblemonMoveSkillAdapter moves = new CobblemonMoveSkillAdapter(this, semantics);
    private final FusionCooldowns cooldowns = new FusionCooldowns();
    private final FusionVisualBridge visuals = new FusionVisualBridge();
    private final FusionStatBridge stats = new FusionStatBridge();
    private final Map<UUID, FusionSession> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lockedPokemon = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, ClassSkill>> pendingPotaraSkills = new ConcurrentHashMap<>();

    public FusionSession session(UUID player) { return player == null ? null : byPlayer.get(player); }
    public boolean isPokemonLocked(UUID pokemon) { return pokemon != null && lockedPokemon.containsKey(pokemon); }
    public int activeCount() { return byPlayer.size(); }
    public FusionCooldowns cooldowns() { return cooldowns; }
    public int potaraCooldownSeconds() { return SVFrameMMOCobblemon.config().fusion.potaraActionCooldownSeconds; }

    public StartResult startPotara(ServerPlayerEntity player, PokemonEntity entity, FusionTier tier) {
        if (tier == null || tier == FusionTier.DANCE) return StartResult.rejected("Invalid Potara tier.");
        DeployedPartyPokemonResolver.Resolution resolved = resolver.resolve(player, entity);
        if (!resolved.accepted()) return StartResult.rejected(resolved.rejection());
        return startResolved(player, resolved.pokemon(), entity, tier, -1L, false);
    }

    /** Fusion Dance morphs directly from the selected party Pokemon; it never deploys a proxy Pokemon entity. */
    public StartResult startDance(ServerPlayerEntity player, Pokemon selected) {
        if (!cooldowns.danceReady(player.getUuid())) return StartResult.rejected("Fusion Dance is still on cooldown.");
        if (selected == null) return StartResult.rejected("No Pokemon was selected.");
        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(selected.getUuid());
        if (pokemon == null || !pokemon.belongsTo(player)) return StartResult.rejected("That Pokemon is not in your current party.");
        PokemonEntity deployed = pokemon.getEntity();
        if (deployed != null && deployed.isBattling()) return StartResult.rejected("That Pokemon is currently battling.");
        long expiresAt = SVFrameMMO.currentTick() + SVFrameMMOCobblemon.config().fusion.danceDurationSeconds * 20L;
        return startResolved(player, pokemon, null, FusionTier.DANCE, expiresAt, false);
    }

    private StartResult startResolved(ServerPlayerEntity player, Pokemon pokemon, PokemonEntity entity, FusionTier tier,
                                      long expiresAt, boolean autoDeployed) {
        UUID playerId = player.getUuid();
        if (byPlayer.containsKey(playerId)) return StartResult.rejected("You are already fused.");
        if (!eligibility.allows(tier, pokemon)) return StartResult.rejected("This Pokemon is not eligible for that fusion rank.");
        if (entity != null && entity.isBattling()) return StartResult.rejected("That Pokemon is currently battling.");
        if (tier != FusionTier.DANCE && entity == null) return StartResult.rejected("Potara requires the selected Pokemon to be deployed.");
        if (lockedPokemon.putIfAbsent(pokemon.getUuid(), playerId) != null)
            return StartResult.rejected("That Pokemon is already locked by a fusion.");

        boolean originalTradeable = pokemon.getTradeable();
        TemporarySkillOverlayRuntime.Handle overlay = null;
        try {
            CobblemonMoveSkillAdapter.Overlay snapshot = moves.snapshot(pokemon);
            pokemon.setTradeable(false);

            if (tier == FusionTier.DANCE) {
                overlay = SVFrameMMO.temporarySkills().push(playerId, OVERLAY_OWNER, snapshot.skills());
                FusionSession session = new FusionSession(playerId, pokemon.getUuid(), null,
                        pokemon.getSpecies().getResourceIdentifier().toString(), pokemon.getDisplayName(false).getString(), tier,
                        SVFrameMMO.currentTick(), expiresAt, false, originalTradeable, false,
                        snapshot.moveIds(), overlay);
                stats.apply(player, pokemon, tier);
                if (byPlayer.putIfAbsent(playerId, session) != null) throw new IllegalStateException("Fusion session already exists");
                // Fusion Dance intentionally has no Potara animation and no deployed Pokemon proxy.
                visuals.start(player, pokemon, null, false);
                return new StartResult(session, null);
            }

            FusionSession pending = new FusionSession(playerId, pokemon.getUuid(), entity.getUuid(),
                    pokemon.getSpecies().getResourceIdentifier().toString(), pokemon.getDisplayName(false).getString(), tier,
                    SVFrameMMO.currentTick(), expiresAt, true, originalTradeable, autoDeployed,
                    snapshot.moveIds(), null);
            pendingPotaraSkills.put(playerId, snapshot.skills());
            if (byPlayer.putIfAbsent(playerId, pending) != null) throw new IllegalStateException("Fusion session already exists");

            // Potara uses Mega Showdown's Kyurem Black/White fusion sequence. The RPG stat bonus, forced four-move
            // overlay and visible fused form are activated together only after the 4.4s sequence completes.
            MegaShowdownEffects.playPotaraFusionStart(pokemon, entity);
            schedulePotaraActivation(player, pending);
            return new StartResult(pending, null);
        } catch (RuntimeException error) {
            if (overlay != null) overlay.close();
            pendingPotaraSkills.remove(playerId);
            stats.remove(player);
            byPlayer.remove(playerId);
            visuals.stop(player, playerId);
            pokemon.setTradeable(originalTradeable);
            lockedPokemon.remove(pokemon.getUuid(), playerId);
            SVFrameMMOCobblemon.LOG.warn("Could not start {} fusion for {}", tier == FusionTier.DANCE ? "Dance" : "Potara", player.getName().getString(), error);
            return StartResult.rejected("Could not start fusion: " + safeMessage(error));
        }
    }

    private void schedulePotaraActivation(ServerPlayerEntity player, FusionSession expected) {
        MinecraftServer server = player.getServerWorld().getServer();
        long at = SVFrameMMO.currentTick() + MegaShowdownEffects.POTARA_FUSION_FORM_DELAY_TICKS;
        SVFrameMMO.delayedActions().schedule(at, () -> {
            FusionSession current = byPlayer.get(expected.playerUuid());
            if (current != expected) return;
            ServerPlayerEntity livePlayer = server.getPlayerManager().getPlayer(expected.playerUuid());
            if (livePlayer == null) return;
            Pokemon livePokemon = Cobblemon.INSTANCE.getStorage().getParty(livePlayer).get(expected.pokemonUuid());
            PokemonEntity liveEntity = livePokemon == null ? null : livePokemon.getEntity();
            if (livePokemon == null || liveEntity == null || liveEntity.isRemoved()
                    || !liveEntity.getUuid().equals(expected.deployedEntityUuid()) || liveEntity.isBattling()) {
                finish(livePlayer, expected);
                return;
            }

            Map<Integer, ClassSkill> skills = pendingPotaraSkills.remove(expected.playerUuid());
            if (skills == null || skills.isEmpty()) {
                SVFrameMMOCobblemon.LOG.warn("Potara pending skill snapshot was missing for {}", expected.playerUuid());
                finish(livePlayer, expected);
                return;
            }

            TemporarySkillOverlayRuntime.Handle overlay = null;
            try {
                overlay = SVFrameMMO.temporarySkills().push(expected.playerUuid(), OVERLAY_OWNER, skills);
                FusionSession activated = expected.withOverlay(overlay);
                if (!byPlayer.replace(expected.playerUuid(), expected, activated)) {
                    overlay.close();
                    return;
                }
                stats.apply(livePlayer, livePokemon, activated.tier());
                visuals.start(livePlayer, livePokemon, liveEntity, activated.autoDeployed());
            } catch (RuntimeException error) {
                SVFrameMMOCobblemon.LOG.warn("Could not activate Potara fused form after Mega Showdown sequence", error);
                FusionSession active = byPlayer.get(expected.playerUuid());
                if (active != null) finish(livePlayer, active);
                else if (overlay != null) overlay.close();
            }
        });
    }

    public EndResult end(ServerPlayerEntity player, boolean manual) {
        FusionSession session = byPlayer.get(player.getUuid());
        if (session == null) return EndResult.rejected("You are not fused.");
        if (manual && !session.manualUnfuseAllowed()) return EndResult.rejected("Fusion Dance cannot be manually unfused.");
        return finish(player, session);
    }

    private EndResult finish(ServerPlayerEntity player, FusionSession session) {
        if (!byPlayer.remove(session.playerUuid(), session)) return EndResult.rejected("Fusion session already ended.");
        pendingPotaraSkills.remove(session.playerUuid());
        lockedPokemon.remove(session.pokemonUuid(), session.playerUuid());
        if (session.overlay() != null) session.overlay().close();
        realtime.clear(session.playerUuid());
        if (player != null) stats.remove(player);
        Pokemon pokemon = player == null ? null : Cobblemon.INSTANCE.getStorage().getParty(player).get(session.pokemonUuid());
        if (pokemon != null) pokemon.setTradeable(session.originalTradeable());
        visuals.stop(player, session.playerUuid());
        if (session.dance()) cooldowns.markDance(session.playerUuid(), SVFrameMMOCobblemon.config().fusion.danceCooldownSeconds);
        return new EndResult(session, null);
    }

    public void onDisconnect(ServerPlayerEntity player) {
        FusionSession session = byPlayer.get(player.getUuid());
        if (session != null) finish(player, session);
    }

    public void tick(long tick, MinecraftServer server) {
        realtime.tick(tick);
        if (byPlayer.isEmpty()) return;
        for (UUID invalid : visuals.tick(server)) {
            FusionSession session = byPlayer.get(invalid);
            if (session == null) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(invalid);
            if (player != null) finish(player, session);
        }
        for (FusionSession session : List.copyOf(byPlayer.values())) {
            if (!session.expired(tick)) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.playerUuid());
            if (player != null) finish(player, session);
        }
    }

    public MoveCast prepareMoveCast(ServerPlayerEntity player, String moveId) {
        FusionSession session = byPlayer.get(player.getUuid());
        if (session == null || !session.activated() || !session.moveIds().contains(moveId)) return null;
        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(session.pokemonUuid());
        if (pokemon == null) return null;
        if (pokemon.getEntity() != null && pokemon.getEntity().isBattling()) return null;
        for (Move move : pokemon.getMoveSet().getMoves()) {
            if (move != null && CobblemonMoveSkillAdapter.id(move.getName()).equals(moveId)) {
                if (move.getCurrentPp() <= 0) {
                    player.sendMessage(Text.literal(move.getDisplayName().getString() + " has no PP left."), true);
                    return null;
                }
                return new MoveCast(session, pokemon, move);
            }
        }
        return null;
    }

    public boolean consumePp(MoveCast cast) {
        Move move = cast.move();
        if (move.getCurrentPp() <= 0) return false;
        move.setCurrentPp(move.getCurrentPp() - 1);
        move.update();
        return true;
    }

    public void executeMove(MoveCast cast, LivingEntity target, MoveSemantic semantic, SkillMetadata metadata) {
        MoveTemplate move = cast.move().getTemplate();
        ServerPlayerEntity player = metadata.getCaster().getData().getPlayer();
        long tick = SVFrameMMO.currentTick();

        if (semantic.protect()) realtime.protect(player.getUuid(), tick, PROTECT_DURATION_TICKS);
        for (MoveSemantic.StageChange change : semantic.stages()) {
            UUID subject = change.target() == MoveSemantic.Target.SELF ? player.getUuid() : target == null ? null : target.getUuid();
            if (subject != null) realtime.add(subject, change.stat(), change.stages(), tick, STAGE_DURATION_TICKS);
            if (change.stat() == BattleStat.SPEED) applySpeedStage(change.target() == MoveSemantic.Target.SELF ? player : target, change.stages());
        }

        boolean hit = target == null || rollAccuracy(player, target, move, tick);
        double dealt = 0d;
        if (hit && target != null && move.getPower() > 0d) {
            int hits = semantic.multiHitMin() == semantic.multiHitMax() ? semantic.multiHitMin()
                    : ThreadLocalRandom.current().nextInt(semantic.multiHitMin(), semantic.multiHitMax() + 1);
            double perHit = damage(cast, target, move, tick);
            for (int i = 0; i < hits; i++) {
                metadata.attack(target, perHit, DamageType.SKILL,
                        move.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL() ? DamageType.PHYSICAL : DamageType.MAGIC);
                dealt += perHit;
            }
        }

        if (hit && semantic.status() != MoveSemantic.Status.NONE && target != null
                && ThreadLocalRandom.current().nextDouble() < semantic.statusChance()) applyStatus(target, semantic.status());
        if (semantic.healFraction() > 0d) player.heal((float) (player.getMaxHealth() * semantic.healFraction()));
        if (dealt > 0d && semantic.drainFraction() > 0d) player.heal((float) (dealt * semantic.drainFraction()));
        if (dealt > 0d && semantic.recoilFraction() > 0d)
            player.setHealth(Math.max(0f, player.getHealth() - (float) (dealt * semantic.recoilFraction())));
    }

    public boolean blocksDamage(LivingEntity entity) {
        return entity != null && realtime.protectedNow(entity.getUuid(), SVFrameMMO.currentTick());
    }

    public void grantProtection(LivingEntity entity, long ticks) {
        if (entity != null && ticks > 0L) realtime.protect(entity.getUuid(), SVFrameMMO.currentTick(), ticks);
    }

    public boolean isVisualEntityOf(UUID player, UUID entity) {
        FusionSession session = player == null ? null : byPlayer.get(player);
        return session != null && session.activated() && entity != null && entity.equals(session.deployedEntityUuid());
    }

    private boolean rollAccuracy(ServerPlayerEntity caster, LivingEntity target, MoveTemplate move, long tick) {
        double accuracy = move.getAccuracy();
        if (accuracy <= 0d) return true;
        int accStage = realtime.stage(caster.getUuid(), BattleStat.ACCURACY, tick);
        int evaStage = realtime.stage(target.getUuid(), BattleStat.EVASION, tick);
        double chance = Math.min(1d, Math.max(0d, accuracy / 100d * accuracyMultiplier(accStage - evaStage)));
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private double damage(MoveCast cast, LivingEntity target, MoveTemplate move, long tick) {
        boolean physical = move.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL();
        BattleStat attackStageStat = physical ? BattleStat.ATTACK : BattleStat.SPECIAL_ATTACK;
        BattleStat defenseStageStat = physical ? BattleStat.DEFENSE : BattleStat.SPECIAL_DEFENSE;
        double attack = cast.pokemon().getStat(physical ? Stats.ATTACK : Stats.SPECIAL_ATTACK)
                * stageMultiplier(realtime.stage(cast.session().playerUuid(), attackStageStat, tick));
        double defense = targetDefense(target, physical)
                * stageMultiplier(realtime.stage(target.getUuid(), defenseStageStat, tick));
        double level = cast.pokemon().getLevel();
        double base = (((2d * level / 5d + 2d) * move.getPower() * Math.max(1d, attack) / Math.max(1d, defense)) / 50d) + 2d;
        return Math.max(1d, base * cast.session().bonusMultiplier());
    }

    private double targetDefense(LivingEntity target, boolean physical) {
        if (target instanceof PokemonEntity pokemonEntity) {
            return Math.max(1d, pokemonEntity.getPokemon().getStat(physical ? Stats.DEFENCE : Stats.SPECIAL_DEFENCE));
        }
        FusionSession targetFusion = byPlayer.get(target.getUuid());
        if (targetFusion != null && targetFusion.activated() && target instanceof ServerPlayerEntity player) {
            Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(targetFusion.pokemonUuid());
            if (pokemon != null) return Math.max(1d, pokemon.getStat(physical ? Stats.DEFENCE : Stats.SPECIAL_DEFENCE));
        }
        return physical ? Math.max(20d, 100d + target.getArmor() * 5d) : 100d;
    }

    private static double stageMultiplier(int stage) {
        return stage >= 0 ? (2d + stage) / 2d : 2d / (2d - stage);
    }

    private static double accuracyMultiplier(int stage) {
        int clamped = Math.max(-6, Math.min(6, stage));
        return clamped >= 0 ? (3d + clamped) / 3d : 3d / (3d - clamped);
    }

    private static void applySpeedStage(LivingEntity entity, int stages) {
        if (entity == null || stages == 0) return;
        int amplifier = Math.min(5, Math.max(0, Math.abs(stages) - 1));
        entity.addStatusEffect(new StatusEffectInstance(stages > 0 ? StatusEffects.SPEED : StatusEffects.SLOWNESS,
                (int) STAGE_DURATION_TICKS, amplifier, false, false));
    }

    private static void applyStatus(LivingEntity target, MoveSemantic.Status status) {
        switch (status) {
            case PARALYSIS -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
            case BURN -> target.setOnFireFor(4.0f);
            case POISON -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 120, 0));
            case BAD_POISON -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 1));
            case SLEEP -> {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 5));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
            }
            case FREEZE -> target.setFrozenTicks(Math.max(target.getFrozenTicks(), 100));
            case CONFUSION -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
            case FLINCH -> {
                target.setVelocity(Vec3d.ZERO);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 5));
            }
            case NONE -> { }
        }
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    public record MoveCast(FusionSession session, Pokemon pokemon, Move move) { }
    public record StartResult(FusionSession session, String rejection) {
        public static StartResult rejected(String reason) { return new StartResult(null, reason); }
        public boolean success() { return session != null; }
    }
    public record EndResult(FusionSession session, String rejection) {
        public static EndResult rejected(String reason) { return new EndResult(null, reason); }
        public boolean success() { return session != null; }
    }
}
