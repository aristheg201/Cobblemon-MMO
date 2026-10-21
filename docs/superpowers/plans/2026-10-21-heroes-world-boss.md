# Hero Progression and World Boss Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship all eleven requested gameplay changes, with native multiplayer evidence and a full client/server/config bundle.
**Architecture:** MMO owns persistent class/skill progress. FantasyHub owns hero combat and presentation; Cobblemon integration owns Pokémon EXP and overworld hostility. A separate WorldBoss module owns encounter schedules, actors, contribution and durable settlement.
**Tech Stack:** Java 21, Fabric 1.21.1, Yarn, SVFrame native APIs, Cobblemon 1.8.2, JUnit 5.
**Spec:** `docs/superpowers/specs/2026-10-21-heroes-world-boss-design.md` (approved; later user amendment: black armored Batman, neither Beyond nor Classic).

## Global Constraints

- Stormbreaker maximum throw distance 20 blocks; hit nearby entities along both paths.
- Every cast path requires learned skill and matching class/level.
- Exactly one free starter class per player; boss materials unlock other classes.
- Native game screenshots/video; full client + server + config delivery; no remote publishing.
- Keep custom geometry out of shared geo/, preserve item/flight/cape/animation fixes.
- Batman black modern armored suit, not Beyond, not Classic.

## Review Focus

- Crash/reconnect during class purchase or raid settlement must not duplicate entitlements/rewards.
- Slot changes and interruption of thrown axe/grapple must not duplicate or lose items.
- Formal Pokémon battle/capture and third-party aggression AI must not receive duplicate overworld attacks/EXP.
- Non-op players must find Flash and use starter/learning flows without hidden permissions.
- Low-quality effects, late observer arrival and dimension changes must not leave stale rendered weapons/tethers/bosses.

### Task 1: Class ownership, starter selection and skill learning
**Files:** create `FantasyHub/core/.../ClassProgression.java`, `ProgressionPolicy.java`; modify `ClassSwitchService`, `FantasyHubCore`, `HeroClassFiles`, `HeroRuntime`, client class/skill screens; add `ProgressionPolicyTest` and catalog generation tests.
**Interfaces:** `ClassProgression.owns(PlayerData,String)`, `starter(PlayerData)`, `purchase(ServerPlayerEntity,String)`, `learn(ServerPlayerEntity,String)`; persistent native unlock/claim keys, no client-granted values.
- [ ] Write/run RED tests for starter-once, 60-material price, learning gates/milestones and 43 skills.
- [ ] Implement transactional authoritative ownership/learning, starter fee bypass, generated locked skills and eight selectable Thor skills; migration keeps explicit learns.
- [ ] Run focused JUnit tests and native ordinary-player denial/learning/restart checks; commit.

### Task 2: Stormbreaker projectile lease
**Files:** create `HeroAxeFlight`, `AxeFlightMath`; modify ThorSkills/router/catalog/presentation and client effects/held-item rendering; test `AxeFlightMathTest`.
**Interfaces:** `HeroAxeFlight.cast(HeroRuntime.Cast)`, `tick()`, `release(UUID)`; server cue `AXE` carries actual segment each tick, distinct outbound/return hit sets.
- [ ] RED tests for swept-segment distance, 20-block clamp, once-per-direction hits and cancellation.
- [ ] Implement physical held-item check, throw/hover/manual recall, wall collision, all legal path targets, visual lease and cleanup.
- [ ] GREEN unit tests; native >=10 path targets, early recall/wall/slot/death/disconnect; commit.

### Task 3: Physical Batman grapple and suit
**Files:** create protocol `GrapplingHookItem`, core `HeroGrapple`, `TetherPhysics`, client `HeroGrappleVisuals`; modify protocol registration/gear files/BatmanSkills; author black armored textures/icons; test `TetherPhysicsTest`.
**Interfaces:** use item routes through learned BATCLAW native cast; `HeroGrapple.tick()`/`release(UUID)`; cue `TETHER` from actor to anchor.
- [ ] RED tests for rope tension, tangent momentum, reel length and release behavior.
- [ ] Implement terrain anchor/swing/reel/release, entity pull/PvP guards, item assets and armor/cape consistency.
- [ ] GREEN tests and native block swing/player pull/immunity/jump/glide evidence; commit.

