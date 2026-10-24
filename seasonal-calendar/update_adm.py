#!/usr/bin/env python3
"""Add or verify the calendar in an existing ADM list, preserving its other records."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import os
import tempfile
import time
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parent
helper_path = ROOT/'adm_format.py'
if not helper_path.exists(): helper_path = ROOT.parent/'medieval-create/update_adm.py'
spec = importlib.util.spec_from_file_location('adm_format', helper_path)
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


def expected(jar):
    raw = jar.read_bytes()
    with zipfile.ZipFile(jar) as archive:
        assert archive.testzip() is None, 'Damaged JAR'
        mods = tomllib.loads(archive.read('META-INF/mods.toml').decode())['mods']
        assert len(mods) == 1 and mods[0]['modId'] == 'empirecraft_calendar', 'Not the Empirecraft calendar JAR'
        assert 'empirecraft_calendar.mixins.json' in archive.namelist()
    return dict(id='empirecraft_calendar', version=mods[0]['version'], sha256=hashlib.sha256(raw).hexdigest(), filePresent=True)


def prepare(current, entry):
    result = copy.deepcopy(current)
    old = next((row for row in result['modIds'] if row['id'] == entry['id']), None)
    if old == entry: return result
    if old is None: result['modIds'].append(entry)
    else: old.update(entry)
    result['modIds'].sort(key=lambda row: row['id'])
    result['lastUpdated'] = int(time.time()*1000)
    result['integrityHash'] = helper.integrity(result)
    assert all(row in result['modIds'] for row in current['modIds'] if row['id'] != entry['id'])
    assert result['resourcePackIds'] == current['resourcePackIds']
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--id-file', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    current = helper.parse(args.id_file.read_text(encoding='utf-8-sig'))
    result = prepare(current, expected(args.jar))
    if args.check:
        assert result == current, 'ADM list does not match the calendar JAR'
    elif result != current:
        descriptor, temporary = tempfile.mkstemp(prefix='.calendar-adm-', dir=args.id_file.parent)
        try:
            os.chmod(temporary, args.id_file.stat().st_mode & 0o777)
            with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
                stream.write(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, args.id_file)
        finally: Path(temporary).unlink(missing_ok=True)
    print('PASS calendar ADM entry and integrity; other mod and resource-pack records preserved')


if __name__ == '__main__':
    main()
