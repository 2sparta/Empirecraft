#!/usr/bin/env python3
"""Verify the shipped pack against the original pinned data, not just the generator output."""
import argparse
import copy
import json
import tempfile
import zipfile
from pathlib import Path
import build


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--starcatcher', type=Path, required=True)
    parser.add_argument('--sails', type=Path, required=True)
    args = parser.parse_args()
    config = json.loads((build.ROOT / 'config.json').read_text())
    for name, path in [('starcatcher', args.starcatcher), ('sails', args.sails)]:
        assert build.digest(path) == config['downloads'][name]['sha256'], name
    checks = 0
    overrides = 0
    base_entries = 0
    with zipfile.ZipFile(args.starcatcher) as original, zipfile.ZipFile(build.OUTPUT) as pack:
        assert json.loads(pack.read('pack.mcmeta'))['pack']['pack_format'] == 15
        checks += 1
        for name in original.namelist():
            if not name.startswith('data/') or '/starcatcher/fish/' not in name or not name.endswith('.json'):
                continue
            before = json.loads(original.read(name))
            after = json.loads(pack.read(name)) if name in pack.namelist() else copy.deepcopy(before)
            changed = False

            def verify(a, b):
                nonlocal changed, checks
                if isinstance(a, dict):
                    assert set(a) == set(b), name
                    for key in a:
                        if a.get('type') == 'starcatcher:dimension' and key in ['dimensions', 'dimensions_blacklist']:
                            expected = a[key] + ([x for x in ['minecraft:world_europe'] if x not in a[key]]
                                                 if 'minecraft:overworld' in a[key] else [])
                            assert b[key] == expected, name
                            checks += 1
                            changed = changed or a[key] != b[key]
                        else:
                            verify(a[key], b[key])
                elif isinstance(a, list):
                    assert len(a) == len(b), name
                    for left, right in zip(a, b):
                        verify(left, right)
                else:
                    assert a == b, name

            verify(before, after)
            checks += 1
            assert (name in pack.namelist()) == changed, name
            if changed:
                overrides += 1
                base_entries += name.startswith(('data/starcatcher/', 'data/minecraft/'))
        assert overrides == 390, overrides
        assert not any('/dimension/' in name or '/dimension_type/' in name for name in pack.namelist())
        checks += 2
        with zipfile.ZipFile(args.sails) as sails:
            default = json.loads(sails.read('data/vs_sails/vs_sails/wind/overworld.json'))
        wind = json.loads(pack.read('data/empirecraft/vs_sails/wind/multiverse_overworld.json'))
        assert wind['wind'] == default['wind']
        assert wind['targets'] == {'dimensions': ['minecraft:world_europe'], 'dimension_tags': []}
        assert wind['priority'] > default['priority']
        assert wind['wind']['dimension_multiplier'] > 0
        assert len(pack.namelist()) == overrides + 2
        checks += 5

    nested = {'restrictions': [{'type': 'starcatcher:dimension', 'dimensions': [],
                               'dimensions_blacklist': ['minecraft:overworld']}]}
    assert build.expand_restrictions(nested, config['dimension_aliases'])
    assert nested['restrictions'][0]['dimensions'] == []
    assert nested['restrictions'][0]['dimensions_blacklist'] == ['minecraft:overworld', 'minecraft:world_europe']
    assert not build.expand_restrictions(nested, config['dimension_aliases'])
    checks += 4
    for bad in [{'minecraft:overworld': ['minecraft:the_nether']},
                {'minecraft:overworld': ['minecraft:world_europe', 'minecraft:world_europe']},
                {'minecraft:overworld': ['World Europe']}, {}]:
        try:
            build.aliases_from({'dimension_aliases': bad})
        except ValueError:
            checks += 1
        else:
            raise AssertionError('Invalid alias configuration accepted')
    with tempfile.TemporaryDirectory() as directory:
        rebuilt = Path(directory) / 'rebuilt.zip'
        build.write_pack(rebuilt, build.entries_for(args.starcatcher, args.sails, config['dimension_aliases']))
        assert rebuilt.read_bytes() == build.OUTPUT.read_bytes()
        checks += 1
    print(json.dumps({'passed': checks, 'fish_resources_extended': overrides,
                      'base_game_and_starcatcher_resources': base_entries,
                      'other_fish_fields_preserved': True, 'byte_identical_rebuild': True}))


if __name__ == '__main__':
    main()
