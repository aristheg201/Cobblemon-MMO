from pathlib import Path
import sys,json,time,re
sys.path.insert(0,'/workspace/scratch');import card_master_skill_qa as q
q.OUT=Path('/workspace/integration/docs/qa/rider-models/runtime/event-combat');q.OUT.mkdir(exist_ok=True)
results=[]
def store(name,r,checks):
 r['checks']=checks;(q.OUT/(name+'.json')).write_text(json.dumps(r,indent=2)+'\n');results.append({'scenario':name,**checks});(q.OUT/'results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,json.dumps(checks),flush=True);assert all(checks.values()),(name,checks)
def hp(player='QAPlayer'):
 raw=q.cmd('data get entity '+player+' Health');return float(re.findall(r'([-\d.]+)f',raw)[-1])
def heal(player='QAPlayer'):q.cmd('svframemmo admin resource-health set '+player+' 999')
deadline=time.monotonic()+40
while time.monotonic()<deadline:
 if 'QAPlayer' in q.cmd('list') and 'QAAttacker' in q.cmd('list'):break
 time.sleep(.5)
else:raise RuntimeError('Clients not reconnected')
q.cmd('effect clear QAPlayer minecraft:regeneration')
q.prepare('knight');before=hp();q.cmd('damage QAPlayer 10 minecraft:generic');after=hp();normal=before-after
q.prepare('knight');r=q.cast('knight_guard_vent',False);before=hp();q.cmd('damage QAPlayer 10 minecraft:generic');after=hp();guarded=before-after;r=q.report('cast_knight_guard_vent');store('knight-eighty-percent-guard',{'normalLoss':normal,'guardedLoss':guarded,'native':r},{'baselineDamagePositive':normal>0,'eightyPercentReduction':abs(guarded/normal-.2)<.03})
for kind in ['mob_attack','arrow']:
 q.prepare('ryuki');q.cmd('execute as @e[tag=rider_qa_target] run attribute @s minecraft:generic.armor base set 0');r=q.cast('ryuki_guard_vent',False);shield=r['immediate']['shield'];q.cmd('execute at QAPlayer run damage QAPlayer 10 minecraft:'+kind+' by @e[tag=rider_qa_target,limit=1,sort=nearest]');r=q.report('cast_ryuki_guard_vent');blocked=shield-r['settled']['shield'];reflected=sum(h['amount'] for h in r['hits']);checks={'shieldAbsorbedDamage':blocked>0}
 if kind=='mob_attack':checks['reflectsTwentyFivePercent']=blocked>0 and abs(reflected/blocked-.25)<.03
 else:checks['rangedNotReflected']=not r['hits']
 store('ryuki-reflection-'+kind,{'native':r,'shieldAbsorbed':blocked,'reflectedDamage':reflected},checks)
q.prepare('ryuki',True);r=q.cast('ryuki_sword_vent');store('ryuki-half-armor-and-restoration',r,{'accepted':r['castResult'],'realHits':bool(r['hits']),'halfArmorDuringEveryHit':all(h['armorDuring']==h['armorBefore']//2 for h in r['hits']) and any(h['armorBefore']==20 for h in r['hits']),'armorRestoredAfterEveryHit':all(h['armorAfter']==h['armorBefore'] for h in r['hits'])})
q.cmd('fill 190 159 -10 210 159 15 minecraft:stone')
for rear in [True,False]:
 q.prepare('knight');q.cmd('tp QAPlayer 200.5 160 .5 0 0');q.cmd('execute as @e[tag=rider_qa_target] at @s run tp @s ~ 160 ~');q.cmd('svframemmo admin resource-stamina set QAPlayer 20')
 if not rear:q.cmd('execute as @e[tag=rider_qa_target] run data merge entity @s {Rotation:[180f,0f]}')
 r=q.cast('knight_nasty_vent');delta=r['staminaAfter']-r['staminaBefore'];checks={'realHits':bool(r['hits']),'staminaRestoresOnce':abs(delta-(4 if rear else 0))<.01}
 if rear:checks['everyRearHitCritical']=all(h['critical'] for h in r['hits'] if h['target'] in {t['uuid'] for t in r['targetsBefore']})
 store('knight-'+('rear' if rear else 'front')+'-passive',r,checks)
# Native PvP and vanilla-team friendly-fire policy, without dummy overlap.
q.prepare('ryuki');q.cmd('kill @e[tag=rider_qa_target]');q.cmd('gamemode survival QAAttacker');q.cmd('effect clear QAAttacker minecraft:regeneration');q.cmd('tp QAAttacker 200.5 65 3.2 180 0');q.cmd('tag QAAttacker add rider_qa_target');heal('QAAttacker')
try:
 before=hp('QAAttacker');r=q.cast('ryuki_strike_vent');after=hp('QAAttacker');store('native-pvp-enemy',{'native':r,'healthBefore':before,'healthAfter':after},{'accepted':r['castResult'],'actualPlayerHit':any(h['target']=='e585ad00-e318-3e35-aa1c-115aefd03416' for h in r['hits']),'playerHealthDecreased':after<before})
 time.sleep(4);q.cmd('team add rider_qa_pvp_allies');q.cmd('team modify rider_qa_pvp_allies friendlyFire false');q.cmd('team join rider_qa_pvp_allies QAPlayer');q.cmd('team join rider_qa_pvp_allies QAAttacker');heal('QAAttacker');before=hp('QAAttacker');r=q.cast('ryuki_strike_vent');after=hp('QAAttacker');store('native-pvp-friendly-fire',{'native':r,'healthBefore':before,'healthAfter':after},{'accepted':r['castResult'],'noAllyHit':not r['hits'],'allyHealthPreserved':after>=before})
finally:
 q.cmd('tag QAAttacker remove rider_qa_target');q.cmd('team remove rider_qa_pvp_allies');q.cmd('gamemode spectator QAAttacker')
print('NATIVE_COMBAT',len(results),flush=True)
