#!/usr/bin/env python3
"""Bake user-owned Blockbench cube animations into vanilla item_display poses.

Only reads assets; generated resource packs stay local. No ModelEngine server or
client mod is needed. Expressions, mesh elements, and Bezier curves are rejected.
"""
import argparse
import base64
import json
import math
import pathlib
import zipfile


def identity():
    return [[float(i == j) for j in range(4)] for i in range(4)]


def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(v):
    m = identity()
    for i in range(3):
        m[i][3] = v[i]
    return m


def scale(v):
    m = identity()
    for i in range(3):
        m[i][i] = v[i]
    return m


def rotate(v):
    m = identity()
    for axis in range(3):
        r = identity()
        a, b = [(1, 2), (2, 0), (0, 1)][axis]
        c, s = math.cos(math.radians(v[axis])), math.sin(math.radians(v[axis]))
        r[a][a] = r[b][b] = c
        r[a][b], r[b][a] = -s, s
        m = multiply(r, m)
    return m


def vector(raw):
    v = [float(raw.get(k, 0)) for k in ('x', 'y', 'z')]
    if not all(math.isfinite(x) for x in v):
        raise ValueError('Non-finite animation value')
    return v


def sample(keys, channel, t):
    default = [1., 1., 1.] if channel == 'scale' else [0., 0., 0.]
    keys = sorted((k for k in keys if k['channel'] == channel), key=lambda k: k['time'])
    if not keys:
        return default
    if t <= keys[0]['time']:
        return vector(keys[0]['data_points'][-1])
    for i in range(len(keys) - 1):
        a, b = keys[i:i + 2]
        if t > b['time']:
            continue
        va, vb = vector(a['data_points'][-1]), vector(b['data_points'][0])
        f = (t - a['time']) / max(1e-9, b['time'] - a['time'])
        mode = b.get('interpolation', 'linear')
        if mode == 'step':
            return va
        if mode == 'catmullrom':
            p = vector(keys[max(0, i - 1)]['data_points'][-1])
            q = vector(keys[min(len(keys) - 1, i + 2)]['data_points'][0])
            return [.5 * (2 * va[k] + (-p[k] + vb[k]) * f +
                          (2 * p[k] - 5 * va[k] + 4 * vb[k] - q[k]) * f * f +
                          (-p[k] + 3 * va[k] - 3 * vb[k] + q[k]) * f ** 3) for k in range(3)]
        if mode != 'linear':
            raise ValueError('Unsupported interpolation: ' + mode)
        return [va[k] + (vb[k] - va[k]) * f for k in range(3)]
    return vector(keys[-1]['data_points'][-1])


