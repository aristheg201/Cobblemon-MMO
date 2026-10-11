# Expanded player skills and upgrade regression — 2026-10-11

Built locally for Minecraft 1.21.1, Fabric Loader >=0.18.4, Java 21 and Cobblemon 1.8.0. No GitHub Actions were used. This verifies the current changes; the full original MMORPG specification remains incomplete.

The uploaded Windows artifacts were missing source. Their own mod bytecode was recovered, reconciled with the existing source and rebuilt. The 155 curated player-move adapters cover 18 Pokémon types and 14 effect families. All 155 pass native model/provider checks against the installed Cobblemon moves.js SHA256 `7f3ffc8ace9de6dc86048006c29c9ae2dc891b3a83afa8b9e7d682b192f2fbae`. This is not live gameplay certification of all 155 moves. The current requested visual/runtime scope is Bite and Lick; the prior 15-move evidence is historical.

## Upgrade root causes and changes

1. Native skill-list slot 15 contains both a level preview and an upgrade button. Rendering used the last item, but click dispatch used the first, so clicks reached the preview. Lib now dispatches to the top visible layer, respecting loaded/configured overlays and hidden items.
2. Public MMO upgrade/bind/unbind APIs, command listings and suggestions still used class-only progression. They now use the unified PlayerSkillCatalog, preserving separate class-scoped serialization primitives so external binding does not recursively erase itself.
3. The client upgrade handler resolved a locked class fallback instead of the purchased external definition. Owned definitions now take priority; the handler explicitly resolves ownership. Skill level/lore lookup also reads the learned external level instead of returning class default level 1.

Upgrades use normal configured MMO skill points, not invented currency prices. Success persists levels and point expenditure. The client shows its skill-point balance and localized explanations for disabled buttons (locked, unavailable, non-upgradable, maximum level or no points). Existing maximum-level and unlock rules remain authoritative.

## Actual Minecraft runtime results

Evidence is in `Runtime-QA.zip/expanded-skills/verification.json`, raw command observations, screenshots and the two `*-player-cast-final.mp4` captures. Earlier pre-fix/intermediate captures are identified as such, not presented as final successes.

| Check | Result |
|---|---|
| Purchase Bite and Lick | PASS: configured CobbleDollars price 2500 each; actual wallet 67500 → 62500. No test money was granted. |
| Duplicate purchase | PASS: wallet remains 62500 and learned level is not reset. |
| Native `/skills` upgrade click | PASS: Bite 1 → 2; points 4 → 3. |
| Client GUI upgrade click | PASS on final build: Bite 2 → 3; points 2 → 1. |
| Native upgrade command | PASS: Lick 2 → 3; points 1 → 0; feedback reports level 3. |
| Class upgrade | PASS: Holy Strike 1 → 2 with one point. This certifies progression, not its original ability graph. |
| No points / unowned skill | PASS: upgrade rejected, no grant or expenditure. |
| Maximum level | PASS: Bite reaches configured level 10; next attempt is rejected while one point remains. Test levels/extra points were then restored. |
| Client binding | PASS: client equips Bite into authoritative native loadout slot 4. |
| Native GUI binding | PASS: `/skills` equips Lick into native slot 3; command unbind removes external slots correctly. |
| Native F input | PASS: F activates server SKILL_BAR mode; native action-bar skill list is suppressed for the presentation owner. Client displays the native input keys rather than assuming they equal bound slots. |
| Actual player casts | PASS: HUD key 3 casts Bite from loadout slot 4; HUD key 2 casts Lick from slot 3. Both produce real damage and native Cobblemon VFX. |
| Level scaling | PASS: level-3 authoritative parameters are Bite damage 6.6/cooldown 2.304s and Lick damage 3.3/cooldown 1.872s. Gear/stat modifiers produce actual cow damage 6.8772001 and 3.4386 respectively. |
| Continuous reconnect | PASS: same server process PID 75723 remains running; both external levels stay 3, class skill stays 2, points stay 0 and loadout stays `{3:Lick,4:Bite}`. |
| HUD after reconnect | PASS: casting is inactive and displayed skill slots are empty. Actual server/client max health both 32 for this test character. Historical Dragonborn high-health proof remains separate. |

Bite/Lick use Cobblemon's original action-effect definitions and native Snowstorm effects: `bite_target`, `bite_targetspark`, `lick_target`, `lick_targetsplash`, `lick_targetdrip`. Impact placement was corrected to the visible target surface so the original billboards are not hidden inside opaque models. Bounded vanilla accents supplement the native graphics. Target validity, damage and status decisions remain on the server; no Pokémon or formal battle invariant is bypassed. Audio playback could not be inspected in the headless client (no OpenAL output device); source sound behavior is retained.

## Local verification and limits

- Lib: 25 JUnit tests pass, including four inventory-overlay regression tests.
- Client: four skill-bar visibility tests pass.
- Skript: all 24 tests pass using the original supplied script directory. An initial invocation with the wrong input directory failed with missing-file errors; rerunning with `config2/Skript/scripts` passed without changing scripts/tests.
- Native MMO smoke and all 72 hotbar-input mapping cases pass.
- Native integration smoke, move models and 155 provider semantic checks pass.
- Bundled Core and separate Bridge both built successfully with matching final Lib/MMO/Integration binaries.

Test skill points, player records, wallet state, script variables, server properties and QA world are excluded from default configs. Install the matching JAR set: version labels were unchanged, so an old Lib with the same label does not contain the GUI fix.

Remaining original-specification gaps are retained in API-HANDOFF.md. There is no new all-class, all-boss, full Skript/Paper, all-155-move, sound-playback or production-performance certification.
