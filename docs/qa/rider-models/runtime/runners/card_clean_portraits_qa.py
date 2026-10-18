from pathlib import Path
import sys,time,json
sys.path.insert(0,'/workspace/scratch');import card_master_skill_qa as q
q.OUT=Path('/workspace/integration/docs/qa/rider-models/runtime/final-models')
for rider in ['ryuki','knight','decade']:
 q.prepare(rider);q.cmd('kill @e[tag=rider_qa_target]');q.cmd('effect clear QAPlayer minecraft:night_vision');q.cmd('tp QAPlayer 200.5 65 .5 0 0');time.sleep(4)
 for name,view in [('front','200.5 64.4 2.9 180 0'),('rear','200.5 64.4 -1.9 0 0')]:
  q.cmd('tp QAAttacker '+view);time.sleep(1);q.snap(rider+'-'+name)
 q.key_f();time.sleep(.3);print('CLEAN_NATIVE_PORTRAITS',rider,flush=True)
