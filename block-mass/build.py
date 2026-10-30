#!/usr/bin/env python3
"""Generate data-driven material rules for the existing Ship Physics 1.2.0."""
import argparse
import collections
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
NAMESPACE = 'empirecraft_mass'
SURFACES = {
    'wood': (0.55, 0.05, 0.6, 1.0),
    'iron': (0.4, 0.03, 0.7, 1.0),
    'brass': (0.45, 0.03, 0.35, 1.0),
    'copper': (0.45, 0.03, 0.35, 1.0),
    'stone': (0.65, 0.02, 0.12, 1.0),
    'glass': (0.25, 0.08, 0.03, 0.65),
    'dirt': (0.8, 0.0, 0.9, 1.0),
    'ice': (0.03, 0.05, 0.1, 0.9),
    'wool': (0.7, 0.01, 0.95, 1.0),
    'leaves': (0.6, 0.01, 0.9, 1.0),
    'slime': (0.8, 0.2, 0.9, 1.0),
}


def read(name):
    return json.loads((ROOT / name).read_text())


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def generated():
    catalog, profiles = read('catalog.json'), read('profiles.json')
    settings = read('settings-base.json')
    # Preserve all existing collision, sail and native material settings.
    assert not settings['blocks'], 'Review existing overrides before replacing them'
    for name, profile in profiles.items():
        assert name not in settings['materials'], 'Built-in densities must stay unchanged'
        settings['materials'][name] = {key: profile[key] for key in ('density', 'toughness')}
    settings.setdefault('surfaces', {})
    for name, profile in profiles.items():
        settings['surfaces'][name] = dict(zip(
            ('friction', 'restitution', 'crushFraction', 'fractureMultiplier'),
            SURFACES[profile['surface']]))
    settings['blocks'] = [
        {'block': f'#{NAMESPACE}:{material}', 'material': material}
        for material in sorted(catalog)
    ]
    result = {Path(folder) / 'empirecraft-ship-physics.json': encode(settings)
              for folder in ('config', 'server/config')}
    for material, ids in sorted(catalog.items()):
        # Some installed JARs retain assets for disabled or optional registrations.
        # Optional *exact* IDs avoid registering or resurrecting that content.
        lines = [json.dumps({'id': bid, 'required': False}) for bid in ids]
        tag = ('{\n  "replace": false,\n  "values": [\n    ' +
               ',\n    '.join(lines) + '\n  ]\n}\n').encode()
        for folder in ('kubejs', 'server/kubejs'):
            result[Path(folder) / 'data' / NAMESPACE / 'tags/blocks' / (material + '.json')] = tag
    for language in ('en_us', 'uk_ua', 'ru_ru'):
        names = {f'empirecraft.physics.material.{name}': profile['names'][language]
                 for name, profile in profiles.items()}
        for folder in ('kubejs', 'server/kubejs'):
            result[Path(folder) / 'assets/empirecraft_ship_physics/lang' / (language + '.json')] = encode(names)
    summary = {
        'physics_version': '1.2.0',
        'catalog_kind': 'unique blockstate asset IDs; not a live registry count',
        'assigned_ids': sum(map(len, catalog.values())),
        'material_tags': len(catalog),
        'new_material_profiles': len(profiles),
        'namespaces': dict(sorted(collections.Counter(
            bid.split(':')[0] for ids in catalog.values() for bid in ids).items())),
        'excluded': {name: len(ids) for name, ids in read('exclusions.json').items()},
        'config_utf8_bytes': len(result[Path('config/empirecraft-ship-physics.json')]),
        'live_world_tested': False,
    }
    result[Path('block-mass/audit/coverage.json')] = encode(summary)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    outputs = generated()
    for relative, content in outputs.items():
        target = REPO / relative
        if args.check:
            assert target.is_file() and target.read_bytes() == content, f'Stale generated file: {relative}'
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
    print(f'PASS {len(outputs)} generated files; client/server material tables match')


if __name__ == '__main__':
    main()
