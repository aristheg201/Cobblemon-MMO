#!/usr/bin/env python3
"""Package a verified local Bridge/client build and a private, complete source bundle.

Private imported resources never become public git files. No worlds, credentials,
Gradle caches, repository metadata or CI service are included or invoked.
"""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,zipfile

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--build',type=Path,required=True)
parser.add_argument('--runtime-mods',type=Path,required=True)
parser.add_argument('--items-source',type=Path,required=True)
parser.add_argument('--mobs-source',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
a=parser.parse_args();repo=Path(__file__).resolve().parents[2];a.output.mkdir(parents=True,exist_ok=True)
manifest=json.loads((a.build/'build-manifest.json').read_text())
if manifest['dirty']:raise SystemExit('Commit source and evidence before final delivery packaging')
if manifest['commit']!=subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],text=True).strip():raise SystemExit('Build manifest does not match current source commit')
for row in manifest['artifacts']:
 if hashlib.sha256((a.build/row['file']).read_bytes()).hexdigest()!=row['sha256']:raise SystemExit('Build checksum mismatch '+row['file'])
update=a.output/'Kamen-Riders-0.1.12-Bridge-Client.zip';source=a.output/'Kamen-Riders-0.1.12-Source-Blockbench-QA.zip'
dependencies=['SVFrameItems-0.1.2-fabric.jar','SVFrameMobs-0.1.2-fabric.jar','SVFrameMMO-Cobblemon-Integration-0.1.18.jar','SVFrameSkript-0.1.0.jar']
with zipfile.ZipFile(update,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for row in manifest['artifacts']:z.write(a.build/row['file'],('client-update/' if 'Client' in row['file'] else 'server-update/')+row['file'])
 for name in dependencies:z.write(a.runtime_mods/name,'server-dependencies/'+name)
 z.write(a.build/'build-manifest.json','build-manifest.json')
 for path in sorted(a.build.rglob('*')):
  if path.is_file() and path.suffix in ['.log','.xml']:z.write(path,'local-build/'+str(path.relative_to(a.build)))
 for path in [repo/'docs/KAMEN-RIDERS-0.1.12.md',repo/'docs/qa/rider-models/LOCAL_BUILD.md']:
  z.write(path,'documentation/'+path.name)
 for path in (repo/'FantasyHub/core/src/main/resources/default/riders').rglob('*.yml'):
  z.write(path,'default-config-reference/'+str(path.relative_to(repo/'FantasyHub/core/src/main/resources/default/riders')))
 z.writestr('INSTALL.vi.txt','SERVER: Thay FantasyHubBridge, SVFrameLib va SVFrameMMO trong server-update. Giu cac dependency tuong thich; ban dang test nam trong server-dependencies.\nCLIENT: Thay FantasyHubClient tren moi client. Minecraft1.21.1/Fabric/Java21. Khong cai Bridge cung bundled FantasyHubCore.\nGiu world/player data/config tuy chinh. Config cu tu migrate danh sach Rider, co backup. Cap quyen theo permission-prefix cua server, mac dinh svframe.class.ryuki / knight / decade.\nF8 mo ho so/chon class, deo belt dung Rider, F bat/tat Henshin va skill bar; dung7 phim hien tren thanh. Survive/Complete la toggle vo han.\nBuild va92 test deu chay local, khong GitHub Actions. Bao cao, source,41 Blockbench va anh/video Minecraft that nam trong goi Source-Blockbench-QA.\n')
# Tracked sources are authoritative. Private legacy inputs and external unchanged
# module sources are added only to this user-owned source ZIP.
seen=set()
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 def add(path,name):
  if name in seen:return
  seen.add(name);z.write(path,name)
 for raw in subprocess.check_output(['git','-C',str(repo),'ls-files','-z']).split(b'\0'):
  if not raw:continue
  relative=raw.decode();path=repo/relative
  if path.is_file():add(path,'source/integration/'+relative)
 for relative in ['FantasyHub/core/src/imported/resources','FantasyHub/client/src/imported/resources']:
  for path in (repo/relative).rglob('*'):
   if path.is_file():add(path,'source/integration/'+str(path.relative_to(repo)))
 for name,root in [('SVFrameItems',a.items_source),('SVFrameMobs',a.mobs_source)]:
  for path in root.rglob('*'):
   if path.is_file() and not any(part in ['.git','.gradle','build','dist','validated','logs','run','__pycache__'] for part in path.relative_to(root).parts):add(path,'source/integration/'+name+'/'+str(path.relative_to(root)))
  add(a.runtime_mods/(name+'-0.1.2-fabric.jar'),'source/integration/'+name+'/build/libs/'+name+'-0.1.2-fabric.jar')
 for row in manifest['artifacts']:
  if row['file'].startswith('SVFrame'):
   name='SVFrameLib' if 'Lib-' in row['file'] else 'SVFrameMMO';add(a.build/row['file'],'source/integration/'+name+'/build/libs/'+row['file'])
 for path in a.build.rglob('*'):
  if path.is_file():add(path,'verified-local-build/'+str(path.relative_to(a.build)))
 z.writestr('SOURCE-README.txt','This private bundle includes original imported user resources needed by the existing build,41 editable Blockbench projects, complete tracked source, the unchanged Items/Mobs module sources and exact local dependency JARs. Do not publish the imported user packs/photos without authorization.\nFrom source/integration: JAVA_HOME=/path/to/jdk21 GRADLE_BIN=/path/to/gradle8.8/bin/gradle FantasyHub/tools/build_local.sh\nOrdinary Maven/Loom dependencies need network access or an already populated local cache; caches and credentials are not bundled. No GitHub Actions is used.\nNative screenshots/videos and reports: source/integration/docs/qa/rider-models/runtime. Main report: source/integration/docs/KAMEN-RIDERS-0.1.12.md. Test XML/local logs and exact commit/hash manifest: verified-local-build.\n')
for path in [update,source]:
 with zipfile.ZipFile(path) as z:
  bad=z.testzip()
  if bad:raise SystemExit('Corrupt ZIP entry '+bad)
result={'commit':manifest['commit'],'branch':'feature/kamen-rider-ryuki-knight-decade','files':[{'file':p.name,'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [update,source]],'requiredUpdatedJars':manifest['artifacts'],'unchangedRuntimeDependencies':[{'file':n,'sha256':hashlib.sha256((a.runtime_mods/n).read_bytes()).hexdigest()} for n in dependencies]}
(a.output/'delivery-manifest.json').write_text(json.dumps(result,indent=2)+'\n')
shutil.copy2(repo/'docs/KAMEN-RIDERS-0.1.12.md',a.output/'KAMEN-RIDERS-0.1.12.md')
print(json.dumps(result,indent=2))
