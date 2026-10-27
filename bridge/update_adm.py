#!/usr/bin/env python3
"""Pin the installed map JAR in the server's local ADM list. No network access."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import time
import zipfile

MOD_ID = 'empirecraft_townymap'
VERSION = '1.2.0'
ROOT = Path(__file__).resolve().parent.parent


def text(value):
    if value is None:
        return 'null'
    return str(value)


def integrity(data):
    value = ''.join(text(data[key]) + '|' for key in
                    ('osName', 'cpuInfo', 'ramInfo', 'gpuInfo', 'anticheatVersion', 'lastUpdated'))
    value += ''.join('{}@{}[{}]{{{}}};'.format(
        text(entry['id']), text(entry['version']), text(entry['sha256']),
        1 if entry['filePresent'] else 0) for entry in data['modIds'])
    value += ''.join('{}[{}];'.format(text(entry['filename']), text(entry['sha256']))
                     for entry in data['resourcePackIds'])
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def jar_hash(path):
    contents = path.read_bytes()
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError('Пошкоджений JAR моста.')
        metadata = archive.read('META-INF/mods.toml').decode('utf-8')
    if not re.search(r'^modId\s*=\s*"' + MOD_ID + r'"\s*$', metadata, re.M):
        raise ValueError('Це не JAR Empirecraft JourneyMap Bridge.')
    if not re.search(r'^version\s*=\s*"' + re.escape(VERSION) + r'"\s*$', metadata, re.M):
        raise ValueError('Потрібна версія моста ' + VERSION)
    return hashlib.sha256(contents).hexdigest()


def prepare(data, sha256):
    if integrity(data) != data['integrityHash']:
        raise ValueError('Початковий integrityHash ADM не збігається. Спершу перевірте файл.')
    result = copy.deepcopy(data)
    entries = [entry for entry in result['modIds'] if entry['id'] == MOD_ID]
    if len(entries) > 1:
        raise ValueError('ADM містить дублікати моста. Потрібна ручна перевірка.')
    entry = dict(id=MOD_ID, version=VERSION, sha256=sha256, filePresent=True)
    if entries:
        entries[0].update(entry)
    else:
        result['modIds'].append(entry)
    result['modIds'].sort(key=lambda item: item['id'])
    for field in ('osName', 'cpuInfo', 'ramInfo', 'gpuInfo'):
        result[field] = 'Unknown'
    result['lastUpdated'] = int(time.time() * 1000)
    result['integrityHash'] = integrity(result)
    assert result['resourcePackIds'] == data['resourcePackIds']
    assert all(entry in result['modIds'] for entry in data['modIds'] if entry['id'] != MOD_ID)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--id-file', type=Path,
                        default=ROOT / 'server/config/admanticheat/idfile/admanticheat_id_list.json')
    parser.add_argument('--jar', type=Path,
                        default=ROOT / ('mods/empirecraft-towny-journeymap-' + VERSION + '.jar'))
    parser.add_argument('--check', action='store_true', help='Перевірити без запису')
    args = parser.parse_args()
    sha256 = jar_hash(args.jar)
    data = json.loads(args.id_file.read_text(encoding='utf-8-sig'))
    if args.check:
        entries = [entry for entry in data['modIds'] if entry['id'] == MOD_ID]
        if integrity(data) != data['integrityHash'] or len(entries) != 1 or any(
            entries[0].get(key) != value for key, value in
            dict(version=VERSION, sha256=sha256, filePresent=True).items()
        ):
            raise ValueError('ADM ще не відповідає встановленому JAR. Запустіть скрипт без --check.')
        print('ADM: JAR моста та integrityHash правильні.')
        return
    updated = prepare(data, sha256)
    descriptor, temporary = tempfile.mkstemp(prefix='.adm-update-', dir=args.id_file.parent)
    try:
        os.chmod(temporary, stat.S_IMODE(args.id_file.stat().st_mode))
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            json.dump(updated, stream, ensure_ascii=False, indent=2)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, args.id_file)
    finally:
        Path(temporary).unlink(missing_ok=True)
    print('ADM оновлено локально: міст ' + VERSION + '; інші моди й ресурс-паки збережені.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        raise SystemExit(str(error))


