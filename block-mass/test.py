#!/usr/bin/env python3
"""Validate complete asset coverage, tag precedence, runtime limits and side parity."""
import argparse
import hashlib
import json
import math
import os
import re
import subprocess
import zipfile
from pathlib import Path
from build import ROOT, REPO, generated, read


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--scan', action='store_true', help='Compare against pinned installed server JARs')
    parser.add_argument('--runtime', action='store_true', help='Run the actual PhysicsConfig.class with filesystem/cache stubs')
    args = parser.parse_args()
    outputs = generated()
    for path, data in outputs.items():
        assert (REPO/path).read_bytes() == data, f'Stale output: {path}'
    catalog, profiles = read('catalog.json'), read('profiles.json')
    ids = [bid for group in catalog.values() for bid in group]
    excluded = [bid for group in read('exclusions.json').values() for bid in group]
    assert len(ids) == len(set(ids)) and not set(ids) & set(excluded)
    assert len(excluded) == len(set(excluded))
    assigned = {bid: material for material, bids in catalog.items() for bid in bids}
    assert all(re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', bid) for bid in ids+excluded)
    config = json.loads(outputs[Path('config/empirecraft-ship-physics.json')])
    assert len(config['blocks']) <= 512 and len(config['materials']) <= 128
    assert len(config['surfaces']) <= 128
    assert len(outputs[Path('config/empirecraft-ship-physics.json')]) < 30000
    assert len(set(row['block'] for row in config['blocks'])) == len(catalog)
    baseline = read('settings-base.json')
    for key, value in baseline.items():
        if key not in ('blocks', 'materials', 'surfaces'):
            assert config[key] == value, f'Unrelated setting changed: {key}'
    for key, value in baseline['materials'].items():
        assert config['materials'][key] == value, f'Vanilla material changed: {key}'
    for material in catalog:
        assert material in config['materials']
        tag = json.loads(outputs[Path(f'kubejs/data/empirecraft_mass/tags/blocks/{material}.json')])
        assert tag['replace'] is False
        assert tag['values'] == [{'id': bid, 'required': False} for bid in catalog[material]]
        assert {'block': '#empirecraft_mass:'+material, 'material': material} in config['blocks']
    for name, profile in profiles.items():
        assert 0 < profile['density'] <= 100000 and 1 <= profile['toughness'] <= 1e9
        assert all(math.isfinite(profile[key]) for key in ('density', 'toughness'))
        assert set(profile['names']) == {'en_us', 'uk_ua', 'ru_ru'}
    # These are semantic regressions: an English word or decorative pattern must
    # not turn a cannon into wood, a wall into vegetation, or logs into masonry.
    expected = {
        'create:large_cogwheel': 'timber_drive', 'create:cogwheel': 'timber_drive',
        'create:shaft': 'andesite_alloy', 'create:andesite_casing': 'andesite_machine',
        'create:brass_casing': 'brass_machine', 'create:fluid_tank': 'copper_machine',
        'create:white_table_cloth': 'wool', 'create:brass_ladder': 'brass',
        'createbigcannons:cast_iron_cannon_barrel': 'iron',
        'createbigcannons:bronze_cannon_barrel': 'bronze',
        'createbigcannons:steel_cannon_barrel': 'steel',
        'createbigcannons:cannon_carriage': 'timber_drive',
        'mcwfurnitures:oak_wardrobe': 'wood_furniture',
        'mcwfurnitures:white_couch': 'upholstery',
        'mcwfences:stone_grass_topped_wall': 'stone',
        'chipped:brick_bond_oak_planks': 'wood',
        'chipped:oak_planks_mosaic': 'wood', 'chipped:coal_block_bricks': 'coal',
        'chipped:raw_iron_block_bricks': 'iron', 'chipped:ice_bricks': 'ice',
        'chipped:obsidian_bricks': 'obsidian', 'chipped:acacia_barrel': 'wood_container',
        'antiquelegacy:tin_ore': 'stone', 'overgeared:steel_block': 'steel',
        'overgeared:water_barrel_full': 'filled_barrel',
        'medieval_boomsticks:arnica_crop_block': 'plant',
        'vinery:grass_slab': 'dirt', 'farmersdelight:tomato_crate': 'filled_crate',
        'supplementaries:awning': 'wool', 'supplementaries:cannon': 'iron',
    }
    for bid, material in expected.items():
        assert assigned.get(bid) == material, (bid, assigned.get(bid), material)
    for bid in ids:
        ns, path = bid.split(':')
        assert ns not in ('copycats', 'vs_sails', 'valkyrienskies', 'minecraft', 'treechop')
        assert not (ns == 'create' and path.startswith('copycat_'))
    assert config['materials']['timber_drive']['density'] == 650
    # The screenshot's 355.5 kg was volume * fallback density 1000.
    # This checks the new balance for that same volume, not every orientation.
    assert abs(355.5 * 650/1000 - 231.075) < 1e-9
    print(f'PASS {len(ids)} unique assigned asset IDs; {len(excluded)} explicit exclusions; {len(expected)} semantic regressions')
    print('PASS paired tags/config/languages, unchanged built-ins and collision settings, config/packet limits')
    if args.scan:
        found = set()
        for record in read('inputs.json'):
            path = REPO/'server/mods'/record['jar']
            assert hashlib.sha256(path.read_bytes()).hexdigest() == record['sha256'], path
            count = 0
            with zipfile.ZipFile(path) as jar:
                for entry in jar.namelist():
                    match = re.fullmatch(r'assets/([^/]+)/blockstates/([^/]+)\.json', entry)
                    if match:
                        found.add(':'.join(match.groups())); count += 1
            assert count == record['blockstate_assets'], path
        assert found == set(ids+excluded), ('missing', sorted(found-set(ids+excluded)), 'extra', sorted(set(ids+excluded)-found))
        print(f'PASS all {len(read("inputs.json"))} pinned block-asset JARs; {len(found)} asset IDs fully accounted for')
    if args.runtime:
        runtime_config_test()


def runtime_config_test():
    physics = REPO/'server/mods/empirecraft-ship-physics-1.2.0.jar'
    assert hashlib.sha256(physics.read_bytes()).hexdigest() == '0a864ef88e62651804c428f5c5190d93bd03186a6808f94a7ec9eb070413e979'
    gson, = (REPO/'server/libraries/com/google/code/gson/gson').glob('*/*.jar')
    classes = ROOT/'build/runtime-test'
    classes.mkdir(parents=True, exist_ok=True)
    cp = os.pathsep.join(map(str, [classes, physics, gson]))
    sources = sorted((ROOT/'tests').rglob('*.java'))
    subprocess.run(['java', 'com.sun.tools.javac.Main', '-proc:none', '--release', '17',
                    '-cp', cp, '-d', str(classes), *map(str, sources)], check=True)
    subprocess.run(['java', '-ea', '-cp', cp, 'RuntimeConfigTest',
                    str(REPO/'server/config/empirecraft-ship-physics.json')], check=True)


if __name__ == '__main__':
    main()