### Task 4: Flash discovery and stronger visual identities
**Files:** modify `ClassSelectionScreen`, `HeroEffects`, `HeroVisuals`, `HeroAnimations`, `HeroSkillIcons`; test presentation policy and roster search.
**Interfaces:** existing server roster remains authoritative; all cues keep entity/cast/phase identities and bounded lifetime.
- [ ] Reproduce search/paging and visual gaps; add policy tests for new cue retention/cleanup.
- [ ] Add class search, explicit locked reasons, Flash moving afterimages/lightning, Batman smoke/bats/scan/batarang/Batwing, richer staged hero impacts.
- [ ] Verify native four-hero skill sequences + Flash; preserve Detailed/BetterCombat; commit.

### Task 5: Pokémon EXP and real hostility
**Files:** create integration `PokemonProgression`, `PokemonHostility`, `PokemonRewardPolicy`; modify integration initializer/config; test reward attribution/de-duplication/eligibility.
**Interfaces:** native `PlayerData.giveExperience` path; one victory token per player/event; adapter excludes owned/busy/battle/capture Pokémon.
- [ ] RED tests for victory dedup/Exp Share/owned exclusion and hostile target eligibility.
- [ ] Implement battle + overworld EXP, real pursuit and telegraphed melee/ranged damage, single AI owner with Fight or Flight.
- [ ] GREEN tests; native wild Pokémon reduces player HP and class EXP/level increases once; commit.

### Task 6: World boss schedule, persistence and rewards
**Files:** add `FantasyHub/worldboss` module, `WorldBossMod`, `BossConfig`, `BossSchedule`, `Encounter`, `BossLedger`, `BossRewards`, tests for schedule/settlement.
**Interfaces:** `Encounter` state transitions SETUP/FIGHT/WON/LOST/ABORTED; encounter UUID/player UUID settlement key; inventory-full/offline pending rewards.
- [ ] RED tests for Asia/Ho_Chi_Minh 12/18/21 schedule, no replay after restart, 12/3 rewards, one settlement and 60-sigil redemption.
- [ ] Implement separate JAR/config/commands, arena validation, setup/timeout/bossbar/contribution/settlement and mailbox.
- [ ] GREEN tests; native win/loss/restart/full inventory/two-player contribution; commit.

### Task 7: Pokémon and class NPC boss adapters
**Files:** add worldboss `PokemonBossActor`, `ClassBossActor`, client player-shaped boss renderer; shared combat actor context/cues in core as needed.
**Interfaces:** `BossActor` supplies real LivingEntity, health, tick targets/skills and cleanup; not armor stand or renamed zombie.
- [ ] RED tests for phase rotation/target legality/interruption; verify native spawn and damage attribution.
- [ ] Implement Pokémon actor/moves and player-shaped class NPC with suit/equipment/real class abilities/telegraphs, leash and phase rotations.
- [ ] Native both actor types damage/receive damage and settle correctly; commit.

### Task 8: Full QA and release
**Files:** extend local build/package scripts, native QA commands/runners; add QA evidence and continuation docs.
- [ ] Run all module builds/tests; inspect geometry guard and exact JAR metadata/dependency contents.
- [ ] Native QA every Thor/Strange/Spider/Batman skill with clips and phase screenshots, ordinary-player progression and both bosses; record failures honestly and fix before pass claims.
- [ ] Fresh whole-branch review, fix important findings with regression tests.
- [ ] Package version 0.2.4 client/server/config/source/evidence/checksums without overwriting 0.2.3; verify ZIP contents; commit handoff.
