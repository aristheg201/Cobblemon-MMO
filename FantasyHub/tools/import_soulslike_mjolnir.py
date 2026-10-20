#!/usr/bin/env python3
"""Import requested weapon model/texture data only. Never bundle Soulslike's executable mod."""
import argparse,hashlib,json,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('archive',type=Path);a=p.parse_args()
out=Path(__file__).resolve().parents[1]/'client/src/imported/resources/assets/fantasyhub'
files=[]
with zipfile.ZipFile(a.archive) as z:
 for source,target in [('geo/mjolnir.geo.json','native_geometry/heroes/soulslike_mjolnir.geo.json'),('textures/entity/mjolnir.png','textures/item/heroes/soulslike_mjolnir.png'),('textures/entity/mjolnir_glowmask.png','textures/item/heroes/soulslike_mjolnir_glowmask.png')]:
  source='assets/soulsweapons/'+source;info=z.getinfo(source)
  if info.file_size>2_000_000:raise ValueError('Unexpected oversized model data')
  data=z.read(source)
  if source.endswith('.json'):
   model=json.loads(data);assert model['format_version']=='1.12.0' and model['minecraft:geometry'][0]['bones']
  dest=out/target;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
  files.append({'source':source,'target':'assets/fantasyhub/'+target,'sha256':hashlib.sha256(data).hexdigest()})
(out/'soulslike-mjolnir-source.json').write_text(json.dumps({'version':'1.4.8-1.21.1-fabric','archiveSha512':hashlib.sha512(a.archive.read_bytes()).hexdigest(),'source':'https://modrinth.com/mod/mariums-soulslike-weaponry','runtimeBundled':False,'files':files},indent=2)+'\n')
print('Imported model, diffuse and glowmask; executable archive not bundled.')
