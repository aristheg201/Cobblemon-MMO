#!/usr/bin/env python3
"""Install the two supplied class configs into a local Fabric server directory.

Requires PyYAML. Existing destination files are preserved unless --replace is used.
Only class definitions and their fourteen skill records are imported, never player
data, server credentials, Bukkit plugins, or unrelated packs.
"""
import argparse
import pathlib
import yaml


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('config2', type=pathlib.Path)
    parser.add_argument('amk', type=pathlib.Path)
    parser.add_argument('server', type=pathlib.Path)
    parser.add_argument('--replace', action='store_true')
    args = parser.parse_args()
    skills = {}
    for root in (args.config2, args.amk):
        for path in sorted((root / 'MythicLib/skill').glob('*.yml')):
            for name, definition in (yaml.safe_load(path.read_text()) or {}).items():
                if name.startswith(('CLS_DEATH_KNIGHT_', 'CLS_ANTI_MAGE_KNIGHT_')):
                    skills[name] = definition
    classes = {}
    for name, root in [('death_knight', args.config2), ('anti_mage_knight', args.amk)]:
        path = root / 'MMOCore/classes' / (name + '.yml')
        definition = yaml.safe_load(path.read_text())
        for skill in definition['skills']:
            if skill not in skills:
                parser.error('Missing class skill: ' + skill)
        classes[name] = definition
    if len(skills) != 14:
        parser.error('Expected exactly fourteen reference skills')
    for name, definition in skills.items():
        definition['source'] = 'native:' + name.removeprefix('CLS_')
        parameters = definition['parameters']
        if name.endswith(('CURSED_SEAL', 'PASSIVE')):
            # Refresh a short native passive window; avoid zero-delay timer loops.
            parameters['timer'] = {'player': {'base': 20, 'per-level': 0}, 'item': 20}
        if name == 'CLS_ANTI_MAGE_KNIGHT_HIT_ME':
            definition['name'] = 'Indomitable Mockery (jump slam)'
        if name == 'CLS_ANTI_MAGE_KNIGHT_OVERLOAD':
            definition['name'] = 'Hit Me / Anti Magic Overload (taunt guard)'
    outputs = {args.server / 'config/SVFrameLib/skill/reference_classes.yml': skills}
    for name, definition in classes.items():
        outputs[args.server / 'config/SVFrameMMO/classes' / (name + '.yml')] = definition
    collisions = [str(path) for path in outputs if path.exists()]
    if collisions and not args.replace:
        parser.error('Existing files retained; use --replace to replace: ' + ', '.join(collisions))
    for path, definition in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(yaml.safe_dump(definition, sort_keys=False, allow_unicode=True))
        print(path)


if __name__ == '__main__':
    main()
