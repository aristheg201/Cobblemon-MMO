# Superhero balance baseline

Static comparison at equal Physical/Magic Attack. Armor, criticals, targeting accuracy, downtime, passive procs, dodges, utility and ordinary attacks are excluded. These numbers identify tuning risks; they do not certify equal combat performance.

| Class | Pool | Combat regen/s | Full cooldown rotation cost/s | Direct coefficient/s |
|---|---:|---:|---:|---:|
| SPIDER_MAN | 100 | 4 | 11.986 | 1.155 |
| SORCERER_SUPREME | 500 | 12.5 | 33.349 | 0.627 |
| IRON_MAN | 100 | 5 | 14.481 | 1.426 |
| THOR | 100 | 0 | 11.118 | 1.109 |
| FLASH | 100 | 0 | 9.375 | 0.446 |
| BATMAN | 100 | 4 | 10.194 | 0.91 |

The full rotation is not sustainable for any class without downtime, combat gains or careful selection. Drone damage is counted across its full eight-second lifetime. Batman counter/stealth, Sorcerer reflection/5-stack proc and Thor awakened chain lightning are excluded here and require runtime combat comparisons.

PvP: every hit, recast and delayed wave in an activation shares one target ledger; regular skills cap at 25% maximum HP, ultimates at 40%. Ultimate attack coefficients cap at 3.0 before defense; the actual applied damage also obeys the HP ceiling.

Unspecified timings resolved explicitly:


- Area NxN means square side N; radial range is N/2 for matching circular visual bounds.
- Mirror Dimension damages once per second for three ticks; Agamotto bands run two seconds after 2.5s freeze.
- Thor final storm has four 0.5x bolts over two seconds by default.
- Flash funnel lasts 1.8 seconds with six 0.3s damage pulses.
- Iron Man laser burn is 5% max HP total spread over three seconds.
- Batarang recast window is two seconds; all recasts share the first activation budget.
- Independent drone damage is two drones, each 0.5x per second for eight seconds.
