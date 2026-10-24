#!/usr/bin/env python3
"""Pin medieval Create in the existing ADM 1.9.3 list, retaining all other records and checks."""
import argparse
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import time
import zipfile

VERSION = '6.0.8-medieval.1'

def text(value): return 'null' if value is None else str(value)

def integrity(data):
    value = ''.join(text(data[key]) + '|' for key in ('osName', 'cpuInfo', 'ramInfo', 'gpuInfo', 'anticheatVersion', 'lastUpdated'))
    value += ''.join('{}@{}[{}]{{{}}};'.format(text(e['id']), text(e['version']), text(e['sha256']), 1 if e['filePresent'] else 0) for e in data['modIds'])
    value += ''.join('{}[{}];'.format(text(e['filename']), text(e['sha256'])) for e in data['resourcePackIds'])
    return hashlib.sha256(value.encode()).hexdigest()

def parse(content):
    if content.lstrip().startswith('{'):
        result = json.loads(content)
    else:
        result = {'modIds': [], 'resourcePackIds': []}
        labels = {'OS': 'osName', 'CPU': 'cpuInfo', 'RAM': 'ramInfo', 'GPU': 'gpuInfo',
                  'Anticheat Version': 'anticheatVersion', 'Last Updated': 'lastUpdated', 'Integrity Hash': 'integrityHash'}
        for label, field in labels.items():
            match = re.search(r'^\s*' + re.escape(label) + r':\s*(.*?)\s*$', content, re.M)
            if not match: raise ValueError('ADM: відсутнє поле ' + label)
            result[field] = int(match[1]) if field == 'lastUpdated' else match[1]
        mod_section = content.split('[Mod IDs]', 1)[1].split('[Resource Pack IDs]', 1)[0]
        for mod_id, version, digest, presence in re.findall(r'^\s*- (\S+)\s+@\s+(.*?)\s+\[([^]]*)\] \{(file|nofile)\}\s*$', mod_section, re.M):
            result['modIds'].append(dict(id=mod_id, version=version, sha256=digest or None, filePresent=presence == 'file'))
        pack_section = content.split('[Resource Pack IDs]', 1)[1]
        for filename, digest in re.findall(r'^\s*- (.*?)\s+\[([^]]*)\]\s*$', pack_section, re.M):
            result['resourcePackIds'].append(dict(filename=filename, sha256=digest or None))
        # Styled ADM files print both null and empty strings as []. Retain the interpretation that validates.
        if integrity(result) != result['integrityHash']:
            candidate = copy.deepcopy(result)
            for entry in candidate['modIds'] + candidate['resourcePackIds']:
                if entry['sha256'] is None: entry['sha256'] = ''
            if integrity(candidate) == candidate['integrityHash']: result = candidate
    if not result['modIds'] or integrity(result) != result['integrityHash']:
        raise ValueError('Початковий integrityHash ADM не збігається. Список не змінено.')
    ids = [e['id'] for e in result['modIds']]
    if len(ids) != len(set(ids)): raise ValueError('ADM містить дублікати mod ID.')
    return result

def expected(jar):
    raw = jar.read_bytes()
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        if archive.testzip(): raise ValueError('Пошкоджений JAR.')
        metadata = archive.read('META-INF/mods.toml').decode()
        if not re.search(r'^modId\s*=\s*"create"\s*$', metadata, re.M) or not re.search(r'^version\s*=\s*"' + re.escape(VERSION) + r'"\s*$', metadata, re.M):
            raise ValueError('Потрібен Create ' + VERSION)
        entries = [dict(id='create', version=VERSION, sha256=hashlib.sha256(raw).hexdigest(), filePresent=True)]
        # ADM 1.9.3 hashes physical file:// paths. Jar-in-jar mods use union/jar paths, as with existing embedded mods.
        for nested in json.loads(archive.read('META-INF/jarjar/metadata.json'))['jars']:
            with zipfile.ZipFile(io.BytesIO(archive.read(nested['path']))) as dependency:
                if 'META-INF/mods.toml' not in dependency.namelist(): continue
                toml = dependency.read('META-INF/mods.toml').decode()
                mod = re.search(r'^modId\s*=\s*"([^"]+)"', toml, re.M)
                version = re.search(r'^version\s*=\s*"([^"]+)"', toml, re.M)
                if not mod or not version: raise ValueError('Невідома вбудована залежність.')
                entries.append(dict(id=mod[1], version=version[1], sha256=None, filePresent=False))
        return entries

def prepare(data, entries):
    result = copy.deepcopy(data)
    replacements = {e['id']: e for e in entries}
    present = {e['id']: e for e in result['modIds']}
    for mod_id, entry in replacements.items():
        if mod_id != 'create' and mod_id in present:
            if present[mod_id]['version'] != entry['version']:
                raise ValueError('Спочатку перевір сумісність установленої версії ' + mod_id)
            continue  # Existing standalone/embedded copy remains authoritative.
        present[mod_id] = entry
    result['modIds'] = sorted(present.values(), key=lambda e: e['id'])
    result['lastUpdated'] = int(time.time() * 1000)
    result['integrityHash'] = integrity(result)
    assert result['resourcePackIds'] == data['resourcePackIds']
    assert all(e in result['modIds'] for e in data['modIds'] if e['id'] != 'create')
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--id-file', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = parse(args.id_file.read_text(encoding='utf-8-sig'))
    entries = expected(args.jar)
    if args.check:
        current = {e['id']: e for e in data['modIds']}
        for entry in entries:
            found = current.get(entry['id'])
            if not found or found['version'] != entry['version'] or (entry['id'] == 'create' and found != entry):
                raise ValueError('ADM ще не відповідає новому JAR: ' + entry['id'])
        print('ADM: новий Create та integrityHash правильні.')
        return
    result = prepare(data, entries)
    # Real JSON is supported by ADM and avoids information loss in its styled text parser.
    descriptor, temporary = tempfile.mkstemp(prefix='.adm-medieval-', dir=args.id_file.parent)
    try:
        os.chmod(temporary, stat.S_IMODE(args.id_file.stat().st_mode))
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2)
            stream.write('\n'); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, args.id_file)
    finally: Path(temporary).unlink(missing_ok=True)
    print('ADM оновлено. Інші моди, ресурс-паки та налаштування захисту збережені.')

if __name__ == '__main__':
    try: main()
    except (OSError, ValueError, KeyError, IndexError, zipfile.BadZipFile) as error: raise SystemExit(str(error))
