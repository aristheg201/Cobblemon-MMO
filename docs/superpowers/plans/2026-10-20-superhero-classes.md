# Six superhero classes implementation plan

> **For agentic workers:** Execute inline with superpowers:executing-plans. No subagents are authorized for this task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Six balanced, playable superhero classes in the existing Fabric SVFrame ecosystem, with equipment stat sets and adapted Fisk presentation.

**Architecture:** SVFrameMMO owns classes, resources, costs, cooldowns and persistence; SVFrameLib owns combat/stat rules. A bounded superhero runtime coordinates temporal actions and controls, while the client handles converted equipment and skill effects.

**Tech Stack:** Minecraft 1.21.1, Fabric, Java 21, existing Gradle 8.8 local build and native QA clients.

**Spec:** `docs/superpowers/specs/2026-10-20-superhero-classes-design.md` plus the user's detailed six-class brief.

## Global constraints

- Local builds only; no GitHub Actions, public upload or bundling FiskHeroes JAR/classes.
- Seven active skills per class; no Henshin requirement; equipment is ordinary stat-bearing gear.
- Reuse native SVFrame owners; main-thread gameplay and debounced persistence.
- Preserve the stable 0.1.14 Rider/HUD baseline; unaccepted Rider 0.1.15 work stays on its branch.

## Review focus

- A 12-projectile or six-slash cast must share one PvP budget, including delayed recasts.
- Same-tick control from different attackers must share DR without permanently trapping a player.
- Unequipping/changing class during flight, phasing or a delayed ultimate must restore safe state.
- An ally/claimed area/solid wall must not become targetable through movement or a portal.
- Missing Thor/Brand New Day resources must be reported accurately without substituting an invented Fisk suit.

### Task 1: Catalog and balance rules

**Files:** Create `FantasyHub/core/src/main/java/vn/svframe/fantasyhub/core/hero/HeroCatalog.java`, `HeroBalance.java`, `HeroCastBudget.java`; tests in matching `core/src/test/java/.../hero/`; configuration `core/src/main/resources/default/heroes.json`.

**Interfaces:** Catalog resolves `HeroDefinition` and `AbilityDefinition` by immutable ID; balance consumes attack/scaling/target profile; cast budget records actual applied damage per target UUID.

- [x] Tests pin 6 classes, 42 unique skills, exact base cooldown/cost/scaling values and total multi-hit coefficients.
- [x] Tests reject nonfinite/negative configuration, duplicate skills and invalid recast/phase data.
- [x] Tests exercise per-target shared caps, ultimate coefficient normalization and hard-control DR across attackers. Reflection recursion is tested with the combat integration in Task 2.
- [x] Implement validated immutable catalog, configurable PvE/PvP profiles and resource policies.
- [x] Generate a reviewable balance report comparing burst, sustained rotation and energy pressure for all six classes.
- [x] Run targeted local tests; commit catalog and rules.

### Task 2: Shared SVFrame integration and executable skill families

**Files:** Create `HeroRuntime.java`, `HeroCombat.java`, `HeroActions.java`, class-specific `SpiderSkills.java`, `SorcererSkills.java`, `IronManSkills.java`, `ThorSkills.java`, `FlashSkills.java`, `BatmanSkills.java`; integrate initialization in `FantasyHubCore.java` and shared skill/control hooks with the smallest compatible extension.

**Interfaces:** `HeroRuntime` resolves active class sessions and validates action generations; `HeroCombat` registers attacks and statuses through native SVFrame; class modules execute immutable ability definitions using bounded action primitives.

- [ ] Test invalid target/cancelled cast spends no resource; accepted casts spend once and share the canonical cooldown/recast window.
- [ ] Implement/install six classes and seven bindings each using the existing class registry and handler manager.
- [ ] Implement all specified projectile, dash, area, zone, counter, clone, channel, portal and finisher phases with per-class passives.
- [ ] Test all 42 handlers produce actual intended combat outcomes and honor the shared balance ledger.
- [ ] Integrate resource regeneration/upkeep and input intent for flight/wall crawl/glide/phasing.
- [ ] Test death/logout/dimension/class/equipment changes cleanly end temporary actions, remove modifiers and preserve existing saved preferences.
- [ ] Run local core/integration tests; commit gameplay.

### Task 3: Stat equipment and native client presentation

**Files:** Add verified converted resources under `FantasyHub/client/src/imported/resources/assets/fantasyhub/heroes/`, asset conversion tools/inventory under `FantasyHub/tools/` and `assets-source/heroes/`; add native equipment feature, effects and protocol payloads; extend existing HUD/localization/icon paths.

**Interfaces:** Server transmits compact class/action presentation; equipment rendering reads actually equipped slot IDs, independent of casting mode. Converted resources contain no Forge classes/JAR.

- [ ] Verify actual source suit IDs/textures and inventory missing heropack resources.
- [ ] Convert only necessary Fisk data assets with source hashes and add native animated armor/attachment rendering.
- [ ] Install six ordinary equipment stat sets through SVFrameItems, with removal/enchantment policy tests.
- [ ] Add 42 distinct skill icons, Vietnamese/English labels/tooltips and class-specific energy indicators.
- [ ] Exercise default/low quality effect limits and first-/third-person anchors.
- [ ] Build both modules locally with asset/index/handshake checks.

### Task 4: Real runtime acceptance and internal delivery

**Files:** Native QA scripts and raw evidence in `docs/qa/superheroes/`; installation/build report and exact JAR manifest.

- [ ] Launch dedicated server and two actual clients with matching built bytes.
- [ ] Exercise all six classes, all 42 skills, all passives and actual suit gear behavior.
- [ ] Test PvP cast budgets/control DR, friendly fire, flight/phase collision, portals, simultaneous actors, reconnect and cleanup.
- [ ] Inspect raw model/skill/HUD captures; record precise asset blockers rather than fabricated acceptance.
- [ ] Verify no nested Fisk mod JAR/classes, no public publishing, clean branch and reproducible local artifacts.
- [ ] Deliver the internal Bridge/client JARs, configuration, source inventory, tests, evidence and installation notes.
