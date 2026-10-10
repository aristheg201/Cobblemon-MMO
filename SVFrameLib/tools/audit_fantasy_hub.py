#!/usr/bin/env python3
"""Inventory owned source packs without executing plugins or exporting private userdata.

Requires PyYAML. Source hashes and locations are retained; migration coverage is
explicitly separate from discovery. Never treat a parsed configuration as an
implemented gameplay feature.
"""
import argparse
import collections
import hashlib
import io
import json
import pathlib
import re
import tarfile
import zipfile

import yaml

PRIVATE = {'userdata', 'playerdata', 'players', 'sessions', 'session', 'logs',
           'backups', '.archive-unpack', 'tmp', 'libraries', 'libs', 'lib',
           'database', 'yaml-storage', 'data_storage', 'cache', 'data'}
SOURCE_PLUGINS = {'MMOCore', 'MMOItems', 'MythicLib', 'MythicMobs', 'ModelEngine',
                  'MCPets', 'Skript', 'ItemsAdder', 'DeluxeMenus', 'MyCommand',
                  'BetonQuest', 'Quests', 'Citizens', 'Shopkeepers', 'ShopGUIPlus',
                  'DungeonsXL', 'Denizen', 'AdvancedEnchantments', 'BattlePass',
                  'AuraSkills', 'HungerIsStamina', 'Waypoints', 'nwMMOUpgrade'}
EXTENSIONS = {'.yml', '.yaml', '.sk', '.bbmodel', '.png', '.ogg', '.json', '.dsc'}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def location(path, root, line=None):
    value = str(path.relative_to(root))
    return value if line is None else f'{value}:{line}'


def extract_sources(root):
    inventory = []
    plugins = []
    for archive in sorted(root.glob('*.tar')):
        counts = collections.Counter()
        extracted = 0
        with tarfile.open(archive) as stream:
            for member in stream:
                if not member.isfile():
                    continue
                p = pathlib.PurePosixPath(member.name)
                if p.is_absolute() or '..' in p.parts:
                    raise ValueError(f'Unsafe archive member: {member.name}')
                counts[p.parts[0]] += 1
                if archive.name == 'plugins.tar' and p.suffix == '.jar':
                    raw = stream.extractfile(member).read()
                    with zipfile.ZipFile(io.BytesIO(raw)) as z:
                        descriptor = next((n for n in ('plugin.yml', 'paper-plugin.yml', 'fabric.mod.json') if n in z.namelist()), None)
                        d = yaml.safe_load(z.read(descriptor)) if descriptor else {}
                    plugins.append({'file': member.name, 'sha256': sha(raw),
                                    'name': d.get('name', d.get('id')), 'version': str(d.get('version')),
                                    'framework': 'Fabric' if descriptor == 'fabric.mod.json' else 'Bukkit/Paper',
                                    'dependencies': {k: d[k] for k in ('depend', 'softdepend', 'loadbefore', 'api-version') if k in d}})
                elif (p.parts[0] in SOURCE_PLUGINS and p.suffix.lower() in EXTENSIONS
                      and not PRIVATE.intersection(part.lower() for part in p.parts[1:-1])):
                    destination = root / archive.stem / p
                    if not destination.exists():
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        destination.write_bytes(stream.extractfile(member).read())
                        extracted += 1
        digest = hashlib.sha256()
        with archive.open('rb') as f:
            for block in iter(lambda: f.read(1024 * 1024), b''):
                digest.update(block)
        inventory.append({'file': archive.name, 'bytes': archive.stat().st_size,
                          'sha256': digest.hexdigest(), 'files': sum(counts.values()),
                          'plugin_folders': dict(sorted(counts.items())), 'new_source_files': extracted})
    return inventory, plugins


