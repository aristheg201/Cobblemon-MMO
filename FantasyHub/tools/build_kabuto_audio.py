#!/usr/bin/env python3
"""Original synthetic announcer cues; no actor samples or movie recordings."""
import argparse,json,os,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--espeak',type=Path,required=True);p.add_argument('--data-parent',type=Path,required=True);p.add_argument('--library-path');a=p.parse_args()
hub=Path(__file__).resolve().parents[1];out=hub/'client/src/imported/resources/assets/fantasyhub/sounds/kabuto';out.mkdir(parents=True,exist_ok=True)
env=os.environ.copy()
if a.library_path:env['LD_LIBRARY_PATH']=a.library_path
sounds=json.loads((hub/'client/src/main/resources/assets/fantasyhub/sounds.json').read_text())
with tempfile.TemporaryDirectory(prefix='kabuto-original-audio-') as tmp:
 for cue,text in [('henshin','Henshin'),('cast_off','Cast. Off.'),('clock_up','Clock. Up.'),('clock_over','Clock. Over.'),('rider_kick','Rider. Kick.'),('hyper','Hyper. Zecter.')]:
  wav=Path(tmp)/(cue+'.wav')
  subprocess.run([str(a.espeak),'--path='+str(a.data_parent),'-v','en-us','-p','18','-s','140','-a','160','-w',str(wav),text],check=True,env=env)
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-af','asetrate=19000,aresample=44100,highpass=f=180,lowpass=f=4200,aecho=0.8:0.5:38:0.18,alimiter=limit=0.9','-ac','1','-c:a','libvorbis','-q:a','5',str(out/(cue+'.ogg'))],check=True)
  sounds['kabuto.'+cue]={'subtitle':'subtitle.fantasyhub.kabuto_'+cue,'sounds':['fantasyhub:kabuto/'+cue]}
(hub/'client/src/main/resources/assets/fantasyhub/sounds.json').write_text(json.dumps(sounds,indent=2)+'\n')
print('Generated6original digital-announcer cues; source speech and processing are in this script.')
