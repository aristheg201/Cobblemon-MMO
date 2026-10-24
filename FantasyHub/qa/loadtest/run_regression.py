import sys,json,time
from pathlib import Path
from decimal import Decimal
import argparse
parser=argparse.ArgumentParser(description="Destructive native QA on a disposable test-world copy only")
parser.add_argument('--server',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--player',default='QAClass024');args=parser.parse_args()
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from run_runtime_qa import command as rcon
props=dict(v.split('=',1) for v in (args.server/'server.properties').read_text().splitlines() if '=' in v and not v.startswith('#'))
def command(text):return rcon('127.0.0.1',int(props.get('rcon.port','25575')),props['rcon.password'],text)
out=args.output;out.mkdir(parents=True,exist_ok=True);events=[]
def run(c):
 raw=command(c);events.append({'at':time.time(),'command':c,'result':raw});(out/'transactions.json').write_text(json.dumps(events,ensure_ascii=False,indent=2)+'\n');return raw
name=args.player
def q(action,id='x',method=0):
 raw=run(f'execute as {name} run commerceqa {action} "{id}" {method}')
 return json.loads(raw[raw.index('{'):])
def money(s):return tuple(Decimal(s[k]) for k in ['beastBalance','hunterBalance'])
checks=[]
def check(label,condition):
 checks.append({'check':label,'passed':bool(condition)});(out/'regression-checks.json').write_text(json.dumps(checks,indent=2)+'\n');assert condition,label
run(f'clear {name}');run(f'tp {name} 100 71 100');run(f'gamemode survival {name}')
a=q('seed');run(f'svframemmo admin class {name} THOR');q('ready');q('starter');q('unown','FLASH');a=q('status');b=q('switch','FLASH');check('free starter selection',b['result'] and b['class']=='FLASH' and money(a)==money(b))
q('unown','THOR');a=q('status');b=q('buy','class:THOR',1);check('BeastCoin entitlement only',b['result'] and b['class']=='FLASH' and money(b)==(money(a)[0]-12000,money(a)[1]))
q('unown','SORCERER_SUPREME');a=q('status');b=q('buy','class:SORCERER_SUPREME',2);check('HunterCoin entitlement only',b['result'] and b['class']=='FLASH' and money(b)==(money(a)[0],money(a)[1]-3))
q('ready');a=q('status');b=q('switch','THOR');check('owned class switch free',b['result'] and b['class']=='THOR' and money(a)==money(b))
q('cooldown');a=q('status');b=q('switch','SORCERER_SUPREME');check('cooldown blocks without debit',not b['result'] and b['class']=='THOR' and money(a)==money(b))
a=q('status');b=q('bypass','SORCERER_SUPREME');check('early bypass BeastCoin only',b['result'] and b['class']=='SORCERER_SUPREME' and money(b)==(money(a)[0]-500,money(a)[1]))
q('ready');a=q('status');b=q('bypass','THOR');check('expired bypass has zero charge',b['result'] and b['class']=='THOR' and money(a)==money(b))
q('unown','AGITO');a=q('status');b=q('duplicate','bundle:AGITO',2);check('bundle and duplicate nonce',b['result'] and b['class']=='THOR' and money(b)==(money(a)[0],money(a)[1]-4))
gear=' '.join(b['inventory']);check('Agito belt and authored weapons',all(x.lower() in gear.lower() for x in ['AGITO_BELT','AGITO_FLAME_SABER','AGITO_STORM_HALBERD','AGITO_SHINING_CALIBER']))
q('full');a=q('status');b=q('buy','bundle:AGITO',1);check('owned bundle equipment only to mailbox',b['result'] and b['class']=='THOR' and money(b)==(money(a)[0]-4000,money(a)[1]) and b['mailCount']>0)
q('clearfill');b=q('claim');check('mailbox recovery',b['mailCount']==0)
q('unown','KABUTO');b=q('buy','bundle:KABUTO',1);gear=' '.join(b['inventory']);check('Kabuto real belt and Kunai Gun',b['result'] and all(x.lower() in gear.lower() for x in ['KABUTO_ZECTER_BELT','KABUTO_KUNAI_GUN']))
a=q('status');b=q('buy','exp_boost',2);check('regular item minimum HunterCoin path',b['result'] and money(b)==(money(a)[0],money(a)[1]-2))
b=q('upgrade_coverage');check('all registered RPG upgrade templates',b['result'])
print(json.dumps(checks,indent=2))