def yaml_records(paths, root, errors, duplicates):
    records = []
    for path in sorted(paths):
        try:
            text = path.read_text(encoding='utf-8-sig')
            node = yaml.compose(text)
            data = yaml.safe_load(text) or {}
            if not isinstance(data, dict) or not isinstance(node, yaml.MappingNode):
                continue
            occurrences = collections.Counter(k.value for k, v in node.value)
            for key, count in occurrences.items():
                if count > 1:
                    duplicates.append({'source': location(path, root), 'id': key, 'occurrences': count})
            lines = {k.value: k.start_mark.line + 1 for k, v in node.value}
            for key, value in data.items():
                if isinstance(value, dict):
                    records.append({'id': str(key), 'source': location(path, root, lines.get(str(key))),
                                    'definition': value})
        except (UnicodeError, yaml.YAMLError, OSError) as ex:
            mark = getattr(ex, 'problem_mark', None)
            errors.append({'source': location(path, root), 'line': mark.line + 1 if mark else None,
                           'error': getattr(ex, 'problem', type(ex).__name__)})
    return records


def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for v in value.values():
            yield from strings(v)
    elif isinstance(value, list):
        for v in value:
            yield from strings(v)


def audit(root, archives=(), plugins=()):
    errors, duplicates = [], []
    classes, skilldefs, mythic, mobs, scripts, assets, misc = [], [], [], [], [], [], []
    equipment, attributes, spawners, drops, guis, quests, npc_files = [], [], [], [], [], [], []
    for pack in sorted(p for p in root.iterdir() if p.is_dir()):
        for path in sorted((pack / 'MMOCore/classes').glob('*.yml')):
            d = yaml.safe_load(path.read_text(encoding='utf-8-sig')) or {}
            classes.append({'id': path.stem.upper(), 'source': location(path, root),
                            'display': d.get('display', {}), 'attributes': d.get('attributes', {}),
                            'skills': d.get('skills', {}), 'skill_slots': d.get('skill-slots', {}),
                            'unlock_rules': {k: v for k, v in d.items() if any(s in k for s in ('unlock', 'condition', 'parent', 'subclass', 'permission', 'option'))},
                            'progression': {k: d[k] for k in ('max-level', 'exp-curve', 'exp-table', 'skill-trees', 'triggers', 'resources') if k in d},
                            'status': 'discovered; selection and complete ability parity unverified'})
        skilldefs.extend(yaml_records((pack/'MythicLib/skill').glob('*.yml'), root, errors, duplicates))
        equipment.extend(yaml_records((pack/'MMOItems/item').glob('*.yml'), root, errors, duplicates))
        attributes.extend(yaml_records((pack/'MMOCore/attributes').glob('*.yml'), root, errors, duplicates))
        for folder, target in [('spawners', spawners), ('RandomSpawns', spawners), ('DropTables', drops)]:
            files = [p for p in (pack/'MythicMobs').rglob('*.yml') if folder.lower() in (s.lower() for s in p.parts)]
            target.extend(yaml_records(files, root, errors, duplicates))
        for folder, target in [('Skills', mythic), ('Mobs', mobs)]:
            files = [p for p in (pack/'MythicMobs').rglob('*.yml') if folder.lower() in (s.lower() for s in p.parts)]
            target.extend(yaml_records(files, root, errors, duplicates))
        for path in sorted((pack / 'Skript/scripts').rglob('*.sk')):
            text = path.read_text(encoding='utf-8-sig')
            headers, syntax = [], collections.Counter()
            for line, raw in enumerate(text.splitlines(), 1):
                statement = raw.strip()
                if not statement or statement.startswith('#'):
                    continue
                if not raw[0].isspace():
                    headers.append({'line': line, 'syntax': statement})
                syntax[re.sub(r'"[^"\n]*"|\{[^}\n]*\}|\b\d+(?:\.\d+)?\b', '<value>', statement)] += 1
            disabled = any(part.startswith('-') for part in path.relative_to(pack/'Skript/scripts').parts)
            scripts.append({'source': location(path, root), 'sha256': sha(path.read_bytes()),
                            'enabled': not disabled, 'headers': headers, 'constructs': dict(sorted(syntax.items())),
                            'variables': sorted(set(re.findall(r'\{([^{}]+)\}', text))),
                            'lines': len(text.splitlines()), 'executed_on_fabric': False})
        for path in sorted(pack.rglob('*')):
            if not path.is_file() or PRIVATE.intersection(s.lower() for s in path.relative_to(pack).parts[:-1]):
                continue
            if path.suffix.lower() in {'.bbmodel', '.png', '.ogg', '.json'}:
                record = {'source': location(path, root), 'bytes': path.stat().st_size,
                          'sha256': sha(path.read_bytes()), 'kind': path.suffix[1:]}
                if path.suffix == '.bbmodel':
                    try:
                        model = json.loads(path.read_text())
                        record.update({'name': model.get('name', path.stem),
                                       'elements': len(model.get('elements', [])),
                                       'animations': [a.get('name') for a in model.get('animations', [])],
                                       'formats': dict(collections.Counter(e.get('type', 'cube') for e in model.get('elements', [])))})
                    except (UnicodeError, ValueError):
                        record['parse_error'] = True
                assets.append(record)
            elif path.suffix in {'.yml', '.yaml', '.dsc'}:
                misc.append({'source': location(path, root), 'sha256': sha(path.read_bytes())})
                relative = path.relative_to(pack)
                parts = {p.lower() for p in relative.parts}
                if parts.intersection({'gui', 'guis', 'gui_menus', 'menus'}) or relative.parts[0] == 'DeluxeMenus':
                    guis.append(misc[-1])
                if relative.parts[0] in {'Quests', 'BetonQuest', 'DungeonsXL', 'Denizen'} or 'quests' in parts:
                    quests.append(misc[-1])
                if relative.parts[0] in {'Citizens', 'Shopkeepers'}:
                    npc_files.append(misc[-1])
    wrappers = collections.defaultdict(list)
    metas = collections.defaultdict(list)
    for record in skilldefs:
        wrappers[record['id']].append(record)
    for record in mythic:
        metas[record['id']].append(record)
    classskills = sorted({s for c in classes for s in c['skills']})
    missing = [{'id': s, 'expected': 'MythicLib/skill/*.yml', 'referenced_by': [c['source'] for c in classes if s in c['skills']]} for s in classskills if s not in wrappers]
    mechanics, targeters, conditions, triggers = (collections.Counter() for _ in range(4))
    references = []
    for record in mythic + mobs:
        for field, value in record['definition'].items():
            for text in strings(value):
                if field.lower().endswith('conditions'):
                    m = re.match(r'([\w:]+)', text)
                    if m:
                        conditions[m[1].lower()] += 1
                elif field.lower() == 'skills':
                    m = re.match(r'([\w:]+)', text)
                    if m:
                        mechanics[m[1].lower()] += 1
                    targeters.update(s.lower() for s in re.findall(r'@([\w]+)', text))
                    triggers.update(s.lower() for s in re.findall(r'~([\w:]+)', text))
                    for ref in re.findall(r'\bskill\s*\{[^}]*?\b(?:s|skill)\s*=\s*([^;}\s]+)', text, flags=re.I):
                        if ref != '[' and '<' not in ref and ref not in metas:
                            references.append({'source': record['source'], 'owner': record['id'], 'missing_meta_skill': ref})
    # Wrappers are aliases/configuration, not executable implementations.
    for record in skilldefs:
        record['status'] = 'configuration discovered; executable parity unverified'
    for record in mythic + mobs:
        record['status'] = 'configuration discovered; executable parity unverified'
    counts = {'class_definitions': len(classes), 'unique_classes': len({c['id'] for c in classes}),
              'class_skill_ids': len(classskills), 'skill_wrapper_definitions': len(skilldefs),
              'unique_skill_wrappers': len(wrappers), 'mythic_meta_skill_definitions': len(mythic),
              'unique_mythic_meta_skills': len(metas), 'mob_definitions': len(mobs),
              'unique_mobs': len({r['id'] for r in mobs}), 'scripts': len(scripts),
              'enabled_scripts': sum(s['enabled'] for s in scripts),
              'source_models': sum(a['kind']=='bbmodel' for a in assets),
              'textures': sum(a['kind']=='png' for a in assets), 'sounds': sum(a['kind']=='ogg' for a in assets),
              'equipment_definitions': len(equipment), 'attribute_definitions': len(attributes),
              'spawner_definitions': len(spawners), 'drop_table_definitions': len(drops),
              'gui_configuration_files': len(guis), 'quest_configuration_files': len(quests),
              'npc_configuration_files': len(npc_files),
              'yaml_parse_errors': len(errors), 'duplicate_yaml_keys': len(duplicates)}
    return {'schema': 1, 'platform': {'minecraft':'1.21.1', 'fabric_loader':'>=0.18.4', 'java':21, 'cobblemon':'1.8.x'},
            'coverage_policy': 'Discovered and parsed are not implemented. No complete-parity claim.',
            'counts': counts, 'archives': archives, 'plugins': plugins,
            'classes': classes, 'class_skill_ids': classskills, 'skill_wrappers': skilldefs,
            'mythic_meta_skills': mythic, 'mobs': mobs, 'scripts': scripts, 'assets': assets,
            'equipment': equipment, 'attribute_definitions': attributes, 'spawners': spawners,
            'drop_tables': drops, 'gui_sources': guis, 'quest_sources': quests, 'npc_sources': npc_files,
            'additional_configuration_files': misc,
            'constructs': {k: dict(sorted(v.items())) for k,v in [('mechanics',mechanics),('targeters',targeters),('conditions',conditions),('triggers',triggers)]},
            'blockers': {'missing_class_wrappers': missing, 'missing_mythic_references': references,
                         'yaml_errors': errors, 'duplicate_yaml_keys': duplicates,
                         'art_direction_images': 'Five requested images are absent from conversation; source location requested.',
                         'reference_baseline': 'No running original Bukkit/Paper environment supplied for differential conformance.',
                         'skript': 'Bukkit/addon classes require native equivalent execution; scripts have not passed Fabric conformance.'}}


