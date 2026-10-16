# Port completion matrix

The machine-readable authority is [PORT_COMPLETION_MATRIX.json](PORT_COMPLETION_MATRIX.json). It reuses the retained migration inventory. Imported, parsed or compiled content is not certified behavioral parity. Source variants retain their own references; these are requirement counts, not completion percentages.

All supplied scripts and the broader upstream compatibility target remain recorded. Original disabled examples stay disabled in production. Base armor is required; armor upgrades are excluded by the user's instruction.

| Category | Records |
| --- | ---: |
| attribute | 10 |
| class | 36 |
| class_skill | 237 |
| dedicated_gui | 12 |
| drops | 83 |
| equipment | 3114 |
| mob | 1132 |
| model | 685 |
| mythic_skill | 4955 |
| npc | 6 |
| quest | 1519 |
| requirement | 19 |
| script | 40 |
| skill_wrapper | 796 |
| sound | 882 |
| source_gui | 70 |
| spawner | 261 |
| texture | 12284 |

The user authorized omitting only the absent `NinjaDamageEvent` call owned by `WardenAttackDummy`. The waiver is recorded in `source_waivers`; it never suppresses a supplied-but-unsupported definition, other missing skills or another mob's reference, and it receives no completed-parity credit. All defined Warden mechanics still require implementation and runtime verification.

Run `python FantasyHub/tools/check_port_completion.py` for the release gate. `--validate-only` validates records without authorizing release. The full release gate currently fails: classes, bosses, assets, GUIs, language compatibility and other requirements still lack complete behavioral runtime verification. Retained JSON assets and the full Cobblemon registry still require reconciliation.

Focused Hunter runtime acceptance is recorded in [HUNTER-IMPLEMENTATION-QA.md](HUNTER-IMPLEMENTATION-QA.md). Its narrow regression entry passes; full class/equipment and ecosystem parity remain unfinished.

Focused Kabuto native-input/skill/reconnect acceptance is recorded in [KABUTO-IMPLEMENTATION-QA.md](KABUTO-IMPLEMENTATION-QA.md). Its narrow regression entry passes; the five broad addition entries remain IN_PROGRESS. The original inventory above is retained; additions and current exact counts are in the JSON/release-gate output.

2026-10-16: narrow Kabuto VFX/gun acceptance0.1.7 is verified; see KABUTO-VFX-0.1.7-QA.md. Full-port release gate remains FAIL; left-hand and broader model/class acceptance are not promoted.
