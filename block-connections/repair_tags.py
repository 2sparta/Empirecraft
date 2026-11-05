#!/usr/bin/env python3
"""Repair shared connection tags and mirror the working server overlay for single player."""
import argparse
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
TAGS = ('fences', 'walls', 'fence_gates', 'wall_post_override')
RELATIVE = Path('data/minecraft/tags/blocks')
spec = importlib.util.spec_from_file_location('connection_tag_references', REPO/'end-removal/tag_references.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


def repaired(data):
    return dict(data, values=[helper.optional_mod_reference(value) for value in data['values']])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    audit_path = REPO/'end-removal/audit/content.json'
    audit = json.loads(audit_path.read_text())
    previous_audit = json.dumps(audit, ensure_ascii=False, indent=2)+'\n'
    for name in TAGS:
        server = REPO/'server/kubejs'/RELATIVE/(name+'.json')
        source = REPO/'end-removal/src/main/resources'/RELATIVE/(name+'.json')
        for path in (server, source):
            before = json.loads(path.read_text())
            after = repaired(before)
            if args.check:
                assert before == after, ('Mandatory optional-mod reference', path)
            elif before != after:
                path.write_text(json.dumps(after, ensure_ascii=False, indent=2)+'\n')
        client = REPO/'kubejs'/RELATIVE/(name+'.json')
        if args.check:
            assert client.read_bytes() == server.read_bytes(), ('Client/server tag mismatch', name)
        else:
            client.parent.mkdir(parents=True, exist_ok=True)
            client.write_bytes(server.read_bytes())
        values = json.loads(source.read_text())['values']
        key = 'blocks:minecraft:'+name
        if args.check:
            assert audit['cleaned_tags'][key] == values, ('Stale tag audit', name)
        else:
            audit['cleaned_tags'][key] = values
    updated_audit = json.dumps(audit, ensure_ascii=False, indent=2)+'\n'
    if not args.check and updated_audit != previous_audit:
        audit_path.write_text(updated_audit)
    print('PASS connection tags: optional mod references, strict vanilla references and client/server parity')


if __name__ == '__main__':
    main()
