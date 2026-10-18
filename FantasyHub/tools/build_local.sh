#!/usr/bin/env bash
# Local Java 21 build, tests and candidate packaging. Does not invoke remote CI.
set -euo pipefail
module_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
repo_dir="$(cd -- "$module_dir/.." && pwd)"
output_dir="${RIDER_BUILD_OUTPUT:-$repo_dir/artifacts/local-riders}"
mkdir -p "$output_dir"
if [[ -n "${GRADLE_BIN:-}" ]]; then
    gradle_command=("$GRADLE_BIN")
else
    gradle_command=("$module_dir/gradlew")
fi
gradle_options=(--console=plain)
if [[ "${RIDER_BUILD_OFFLINE:-0}" == 1 ]]; then gradle_options+=(--offline); fi
if [[ -n "${RIDER_BUILD_INIT_SCRIPT:-}" ]]; then gradle_options+=(-I "$RIDER_BUILD_INIT_SCRIPT"); fi
# Canonical framework projects own gameplay and persistence. Build them locally first.
"${gradle_command[@]}" -p "$repo_dir/SVFrameLib" "${gradle_options[@]}" build 2>&1 | tee "$output_dir/lib-build.log"
"${gradle_command[@]}" -p "$repo_dir/SVFrameMMO" "${gradle_options[@]}" build nativeCoreSmoke 2>&1 | tee "$output_dir/mmo-build.log"
"${gradle_command[@]}" -p "$module_dir" "${gradle_options[@]}" localArtifactPaths 2>&1 | tee "$output_dir/riders-build.log"
python3 - "$module_dir" "$output_dir" <<'PY'
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,xml.etree.ElementTree as ET,zipfile
module,out=map(Path,sys.argv[1:])
artifacts=[]
paths=json.loads((module/'build/local-artifacts.json').read_text())
for name,label in [('core','FantasyHubBridge'),('client','FantasyHubClient')]:
    source=Path(paths[name])
    with zipfile.ZipFile(source) as jar:
        metadata=json.loads(jar.read('fabric.mod.json'))
        if name=='core':
            nested=metadata.get('jars',[])
            if any('SVFrame' in row['file'] for row in nested): raise SystemExit('Bundled Core is not a Bridge distribution')
    target=out/f'{label}-{metadata["version"]}.jar'
    shutil.copy2(source,target)
    artifacts.append({'file':target.name,'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
# These exact canonical builds contain the shared defense and profile/recast API extensions.
# Existing installations must replace them too, even when the base version number matches.
for project,filename in [('SVFrameLib','SVFrameLib-1.7.2-fabric.jar'),('SVFrameMMO','SVFrameMMO-1.13.4-fabric.jar')]:
    source=module.parent/project/'build/libs'/filename
    target=out/filename
    shutil.copy2(source,target)
    artifacts.append({'file':target.name,'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
tests=[]
for project in [module/'core',module/'client',module.parent/'SVFrameLib',module.parent/'SVFrameMMO']:
    for report in (project/'build/test-results/test').glob('TEST-*.xml'):
        suite=ET.parse(report).getroot()
        tests.append({'project':project.name,'suite':suite.attrib['name'],**{k:int(suite.attrib.get(k,0)) for k in ['tests','failures','errors','skipped']}})
        dest=out/'test-results'/project.name;dest.mkdir(parents=True,exist_ok=True);shutil.copy2(report,dest/report.name)
if any(t['failures'] or t['errors'] for t in tests): raise SystemExit('Failing test report; refusing candidate manifest')
manifest={'build':'local','runtimeAcceptance':'NOT_IMPLIED_BY_BUILD','commit':subprocess.check_output(['git','-C',str(module.parent),'rev-parse','HEAD'],text=True).strip(),'dirty':bool(subprocess.check_output(['git','-C',str(module.parent),'status','--porcelain'],text=True)),'artifacts':artifacts,'tests':tests}
(out/'build-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'artifacts':artifacts,'tests':sum(t['tests'] for t in tests),'runtimeAcceptance':'requires separate native QA'},indent=2))
PY
