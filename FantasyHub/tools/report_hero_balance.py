#!/usr/bin/env python3
"""Static tuning evidence. It intentionally does not claim combat/runtime acceptance."""
import json,argparse,csv
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--catalog',type=Path,default=Path(__file__).resolve().parents[1]/'core/src/main/resources/default/heroes.json');p.add_argument('--output',type=Path,required=True);a=p.parse_args();d=json.loads(a.catalog.read_text());a.output.mkdir(parents=True,exist_ok=True)
rows=[];classes=[]
for h in d['heroes']:
 for skill in h['abilities']:
  scaling=sum(p['scale']*p['hits'] for p in skill['phases'] if p['kind']!='MAX_HEALTH')
  # Blitz and chain lightning divide their hit list across distinct targets.
  per_target=scaling
  if skill['key']=='WEB_BLITZ':per_target=1.5
  if skill['id']=='FLASH_LIGHTNING_THROW':per_target=1.7
  pvp=per_target*(min(1,d['balance']['ultimate_attack_budget']/per_target) if skill['ultimate'] and per_target else d['balance']['pvp_multiplier'])
  rows.append(dict(hero=h['id'],skill=skill['id'],cooldown=skill['cooldown'],cost=skill['cost'],resource=h['resource_name'],max_attack_coefficient_across_targets=round(scaling,4),max_single_target_coefficient=round(per_target,4),pvp_single_target_before_defense=round(pvp,4),pvp_hp_ceiling=d['balance']['ultimate_cast_hp_cap' if skill['ultimate'] else 'regular_cast_hp_cap'],duration=skill['duration'],recast=skill['recast']))
 own=[r for r in rows if r['hero']==h['id']];pressure=sum(r['cost']/r['cooldown'] for r in own)
 classes.append(dict(hero=h['id'],resource_capacity=h['maximum'],combat_regen=h['regen_combat'],full_rotation_energy_per_second=round(pressure,3),raw_direct_attack_coefficient_per_second=round(sum(r['max_single_target_coefficient']/r['cooldown'] for r in own),3)))
with (a.output/'abilities.csv').open('w') as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
(a.output/'analysis.json').write_text(json.dumps(dict(kind='static coefficient and energy analysis, not gameplay QA',classes=classes,abilities=rows,assumptions=d['assumptions']),indent=2)+'\n')
lines=['# Superhero balance baseline','', 'Static comparison at equal Physical/Magic Attack. Armor, criticals, targeting accuracy, downtime, passive procs, dodges, utility and ordinary attacks are excluded. These numbers identify tuning risks; they do not certify equal combat performance.','', '| Class | Pool | Combat regen/s | Full cooldown rotation cost/s | Direct coefficient/s |','|---|---:|---:|---:|---:|']
for c in classes:lines.append('| {hero} | {resource_capacity} | {combat_regen} | {full_rotation_energy_per_second} | {raw_direct_attack_coefficient_per_second} |'.format(**c))
lines+=['','The full rotation is not sustainable for any class without downtime, combat gains or careful selection. Drone damage is counted across its full eight-second lifetime. Batman counter/stealth, Sorcerer reflection/5-stack proc and Thor awakened chain lightning are excluded here and require runtime combat comparisons.','', 'PvP: every hit, recast and delayed wave in an activation shares one target ledger; regular skills cap at 25% maximum HP, ultimates at 40%. Ultimate attack coefficients cap at 3.0 before defense; the actual applied damage also obeys the HP ceiling.','', 'Unspecified timings resolved explicitly:']+['','']
lines += ['- '+s for s in d['assumptions']]
(a.output/'BASELINE.md').write_text('\n'.join(lines)+'\n');print('Wrote',len(rows),'skill rows and',len(classes),'class baselines')
