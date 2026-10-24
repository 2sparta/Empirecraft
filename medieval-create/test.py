#!/usr/bin/env python3
"""Offline checks for the actual patched JAR, survival progression and ADM preservation."""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
from build import NAME, SOURCE_SHA256, VERSION
from policy import allowed, recipe_reason, DISABLED_TYPES
from update_adm import parse, prepare, expected, integrity

ROOT = Path(__file__).resolve().parent

def result_ids(recipe):
    result = recipe.get('result', recipe.get('results', []))
    if isinstance(result, str): return [result]
    if isinstance(result, dict): result = [result]
    return [r['item'] for r in result if 'item' in r]

def ingredients(recipe):
    if 'key' in recipe: return list(recipe['key'].values())
    return recipe.get('ingredients', [recipe['ingredient']] if 'ingredient' in recipe else [])

def check_jar(source, target):
    assert hashlib.sha256(source.read_bytes()).hexdigest() == SOURCE_SHA256
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(target) as new:
        assert new.testzip() is None
        assert len(new.namelist()) == len(set(new.namelist()))
        assert all(name in new.namelist() for name in old.namelist() if name.endswith('.class'))
        for name in old.namelist():
            if name.startswith('META-INF/jarjar/') and not name.endswith('/'):
                assert new.read(name) == old.read(name), name
        recipes = [json.loads(new.read(n)) for n in new.namelist() if n.startswith('data/') and '/recipes/' in n and n.endswith('.json')]
        assert all(recipe_reason(r) is None for r in recipes)
        assert not any(r['type'] in DISABLED_TYPES for r in recipes)
        assert all(allowed(item) for r in recipes for item in result_ids(r))
        kept = set(new.read('empirecraft/medieval-create-allowed.txt').decode().splitlines())
        needed = {'create:' + name for name in '''andesite_alloy andesite_casing shaft cogwheel large_cogwheel
rope_pulley mechanical_piston sticky_mechanical_piston piston_extension_pole gantry_shaft gantry_carriage
linear_chassis secondary_linear_chassis radial_chassis mechanical_bearing windmill_bearing water_wheel
large_water_wheel hand_crank white_sail sail_frame clutch gearshift gearbox vertical_gearbox
encased_chain_drive adjustable_chain_gearshift sequenced_gearshift super_glue wrench
empty_schematic schematic_and_quill schematic_table crafting_blueprint contraption_controls redstone_contact'''.split()}
        assert needed | {'create:schematic'} <= kept
        assert not {'create:steam_engine', 'create:potato_cannon', 'create:schematicannon', 'create:encased_fan',
                    'create:creative_motor', 'create:track', 'create:deployer'} & kept
        # Resolve Create's item tags as alternatives, then grow a graph from vanilla resources.
        tags = {}
        for name in new.namelist():
            if name.startswith('data/') and '/tags/items/' in name and name.endswith('.json'):
                namespace, suffix = name[5:].split('/tags/items/', 1)
                tags[namespace + ':' + suffix[:-5]] = json.loads(new.read(name)).get('values', [])
        # Create zinc is obtained by mining its retained world-generation ore, then normal smelting.
        available = {'create:raw_zinc'}
        def obtainable(value, seen=None):
            if isinstance(value, list): return any(obtainable(v, seen) for v in value)
            if 'item' in value:
                item = value['item']
                return not item.startswith('create:') or item in available
            if 'tag' not in value: return False
            tag = value['tag']; seen = set(seen or ())
            if tag in seen: return False
            seen.add(tag)
            if tag not in tags: return not tag.startswith('create:')
            def tag_value(v):
                if isinstance(v, dict): v = v['id']
                return {'tag': v[1:]} if v.startswith('#') else {'item': v}
            return any(obtainable(tag_value(v), seen) for v in tags[tag])
        # A working press, mill or saw is needed before their processing recipes can unlock resources.
        machines = {'create:pressing': 'create:mechanical_press', 'create:milling': 'create:millstone', 'create:cutting': 'create:mechanical_saw'}
        for _ in range(100):
            before = len(available)
            for recipe in recipes:
                typ = recipe['type']
                if typ in machines and machines[typ] not in available: continue
                if all(obtainable(value) for value in ingredients(recipe)):
                    available.update(result_ids(recipe))
            if len(available) == before: break
        assert needed <= available, 'Unavailable gate/blueprint components: ' + str(sorted(needed - available))
        print('JAR: CRC, embedded dependencies, forbidden recipes and gate/blueprint progression passed.')

