#!/usr/bin/env python3
"""Apply the user's enchanting policy to authored defaults, never to a live custom server config."""
import argparse, hashlib, json, re, zipfile
from pathlib import Path

def finalize(root,pack=None):
    pack=pack or root/'core/src/imported/resources/default/source-pack.zip'
    with zipfile.ZipFile(pack) as z:files={n:z.read(n) for n in z.namelist()}
    for name,data in list(files.items()):
        if name.startswith('SVFrameItems/rules/') and name.endswith('.yml'):
            files[name]=re.sub(rb'(disable-enchanting:\s*)true',rb'\g<1>false',data)
    files['FantasyHub/kabuto.yml']=(root/'core/src/main/resources/default/kabuto/FantasyHub/kabuto.yml').read_bytes()
    manifest=json.loads(files['manifest.json']);manifest['revision']='equipment-mining-compat-0.1.9'
    manifest['files']=[{'path':n,'sha256':hashlib.sha256(b).hexdigest()} for n,b in files.items() if n not in ('manifest.json','source-choices.json')]
    files['manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    with zipfile.ZipFile(pack,'w',zipfile.ZIP_DEFLATED) as z:
        for n,data in files.items():z.writestr(n,data)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);finalize(p.parse_args().root)
