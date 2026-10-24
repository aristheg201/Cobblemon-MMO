#!/usr/bin/env python3
"""Report repeated, equivalent A/B windows, including direct FantasyHub callback probes."""
from pathlib import Path
import json,math,statistics,sys
root=Path(sys.argv[1]);runs={}
def stats(ns):
 v=sorted(x/1e6 for x in ns)
 if not v:return {}
 return {'count':len(v),'meanMs':statistics.mean(v),'p50Ms':v[math.ceil(len(v)*.50)-1],'p95Ms':v[math.ceil(len(v)*.95)-1],'p99Ms':v[math.ceil(len(v)*.99)-1],'maxMs':v[-1]}
for phase in sys.argv[2:]:
 runs[phase]={}
 for n in [1,5,10,20,40]:
  path=root/phase/str(n)
  if not (path/'ticks.json').exists():continue
  tick=json.loads((path/'ticks.json').read_text());samples=json.loads((path/'samples.json').read_text());actor=json.loads((path/'bots.log').read_text().splitlines()[-1]);assert tick['players']==n and actor['joined']==n and not actor['failures'];assert all(f'There are {n} of' in x['list'] for x in samples)
  assert not tick['overflow'] and not tick['fantasySync']['overflow'];assert len(tick['tickNanos'])>500
  seconds=(tick['endEpochMs']-tick['startEpochMs'])/1000;sync=tick['fantasySync'];last=json.loads(samples[-1]['perf']);cleanup=json.loads((path/'cleanup.json').read_text());assert cleanup['connectedPlayers']==cleanup['pipeline']['sessions']==0
  before={row['name']:row for row in tick['gcStart']};gc={row['name']:{'collections':row['count']-before[row['name']]['count'],'pauseMs':row['timeMs']-before[row['name']]['timeMs']} for row in tick['gcEnd']}
  runs[phase][str(n)]={'seconds':seconds,'ticks':stats(tick['tickNanos']),'syncWall':stats(sync['wallNanos']),'syncCpu':stats(sync['cpuNanos']),'syncAllocationMBps':sum(sync['allocatedBytes'])/1e6/seconds,'mainAllocationMBps':sum(tick['mainAllocatedBytes'])/1e6/seconds,'cpuCorePercent':tick['processCpuNanos']/1e9/seconds*100,'gc':gc,'hudLatency':last.get('captureToDispatch'),'packets':last.get('packets'),'wireBytes':last.get('wirePayloadBytes'),'pipeline':last['pipeline'],'actor':actor,'commerce':last.get('commerce'),'captureErrors':last['captureErrors']}
  assert last['captureErrors']==0 and last['pipeline']['workerErrors']==0
  assert last['pipeline']['completed']>0 and last['pipeline']['completed']>last['pipeline']['submitted']*.35,(phase,n,'snapshot starvation')
(root/'ab-results.json').write_text(json.dumps(runs,indent=2)+'\n')
print('| Run | Players | Server P50/P95/P99 ms | Sync P95/P99/max ms | Sync MB/s | Main MB/s |')
print('|---|---:|---:|---:|---:|---:|')
for phase,windows in runs.items():
 for n,row in windows.items():
  a,b=row['ticks'],row['syncWall'];print(f'| {phase} | {n} | {a["p50Ms"]:.2f}/{a["p95Ms"]:.2f}/{a["p99Ms"]:.2f} | {b["p95Ms"]:.3f}/{b["p99Ms"]:.3f}/{b["maxMs"]:.2f} | {row["syncAllocationMBps"]:.3f} | {row["mainAllocationMBps"]:.2f} |')
