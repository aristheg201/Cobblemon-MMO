#!/usr/bin/env python3
"""Reject invalid player windows; compare exact, bounded native QA tick/allocation samples."""
from pathlib import Path
import json,math,statistics,sys
root=Path(sys.argv[1]);result={}
for phase in ['baseline','optimized']:
 result[phase]={}
 for players in [1,5,10,20,40]:
  path=root/phase/str(players)
  if not (path/'ticks.json').exists():continue
  ticks=json.loads((path/'ticks.json').read_text());samples=json.loads((path/'samples.json').read_text())
  last=(path/'bots.log').read_text().splitlines()[-1]
  if not last.startswith('{'):continue
  actor=json.loads(last)
  assert ticks['players']==players and actor['joined']==players and not actor['failures'],(phase,players,'lost clients')
  assert all(f'There are {players} of' in row['list'] for row in samples),(phase,players,'player count changed')
  assert not ticks['overflow'] and len(ticks['tickNanos'])>500,(phase,players,'invalid tick samples')
  wall=(ticks['endEpochMs']-ticks['startEpochMs'])/1000
  times=sorted(n/1e6 for n in ticks['tickNanos'])
  def quantile(p):return times[max(0,math.ceil(len(times)*p)-1)]
  before={v['name']:v for v in ticks['gcStart']}
  gc={v['name']:{'collections':v['count']-before[v['name']]['count'],'pauseMs':v['timeMs']-before[v['name']]['timeMs']} for v in ticks['gcEnd']}
  data={'players':players,'ticks':len(times),'seconds':wall,'observedTicksPerSecond':len(times)/wall,'meanMs':statistics.mean(times),'p95Ms':quantile(.95),'p99Ms':quantile(.99),'maxMs':max(times),'over100ms':sum(x>100 for x in times),'mainAllocationMBps':sum(ticks['mainAllocatedBytes'])/1e6/wall,'cpuCorePercent':ticks['processCpuNanos']/1e9/wall*100,'heapUsedEndBytes':ticks['heapUsed'],'gc':gc,'modPayloadPackets':actor['packets'],'modPayloadBytes':actor['packetBytes'],'purchaseAttempts':actor['purchases'],'castAttempts':actor['casts'],'lastPerf':json.loads(samples[-1]['perf'])}
  result[phase][str(players)]=data
(root/'comparison.json').write_text(json.dumps(result,indent=2)+'\n')
print('| Players | Mean ms before/after | P95 ms before/after | P99 ms before/after | Max ms before/after | Main MB/s before/after |')
print('|---:|---:|---:|---:|---:|---:|')
for n in ['1','5','10','20','40']:
 if n not in result['baseline'] or n not in result['optimized']:continue
 a,b=result['baseline'][n],result['optimized'][n]
 print('| '+n+' | '+' | '.join(f'{a[k]:.2f} / {b[k]:.2f}' for k in ['meanMs','p95Ms','p99Ms','maxMs','mainAllocationMBps'])+' |')
