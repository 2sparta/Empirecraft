#!/usr/bin/env python3
"""Build the server-only dimension compatibility pack from checksum-pinned mod data."""
import argparse
import copy
import hashlib
import json
import re
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
OUTPUT = REPO / 'server/world/datapacks/empirecraft-multiverse.zip'
WIND_FILES = {'minecraft:overworld': 'overworld', 'minecraft:the_nether': 'the_nether', 'minecraft:the_end': 'the_end'}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def aliases_from(config):
    aliases = config['dimension_aliases']
    seen = set(WIND_FILES)
    if not aliases:
        raise ValueError('At least one dimension alias is required')
    for source, targets in aliases.items():
        if source not in WIND_FILES or not targets:
            raise ValueError('Expected a vanilla source dimension and nonempty alias list')
        for target in targets:
            if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9/._-]+', target) or target in seen:
                raise ValueError('Invalid, duplicate or chained dimension alias: ' + target)
            seen.add(target)
    return aliases


def expand_restrictions(value, aliases):
    """Only extend explicit dimension restrictions, including nested restrictions and denylists."""
    changed = False
    if isinstance(value, dict):
        if value.get('type') == 'starcatcher:dimension':
            for field in ['dimensions', 'dimensions_blacklist']:
                values = value.get(field, [])
                for source, targets in aliases.items():
                    if source in values:
                        for target in targets:
                            if target not in values:
                                values.append(target)
                                changed = True
        for child in value.values():
            changed = expand_restrictions(child, aliases) or changed
    elif isinstance(value, list):
        for child in value:
            changed = expand_restrictions(child, aliases) or changed
    return changed


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def entries_for(starcatcher, sails, aliases):
    entries = {'pack.mcmeta': json_bytes({'pack': {
        'pack_format': 15,
        'description': 'Empirecraft: Sails wind and Starcatcher fishing in world_europe'
    }})}
    with zipfile.ZipFile(sails) as source:
        for dimension, targets in sorted(aliases.items()):
            filename = WIND_FILES[dimension]
            rule = json.loads(source.read('data/vs_sails/vs_sails/wind/' + filename + '.json'))
            rule['priority'] = 10
            rule['targets'] = {'dimensions': targets, 'dimension_tags': []}
            entries['data/empirecraft/vs_sails/wind/multiverse_' + filename + '.json'] = json_bytes(rule)
    with zipfile.ZipFile(starcatcher) as source:
        for name in sorted(source.namelist()):
            if not re.fullmatch(r'data/[^/]+/starcatcher/fish/.+\.json', name):
                continue
            original = json.loads(source.read(name))
            patched = copy.deepcopy(original)
            if expand_restrictions(patched, aliases):
                entries[name] = json_bytes(patched)
    return entries


def write_pack(output, entries):
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w') as archive:
        for name, data in sorted(entries.items()):
            entry = zipfile.ZipInfo(name, (2026, 10, 25, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--starcatcher', type=Path)
    parser.add_argument('--sails', type=Path)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    args = parser.parse_args()
    config = json.loads((ROOT / 'config.json').read_text())
    aliases = aliases_from(config)
    inputs = {}
    for name, item in config['downloads'].items():
        path = getattr(args, name) or ROOT / 'build' / item['file']
        if not path.exists():
            path.parent.mkdir(parents=True, exist_ok=True)
            with urllib.request.urlopen(item['url'], timeout=60) as response:
                path.write_bytes(response.read())
        if digest(path) != item['sha256']:
            raise SystemExit('Checksum mismatch: ' + name)
        inputs[name] = path
    entries = entries_for(inputs['starcatcher'], inputs['sails'], aliases)
    write_pack(args.output, entries)
    print(json.dumps({'file': str(args.output), 'sha256': digest(args.output),
                      'fish_overrides': sum('/starcatcher/fish/' in name for name in entries),
                      'wind_rules': len(aliases), 'dimension_aliases': aliases}))


if __name__ == '__main__':
    main()
