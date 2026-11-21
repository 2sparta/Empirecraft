#!/usr/bin/env python3
"""Verify the packaged native Field Guide mappings and the existing handbook resources."""

import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from build import NAME
from item_hints import FIELD_GUIDE, scan_pack
from layout import MAX_LINES, WRAPPED_TEMPLATE, lines


def read_entries(jar, locale):
    prefix = FIELD_GUIDE + locale + '/'
    categories = {
        path.removeprefix(prefix + 'categories/').removesuffix('.json'): json.loads(jar.read(path))
        for path in jar.namelist() if path.startswith(prefix + 'categories/')
    }
    assert 'parent' not in categories['empirecraft']
    for key, category in categories.items():
        if key != 'empirecraft':
            assert category['parent'] == 'tfc:empirecraft', key
    assert json.loads(jar.read(prefix + 'templates/empirecraft/wrapped_text.json')) == WRAPPED_TEMPLATE

    lookup = {}
    for path in jar.namelist():
        if not path.startswith(prefix + 'entries/empirecraft/'):
            continue
        entry = json.loads(jar.read(path))
        assert entry['category'].removeprefix('tfc:') in categories, path
        assert entry['pages'][0]['type'] == 'tfc:empirecraft/wrapped_text', path
        for page in entry['pages']:
            assert lines(page['text']) <= MAX_LINES, (path, page['text'])
        for stack, page in entry['extra_recipe_mappings'].items():
            assert page == 0 and stack not in lookup, (stack, path)
            lookup[stack] = path.removeprefix(prefix + 'entries/empirecraft/')
    return lookup


def main():
    _, native_items, native_files = scan_pack(ROOT.parent)
    with zipfile.ZipFile(ROOT / 'dist' / NAME) as jar:
        assert native_files.isdisjoint(jar.namelist())
        lookup = read_entries(jar, 'en_us')
        assert lookup == read_entries(jar, 'uk_ua')
        assert native_items.isdisjoint(lookup)
        assert 'minecraft:stick' not in lookup
        for crop, item in {
            'cabbages': 'farmersdelight:cabbage_seeds',
            'onions': 'farmersdelight:onion',
            'tomatoes': 'farmersdelight:tomato_seeds',
            'carrots': 'minecraft:carrot',
            'potatoes': 'minecraft:potato',
            'beetroots': 'minecraft:beetroot_seeds',
            'rice': 'farmersdelight:rice',
        }.items():
            target = f'plants/farmersdelight/wild_{crop}.json'
            assert lookup[item] == lookup[f'farmersdelight:wild_{crop}'] == target
        assert lookup['farmersdelight:tomato'] == lookup['farmersdelight:tomato_seeds']
        assert lookup['farmersdelight:cooking_pot'] == 'cooking.json'
        assert lookup['create:water_wheel'] == 'power.json'
        assert lookup['empirecraft_tfc:quern_drive'] == 'quern.json'
        assert lookup['vs_sails:white_sail'] == 'sails.json'
        assert lookup['starcatcher:tackle_box'] == 'tackle.json'
    print(f'FIELD_GUIDE_ITEM_HINTS_PASS items={len(lookup)} locales=en_us,uk_ua')


if __name__ == '__main__':
    main()
