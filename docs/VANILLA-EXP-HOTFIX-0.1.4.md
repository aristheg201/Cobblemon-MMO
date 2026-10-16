# Vanilla EXP hotfix 0.1.4

The old MMO display mode cancelled vanilla gains, wrote the RPG level into the actual Minecraft level/progress/total fields every eight ticks and suppressed vanilla death EXP drops. Separately, the client replaced the EXP HUD even when the server selected VANILLA. Reproduction: set vanilla level10 and add7 points; it returned to RPG level100 and zero points. With VANILLA selected the native fields stayed10/7, while the old HUD still showed100.

The fix preserves native EXP in both display modes. The mixin observes completed positive gains only for optional RPG mirroring, without cancellation. Enchanting, anvils and Mending use native Minecraft fields; no RPG EXP packets or periodic native-field writes occur. Normal death EXP calculation remains vanilla. Core sends a semantic experienceDisplay preference; the client overrides the EXP HUD only for explicit MMO. Missing/invalid preferences leave the vanilla HUD intact.

Install matching server/client0.1.4. Standalone SVFrameMMO is1.13.3-fabric. Bundled Core contains the framework mods; the separate-mod Bridge contains only presentation networking and requires the five standalone framework mods. Install exactly one server layout. The presentation client JAR goes in the player's game.

To show Minecraft's real EXP bar/level, keep this block in existing config/SVFrameMMO/config.yml (preserve all other configuration):

```yaml
vanilla-exp-display:
  mode: VANILLA
```

MMO is now a display preference only. Optional vanilla-exp-redirection.enabled still mirrors positive vanilla gains into RPG progression at the configured ratio, while preserving the original vanilla gain. Disabling it leaves the two progressions independent.

Actual dedicated-server/real-client checks pass for both display modes with RPG mirroring off/on: direct gains, real ExperienceOrbEntity pickup, Mending repair, native EnchantmentScreenHandler button/enchantment/lapis/cost, native AnvilScreenHandler rename/output/cost and survival of costs across40 subsequent server ticks. Client/server native level/progress/total agree; VANILLA mode renders the real level10 rather than the independent class level100. Logs and unedited in-game screenshots are retained in docs/qa/vanilla-xp. The automatic fixture restores its test player's equipment, RPG profile, health and native EXP; it uses temporary owned items/orbs. It does not certify every enchantment or anvil recipe. Native death-drop cancellation was removed; no new death regression is claimed here.

Existing vanilla EXP previously overwritten by the old mode cannot be reconstructed reliably without a backup. This is a focused EXP bugfix, not a completed full-port release; the production completion matrix remains incomplete.
