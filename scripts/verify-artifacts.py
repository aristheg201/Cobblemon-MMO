#!/usr/bin/env python3
"""Reject stale metadata and mismatched platform requirements after a local build."""
from pathlib import Path
import hashlib,json,re,zipfile
root=Path(__file__).resolve().parents[1]
for module in ['SVFrameLib','SVFrameMMO','SVFrameItems','SVFrameMMOCobblemon','SVFrameMobs']:
    directory=root/module
    if not directory.is_dir(): continue
    build=(directory/'build.gradle').read_text()
    properties=dict(line.split('=',1) for line in (directory/'gradle.properties').read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
    match=re.search(r"version\s*=\s*['\"]([^'\"]+)['\"]",build)
    version=match.group(1) if match else properties['mod_version']
    archive=re.search(r"archivesName\s*=\s*['\"]([^'\"]+)['\"]",build).group(1)
    jar=directory/'build/libs'/f'{archive}-{version}.jar'
    with zipfile.ZipFile(jar) as zipped: metadata=json.loads(zipped.read('fabric.mod.json'))
    assert metadata['version']==version,(jar,'stale version',metadata['version'],version)
    assert metadata['depends']['minecraft']=='1.21.1',(jar,'Minecraft must be exactly 1.21.1')
    assert metadata['depends']['fabricloader']=='>=0.18.4',(jar,'Fabric Loader requirement')
    assert metadata['environment']=='*',(jar,'integrated singleplayer entrypoint filtered out')
    print(f'{jar.name}: verified; sha256={hashlib.sha256(jar.read_bytes()).hexdigest()}')