def check_kubejs():
    path = ROOT / 'dist/overlay/kubejs/server_scripts/zz_medieval_create.js'
    node = shutil.which('node')
    if not node: raise RuntimeError('Node is required to check the generated server script.')
    source = '''const fs = require('fs'), vm = require('vm');
const removed = [];
vm.runInNewContext(fs.readFileSync(process.argv[1], 'utf8'), {ServerEvents: {recipes: f => f({remove: filter => removed.push(filter)})}});
for (const id of ['create:steam_engine','create:potato_cannon','create:schematicannon','create:encased_fan']) {
  if (!removed.some(f => f.output === id) || !removed.some(f => f.input === id)) throw Error('Missing restriction: ' + id);
}
if (!removed.some(f => f.type === 'create:splashing')) throw Error('Splashing enabled');
for (const id of ['create:rope_pulley','create:gantry_carriage','create:schematic_and_quill','create:schematic_table'])
  if (removed.some(f => f.output === id)) throw Error('Required component disabled: ' + id);
const milling = removed.find(f => f.type === 'create:milling' && f.output && typeof f.output.test === 'function');
if (!milling || !milling.output.test('minecraft:iron_nugget') || milling.output.test('create:wheat_flour')) throw Error('Milling rule failed');
'''
    subprocess.run([node, '-e', source, str(path)], check=True)
    print('KubeJS: script loads, industrial recipes removed, required components retained.')

def check_adm(jar):
    sample = dict(osName='Test OS', cpuInfo='CPU', ramInfo='RAM', gpuInfo='GPU', anticheatVersion='1.9.3', lastUpdated=1,
                  modIds=[dict(id='example', version='1.0', sha256='a'*64, filePresent=True),
                          dict(id='mixinextras', version='0.4.1', sha256=None, filePresent=False)],
                  resourcePackIds=[dict(filename='pack.zip', sha256='b'*64)])
    sample['integrityHash'] = integrity(sample)
    assert parse(json.dumps(sample)) == sample
    labels = [('OS','osName'),('CPU','cpuInfo'),('RAM','ramInfo'),('GPU','gpuInfo'),('Anticheat Version','anticheatVersion'),('Last Updated','lastUpdated'),('Integrity Hash','integrityHash')]
    styled = '[System Info]\n' + ''.join('  '+label+': '+str(sample[key])+'\n' for label,key in labels)
    styled += '[Mod IDs]\n' + ''.join('  - {} @ {} [{}] {{{}}}\n'.format(e['id'], e['version'], e['sha256'] or '', 'file' if e['filePresent'] else 'nofile') for e in sample['modIds'])
    styled += '[Resource Pack IDs]\n  - pack.zip ['+'b'*64+']\n'
    assert parse(styled) == sample
    before = copy.deepcopy(sample)
    new = prepare(sample, expected(jar))
    assert sample == before
    assert new['resourcePackIds'] == before['resourcePackIds']
    assert all(e in new['modIds'] for e in before['modIds'])
    assert integrity(new) == new['integrityHash']
    assert {e['id'] for e in new['modIds']} == {'example','mixinextras','create','flywheel','ponder'}
    bad = copy.deepcopy(sample); bad['modIds'][0]['sha256'] = 'c'*64
    try: parse(json.dumps(bad))
    except ValueError: pass
    else: raise AssertionError('Corrupt ADM list was accepted')
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory) / 'adm.json'
        target.write_text(styled)
        subprocess.run(['python3', str(ROOT/'update_adm.py'), '--id-file', str(target), '--jar', str(jar)], check=True)
        subprocess.run(['python3', str(ROOT/'update_adm.py'), '--id-file', str(target), '--jar', str(jar), '--check'], check=True)
        assert all(e in parse(target.read_text())['modIds'] for e in before['modIds'])
    print('ADM: JSON/styled formats, null hashes, dependency records, preservation and corruption rejection passed.')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    args = parser.parse_args()
    jar = ROOT/'dist'/NAME
    check_jar(args.source, jar)
    check_kubejs()
    check_adm(jar)
    print('Offline checks passed. Minecraft server/client startup was NOT tested.')

if __name__ == '__main__': main()
