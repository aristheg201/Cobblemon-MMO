#!/usr/bin/env python3
"""Import the pinned, user-requested packs for the private client distribution.

No mod binaries are embedded. Sources remain outside version control.
"""
import argparse
import hashlib
import json
import re
import shutil
import zipfile
from pathlib import Path, PurePosixPath

PACKS = {
    'detailed_animations': {
        'file': 'DetailedAnimationsReworked - V1.15.zip',
        'version': 'dFBidLNv', 'project': 'detailed-animations',
        'sha512': '6ac0875c11908819d05680529e450bc91195a68894c94478775529ba7ca182775597cb64ed417fc3c9832c8bd23e06f6e19da2cffe0dd7799a81815ce1653fed',
        'requires': ['entity_model_features', 'entity_texture_features'],
    },
    'e19_minimap_icons': {
        'file': 'E19 Cobblemon Minimap Icons.zip',
        'version': 'MAumPyhV', 'project': 'e19_cobblemon_minimap_icons',
        'sha512': 'f9eff4c718818a2b030aa7b6b86b73f384f869cb39fe1e4c189c07f8feb7aedd717a33d966face570ae89b67ee0c28c35fb611883b882677eecf0fa041b9088f',
        'requires': ['xaerominimap', 'cobblemon'],
    },
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archives', required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1] / 'client/src/imported/resources'
    records = []
    for name, spec in PACKS.items():
        archive = args.archives / spec['file']
        actual = hashlib.sha512(archive.read_bytes()).hexdigest()
        if actual != spec['sha512']:
            raise ValueError(f'Archive checksum mismatch: {archive.name}')
        target = root / 'resourcepacks' / name
        with zipfile.ZipFile(archive) as zipped:
            files = [i for i in zipped.infolist() if not i.is_dir()]
            if len(files) > 16000 or sum(i.file_size for i in files) > 64_000_000:
                raise ValueError('Pack exceeds import bounds')
            for entry in files:
                path = PurePosixPath(entry.filename)
                if path.is_absolute() or '..' in path.parts or '\\' in entry.filename or entry.file_size > 8_000_000:
                    raise ValueError(f'Unsafe archive entry: {entry.filename}')
                if path.parts[0] not in ('assets', 'combee_mc_remodel', 'shiny_gholdengo_fix') and entry.filename not in ('pack.mcmeta', 'pack.png'):
                    raise ValueError(f'Unexpected pack entry: {entry.filename}')
            # Validate the entire archive before touching the existing imported pack.
            if target.exists():
                shutil.rmtree(target)
            for entry in files:
                # E19 ships legacy Cobblemon model/resolver replacements too. The
                # requested minimap integration must not replace 1.8's live models.
                if name == 'e19_minimap_icons' and not (entry.filename.startswith('assets/xaerominimap/') or entry.filename in ('pack.mcmeta', 'pack.png')):
                    continue
                output = target / entry.filename
                output.parent.mkdir(parents=True, exist_ok=True)
                data = zipped.read(entry)
                if name == 'detailed_animations' and entry.filename.endswith('.jem'):
                    # Preserve the animation equations while expressing booleans
                    # explicitly for EMF 3's typed ASM compiler.
                    text = data.decode('utf-8')
                    text = re.sub(r'(?<![\w.])(is_\w+)\s*<\s*1\b', r'!\1', text)
                    text = re.sub(r'(?<![\w.])(is_\w+)\s*\*\s*1\b', r'if(\1, 1, 0)', text)
                    # A repeated render in one tick legitimately has frame_time=0.
                    # Prevent 0/0 poisoning every downstream body transform.
                    text = text.replace('/ (var.frame_time)', '/ max(var.frame_time, 0.0001)')
                    text = text.replace('/ var.HeightVelocity', '/ if(abs(var.HeightVelocity) > 0.0001, var.HeightVelocity, 0.0001)')
                    text = text.replace('/ var.SprintingVactor', '/ max(var.SprintingVactor, 1)')
                    data = text.encode('utf-8')
                output.write_bytes(data)
        records.append({'id': name, **spec, 'sourceFiles': len(files),
                        'files': sum(p.is_file() for p in target.rglob('*')),
                        'adaptation': 'minimap namespace only; retain current Cobblemon models' if name == 'e19_minimap_icons' else 'explicit boolean casts for EMF 3 ASM; guard zero-frame velocity divisions and initial sprint divisor',
                        'url': f"https://modrinth.com/resourcepack/{spec['project']}/version/{spec['version']}"})
        print(name, len(files), 'verified files')
    manifest = root / 'assets/fantasyhub/client-hud-packs.json'
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps({'distribution': 'INTERNAL_ONLY', 'embeddedModBinaries': False,
                                   'packs': records}, indent=2) + '\n')

if __name__ == '__main__':
    main()