def write_report(manifest, path):
    counts = manifest['counts']
    lines = ['# Fantasy Hub source-to-Fabric migration manifest', '',
             'This inventory is generated from the supplied archives. Discovery is separate from executable coverage.', '',
             '| Source category | Count |', '| --- | ---: |']
    lines.extend(f'| {k.replace("_", " ")} | {v} |' for k,v in counts.items())
    lines.extend(['', '| Class | Source file | Skills | Execution status |', '| --- | --- | ---: | --- |'])
    lines.extend(f'| {c["id"]} | `{c["source"]}` | {len(c["skills"])} | Not fully verified |' for c in manifest['classes'])
    lines.extend(['', '## Integration and migration requirements', '',
                  'The JSON manifest records each class growth curve, slots, unlock metadata, skill wrapper and effect graph, mob definition, script constructs/variables, model animations, textures, sounds, and additional menu, quest, NPC, economy and equipment source locations.', '',
                  'All source plugin JAR descriptors identify their original framework and dependencies. Bukkit/Paper APIs are not assumed available on Fabric.', '',
                  'No supplied script is marked executed by loading or parsing alone. Current migration coverage must be updated only from actual implementation and runtime evidence.', '',
                  'Private player databases, logs, caches and credentials are excluded. Vendor source assets remain in the local owned-input directory.', '',
                  '## Unresolved source references', '',
                  f'- Missing class wrappers: {len(manifest["blockers"]["missing_class_wrappers"])}.',
                  f'- Missing static Mythic meta-skill references: {len(manifest["blockers"]["missing_mythic_references"])} (dynamic placeholders excluded).',
                  f'- YAML parse errors: {len(manifest["blockers"]["yaml_errors"])}.',
                  f'- Duplicate top-level YAML keys: {len(manifest["blockers"]["duplicate_yaml_keys"])}; precedence requires explicit review.',
                  '- Five art-direction images were not attached; their location was requested.',
                  '- A running original environment is unavailable for differential Skript conformance.', ''])
    path.write_text('\n'.join(lines))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('source', type=pathlib.Path)
    p.add_argument('output', type=pathlib.Path)
    p.add_argument('--extract', action='store_true')
    args = p.parse_args()
    archives, plugins = extract_sources(args.source) if args.extract else ([], [])
    manifest = audit(args.source, archives, plugins)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, default=str)+'\n')
    write_report(manifest, args.output.with_suffix('.md'))
    print(json.dumps(manifest['counts'], indent=2))


if __name__ == '__main__':
    main()