def bake(blueprint, namespace, model_id, first_data):
    elements = {e['uuid']: e for e in blueprint['elements'] if e.get('visibility', True)}
    if len(elements) > 128 or any(e.get('type', 'cube') != 'cube' for e in elements.values()):
        raise ValueError('Only cube models with at most 128 visible cubes are supported')
    groups, parents, cube_parents = {}, {}, {}

    def walk(nodes, parent=None):
        for node in nodes:
            if isinstance(node, str):
                cube_parents[node] = parent
            else:
                groups[node['uuid']], parents[node['uuid']] = node, parent
                walk(node.get('children', []), node['uuid'])
    walk(blueprint['outliner'])
    for cube in elements:
        if cube not in cube_parents:
            raise ValueError('Cube is missing from bone hierarchy')
    textures, files = {}, {}
    for index, texture in enumerate(blueprint['textures']):
        resource = f'{namespace}:textures/item/{model_id}_{index}.png'
        source = texture.get('source', '')
        if not source.startswith('data:image/png;base64,'):
            raise ValueError('Blueprint must contain embedded PNG textures')
        files[f'assets/{namespace}/textures/item/{model_id}_{index}.png'] = base64.b64decode(source.split(',', 1)[1], validate=True)
        textures[str(index)] = resource.replace(':textures/', ':').removesuffix('.png')
    width, height = blueprint['resolution']['width'], blueprint['resolution']['height']
    bones, overrides, factors = [], [], {}
    for index, (uid, cube) in enumerate(elements.items()):
        origin = cube.get('origin', [0, 0, 0])
        extent = max(abs(float(cube[k][i]) - origin[i]) for k in ('from', 'to') for i in range(3))
        factor = max(1., extent / 23.)
        factors[uid] = factor
        faces = {}
        for face, raw in cube.get('faces', {}).items():
            if raw.get('texture') is None:
                continue
            uv = raw['uv']
            faces[face] = {'uv': [uv[i] * 16 / (width if i % 2 == 0 else height) for i in range(4)],
                           'texture': '#' + str(raw['texture']), 'rotation': raw.get('rotation', 0)}
        geometry = {k: [(float(cube[k][i]) - origin[i]) / factor + 8 for i in range(3)] for k in ('from', 'to')}
        geometry['faces'] = faces
        resource = f'{namespace}:{model_id}/cube_{index}'
        # Untextured helper cubes are animation anchors, not drawable geometry.
        # Preserve their identity/poses but emit an empty model: vanilla rejects
        # an element with no faces, even though Blockbench accepts it.
        files[f'assets/{namespace}/models/{model_id}/cube_{index}.json'] = json.dumps({'textures': textures, 'elements': [geometry] if faces else []}).encode()
        data = first_data + index
        overrides.append({'predicate': {'custom_model_data': data}, 'model': resource})
        bones.append({'item': 'minecraft:paper', 'custom-model-data': data, 'frames': []})
    animations = blueprint.get('animations', []) or [{'name': 'idle', 'length': .05, 'animators': {}}]
    models, anchors = {}, {}
    for animation in animations:
        if animation.get('anim_time_update') or animation.get('start_delay') or animation.get('loop_delay'):
            raise ValueError('Animation expressions/delays require manual porting')
        duration = max(1, math.ceil(float(animation['length']) * 20))
        if duration > 240:
            raise ValueError('Animation exceeds 12 seconds')
        pose_bones = [{'item': b['item'], 'custom-model-data': b['custom-model-data'], 'frames': []} for b in bones]
        pose_anchors = {group['name']: [] for group in groups.values() if group.get('name', '').startswith('p') and group.get('name', '')[1:].isdigit()}
        for tick in range(duration + 1):
            cache = {}
            def group_matrix(uid):
                if uid is None:
                    return rotate([0, 180, 0])
                if uid in cache:
                    return cache[uid]
                group, parent = groups[uid], parents[uid]
                pivot = group.get('origin', [0, 0, 0])
                parent_pivot = groups[parent].get('origin', [0, 0, 0]) if parent else [0, 0, 0]
                keys = animation.get('animators', {}).get(uid, {}).get('keyframes', [])
                position = sample(keys, 'position', tick / 20)
                rotation = sample(keys, 'rotation', tick / 20)
                resting = group.get('rotation', [0, 0, 0])
                local = multiply(translate([(pivot[i] - parent_pivot[i] + position[i]) / 16 for i in range(3)]),
                                 multiply(rotate([resting[i] + rotation[i] for i in range(3)]), scale(sample(keys, 'scale', tick / 20))))
                cache[uid] = multiply(group_matrix(parent), local)
                return cache[uid]
            for uid, group in groups.items():
                if group.get('name') in pose_anchors:
                    matrix = group_matrix(uid)
                    pose_anchors[group['name']].append([round(matrix[i][3], 6) for i in range(3)])
            for index, (uid, cube) in enumerate(elements.items()):
                parent = cube_parents[uid]
                pivot = groups[parent].get('origin', [0, 0, 0]) if parent else [0, 0, 0]
                origin = cube.get('origin', [0, 0, 0])
                local = multiply(translate([(origin[i] - pivot[i]) / 16 for i in range(3)]),
                                 multiply(rotate(cube.get('rotation', [0, 0, 0])), scale([factors[uid]] * 3)))
                matrix = multiply(group_matrix(parent), local)
                pose_bones[index]['frames'].append([round(matrix[i][j], 6) for j in range(4) for i in range(4)])
        models[f'{model_id}@{animation["name"]}'] = pose_bones
        anchors[f'{model_id}@{animation["name"]}'] = pose_anchors
    # A plain model ID plays the first animation.
    models[model_id] = next(iter(models.values()))
    anchors[model_id] = next(iter(anchors.values()))
    files[f'assets/{namespace}/anchors/{model_id}.json'] = json.dumps(anchors).encode()
    return models, files, overrides


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('blueprints', type=pathlib.Path)
    parser.add_argument('--model', action='append', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--sounds-assets', type=pathlib.Path, help='Optional original resourcepack/assets directory; copies only sounds.json and OGG files')
    args = parser.parse_args()
    files, models, overrides, data = {}, {}, [], 200000
    for name in args.model:
        if not name or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789_' for c in name):
            parser.error('Invalid model name')
        blueprint = json.loads((args.blueprints / (name + '.bbmodel')).read_text())
        imported, resources, predicates = bake(blueprint, 'svframe_imported', name, data)
        models.update(imported); files.update(resources); overrides.extend(predicates); data += len(predicates)
    files['assets/minecraft/models/item/paper.json'] = json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/paper'}, 'overrides': overrides}).encode()
    files['pack.mcmeta'] = json.dumps({'pack': {'pack_format': 34, 'description': 'SVFrame local imported class visuals'}}).encode()
    if args.sounds_assets:
        for path in args.sounds_assets.rglob('*'):
            if path.is_file() and not path.is_symlink() and (path.name == 'sounds.json' or path.suffix == '.ogg'):
                relative = path.relative_to(args.sounds_assets).as_posix()
                files['assets/' + relative] = path.read_bytes()
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'reference-models.json').write_text(json.dumps(models, separators=(',', ':')))
    anchors = {}
    for name, content in files.items():
        if '/anchors/' in name:
            anchors.update(json.loads(content))
    (args.output / 'reference-anchors.json').write_text(json.dumps(anchors, separators=(',', ':')))
    with zipfile.ZipFile(args.output / 'SVFrameReferenceVisuals-1.21.1.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, value in sorted(files.items()):
            archive.writestr(name, value)
    print(f'Imported {len(args.model)} blueprints, {len(overrides)} cubes, {len(models)} animation IDs into {args.output}')


if __name__ == '__main__':
    main()
