#!/usr/bin/env python3
"""Original synthesized card-reader, form-change, launch and impact cues. No film recordings."""
import math,random,wave,struct,subprocess,tempfile,json
from pathlib import Path
RATE=48000

def synth(kind):
    duration={'vent':.75,'form':1.8,'shot':.3,'impact':.6}[kind];rng=random.Random(5801);out=[];filtered=phase=0
    for i in range(int(duration*RATE)):
        t=i/RATE;noise=rng.uniform(-1,1);filtered=.84*filtered+.16*noise;v=0
        if kind in ('vent','form'):
            # Read-in pulse, paired alternating scanner chirps, then a low confirmation chord.
            for at,freq in [(.03,1250),(.16,1900),(.29,1500),(.42,2300)]:
                d=t-at
                if 0<d<.09:v+=.22*math.sin(math.tau*freq*d)*math.sin(math.pi*d/.09)**2
            if t>.5:
                d=t-.5
                for freq in [164,246,328]:v+=.075*math.sin(math.tau*freq*d)*math.exp(-d*5)
            if kind=='form':
                phase+=math.tau*(140+520*t)/RATE
                v+=.22*math.sin(phase)*math.sin(math.pi*t/duration)**2+.28*filtered*math.sin(math.pi*t/duration)**2
        else:
            phase+=math.tau*(110+1800*math.exp(-t*30))/RATE
            v=.55*noise*math.exp(-t*(55 if kind=='shot' else 15))+.36*math.sin(phase)*math.exp(-t*14)
        out.append(int(max(-.9,min(.9,v))*32767*min(1,t/.002,(duration-t)/.02)))
    return out

def main():
    root=Path(__file__).resolve().parents[1];assets=root/'client/src/main/resources/assets/fantasyhub';dest=assets/'sounds/riders';dest.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory() as folder:
        for kind in ['vent','form','shot','impact']:
            samples=synth(kind);wav=Path(folder)/(kind+'.wav')
            with wave.open(str(wav),'wb') as out:out.setnchannels(1);out.setsampwidth(2);out.setframerate(RATE);out.writeframes(struct.pack('<'+'h'*len(samples),*samples))
            subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-i',str(wav),'-c:a','libvorbis','-q:a','5',str(dest/(kind+'.ogg'))],check=True)
    p=assets/'sounds.json';sounds=json.loads(p.read_text())
    for kind in ['vent','form','shot','impact']:sounds['riders.'+kind]={'subtitle':'subtitle.fantasyhub.riders_'+kind,'sounds':['fantasyhub:riders/'+kind]}
    p.write_text(json.dumps(sounds,indent=2)+'\n')
    for lang,entries in {'en_us':['Card reader','Rider transformation','Energy discharge','Rider impact'],'vi_vn':['Quẹt thẻ Vent','Rider chuyển dạng','Phóng năng lượng','Rider va chạm']}.items():
        p=assets/'lang'/(lang+'.json');data=json.loads(p.read_text());data.update({'subtitle.fantasyhub.riders_'+k:v for k,v in zip(['vent','form','shot','impact'],entries)});p.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
if __name__=='__main__':main()
