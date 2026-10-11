# Native Skript delivery and runtime evidence — 2026-10-11

This supersedes earlier statements that no native Skript JAR exists. SVFrameSkript-0.1.0 now executes the three required original class/dash files. It does not establish full Skript/addon parity or complete Fantasy Hub production readiness. The retained audit was not repeated.

## Verified scope

- All three source files load byte-for-byte unchanged: 19 functions, 6 commands, 12 triggers, no parse diagnostics. Source hashes and the runtime report are delivered with QA evidence.
- Java 21 local builds: 24 Skript JUnit checks and 21 matching Lib checks, zero failures/errors/skips. The supplied-source test is enabled with `-PsuppliedScripts`.
- Eight actual-client dash scenarios pass. Four walking directions each debit exactly 8 stamina; sprint, crouch, standing and insufficient-stamina cases debit zero. Ordinary resource regeneration is recorded separately from the skill debit. Real incoming damage is cancelled inside the source 20-tick dodge window and succeeds after expiry.
- All six AMK active wrappers execute the original source script through real MMO casts: Northern Gust, Clap, Heroic Surge, Overload, Hit Me and Meteor Landing. Actual cow damage, mana expenditure and source audiovisual effects were observed. Recordings are direct Minecraft captures, including meteor hover and impact; no mockups. Gust's critical passive affected its observed damage.
- Original class switching rejects insufficient funds, charges exactly the source 100000 fee, switches Dragonborn to Paladin, applies the source 3600-second cooldown and rejects a second switch without another withdrawal. This exact balance test used CobbleDollars; source cooldown variables survive restart.
- The configured BEconomy primary provider was then restored and tested with its actual native API. After an explicit QA-only credit, the original script switches to Paladin and reports BEconomy available with the original fee/cooldown. This second-provider evidence is narrower than the exact before/after CobbleDollars balance test. BEconomy additionally requires Text Placeholder API 2.4.2+1.21; the initial missing-dependency startup failure was a dependency setup issue.
- Source menu aliases open the real native class selection GUI. Close-inventory commands also close custom presentation screens.

## Changes and boundaries

The interpreter supplies typed native adapters for the exact imported APIs used by these files. Source AMK logic owns the behavior and avoids duplicate native combat execution; server MMO still owns resources/cooldowns. The class screen reads source policy and sends selection through the original command. The final source-valid class filtering hook compiles and passes the local suite but has not received another real-client runtime pass after its final change.

This is an executable subset driven by actual supplied Skript syntax, not a replacement mini-DSL. Broader Skript 2.13.2 semantics and arbitrary addons remain incomplete. Complete original-Paper comparison, every AMK passive/guard/taunt/PvP interaction, Skript-to-Pokémon runtime interactions, delayed-transaction crash recovery and all scripting reload/lifecycle combinations are not certified. Successful parsing of three scripts is not full behavioral certification.

The user's scope excludes item/shop/unrelated scripts. Historical audit counts remain 40 files / 28 enabled, but only these three class/dash files are required in the current script delivery. Original source scripts and premium assets remain outside public Git. The API handoff lists unfinished classes, bosses, move adapters, models and interfaces separately.
