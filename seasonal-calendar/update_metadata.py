#!/usr/bin/env python3
"""Register this new client/server JAR without replacing other lock/ADM entries."""
import argparse
import copy
import hashlib
import importlib.util
import json
import time
import zipfile
from build import ROOT, REPO, NAME, VERSION

spec = importlib.util.spec_from_file_location('empirecraft_adm', REPO/'medieval-create/update_adm.py')
adm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(adm)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    raw = (REPO/'mods'/NAME).read_bytes()
    assert raw == (REPO/'server/mods'/NAME).read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    entry = dict(id='empirecraft_calendar', file=NAME, sides=['client', 'server'], version=VERSION,
                 old_version='1.2.3', updated=True, source='local:seasonal-calendar',
                 git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),
                 sha256=digest, sha512=hashlib.sha512(raw).hexdigest(), size=len(raw),
                 pin_reason='Persistent UTC calendar with civil years, administrator date/time rebasing, 48-minute days and 30-day months; 1.2.4 repairs mismatched Serene Seasons timing at startup with config backup; seasonal-calendar/ contains source and native checks.')
    lock_path = REPO/'modpack-lock.json'
    old_lock = json.loads(lock_path.read_text())
    lock = copy.deepcopy(old_lock)
    previous = next((r for r in lock['mods'] if r['id']==entry['id']), None)
    if previous is None:
        lock['mods'].append(entry)
        for side in entry['sides']: lock[side+'_jar_count'] += 1
    else: previous.update(entry)
    lock['updated_mod_count'] = sum(bool(row.get('updated')) for row in lock['mods'])
    path = REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'
    old_ids = adm.parse(path.read_text(encoding='utf-8-sig'))
    ids = copy.deepcopy(old_ids)
    expected = dict(id=entry['id'], version=VERSION, sha256=digest, filePresent=True)
    current = next((r for r in ids['modIds'] if r['id']==entry['id']), None)
    if current != expected:
        if current is None: ids['modIds'].append(expected)
        else: current.update(expected)
        ids['lastUpdated'] = int(time.time()*1000)
        ids['integrityHash'] = adm.integrity(ids)
    assert all(row in lock['mods'] for row in old_lock['mods'] if row['id']!=entry['id'])
    assert all(row in ids['modIds'] for row in old_ids['modIds'] if row['id']!=entry['id'])
    assert ids['resourcePackIds'] == old_ids['resourcePackIds']
    assert ids['integrityHash'] == adm.integrity(ids)
    changes = [(lock_path, old_lock, lock), (path, old_ids, ids)]
    for inventory, side in [(ROOT/'installed-mods.json', 'server/mods'),
                            (REPO/'forging-balance/installed-client-mods.json', 'mods')]:
        if not inventory.exists(): continue
        old = json.loads(inventory.read_text())
        rows = [row for row in old if not row['name'].startswith('empirecraft-calendar-')]
        rows.append(dict(name=NAME, path=side+'/'+NAME, size=len(raw), sha=entry['git_sha']))
        changes.append((inventory, old, sorted(rows, key=lambda row: row['name'])))
    coverage_path = ROOT/'audit/coverage.json'
    old = json.loads(coverage_path.read_text()); coverage = copy.deepcopy(old)
    coverage['jar_sha256'] = {name:sha for name,sha in coverage['jar_sha256'].items() if not name.startswith('empirecraft-calendar-')}
    coverage['jar_sha256'][NAME] = digest
    changes.append((coverage_path, old, coverage))
    native = ROOT/'build/native-release.json'
    if native.exists():
        release = json.loads(native.read_text())
        assert release['file'] == NAME and release['sha256'] == digest
        for relative in ['native-content/inputs.json', 'native-content/audit/build.json']:
            target = REPO/relative
            old = json.loads(target.read_text())
            updated = {key:value for key,value in old.items() if not key.split('/')[-1].startswith('empirecraft-calendar-')}
            for side in ['mods', 'server/mods']:
                updated[side+'/'+NAME] = release['input_sha256'] if relative.endswith('inputs.json') else release['report']
            changes.append((target, old, updated))
        target = REPO/'native-content/retained-code.json'; old = json.loads(target.read_text())
        updated = {key:value for key,value in old.items() if not key.startswith('empirecraft-calendar-')}
        with zipfile.ZipFile(REPO/'mods'/NAME) as jar:
            updated[NAME] = {name:hashlib.sha256(jar.read(name)).hexdigest() for name in sorted(jar.namelist()) if name.endswith('.class')}
        changes.append((target, old, updated))
    for target, before, after in changes:
        if args.check: assert before == after, ('Stale calendar metadata', target)
        elif before != after: target.write_text(json.dumps(after, ensure_ascii=False, indent=2)+'\n')
    print('PASS calendar lock entry, identical client/server hashes, ADM integrity and preservation of other records')


if __name__ == '__main__':
    main()
