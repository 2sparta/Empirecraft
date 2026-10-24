#!/usr/bin/env python3
"""Register this new client/server JAR without replacing other lock/ADM entries."""
import argparse
import copy
import hashlib
import importlib.util
import json
import time
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
                 old_version=None, updated=False, source='local',
                 git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest(),
                 sha256=digest, sha512=hashlib.sha512(raw).hexdigest(), size=len(raw),
                 pin_reason='Empirecraft 48-minute day and 30-day month; source and exact-JAR tests in seasonal-calendar/')
    lock_path = REPO/'modpack-lock.json'
    old_lock = json.loads(lock_path.read_text())
    lock = copy.deepcopy(old_lock)
    previous = next((r for r in lock['mods'] if r['id']==entry['id']), None)
    if previous is None:
        lock['mods'].append(entry)
        for side in entry['sides']: lock[side+'_jar_count'] += 1
    else: previous.update(entry)
    path = REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'
    old_ids = adm.parse(path.read_text(encoding='utf-8-sig'))
    ids = copy.deepcopy(old_ids)
    expected = dict(id=entry['id'], version=VERSION, sha256=digest, filePresent=True)
    current = next((r for r in ids['modIds'] if r['id']==entry['id']), None)
    if current != expected:
        if current is None: ids['modIds'].append(expected)
        else: current.update(expected)
        ids['modIds'].sort(key=lambda row: row['id'])
        ids['lastUpdated'] = int(time.time()*1000)
        ids['integrityHash'] = adm.integrity(ids)
    assert all(row in lock['mods'] for row in old_lock['mods'] if row['id']!=entry['id'])
    assert all(row in ids['modIds'] for row in old_ids['modIds'] if row['id']!=entry['id'])
    assert ids['resourcePackIds'] == old_ids['resourcePackIds']
    assert ids['integrityHash'] == adm.integrity(ids)
    if args.check:
        assert lock == old_lock, 'Lock file does not match the calendar JAR'
        assert ids == old_ids, 'ADM does not match the calendar JAR'
    else:
        lock_path.write_text(json.dumps(lock, ensure_ascii=False, indent=2)+'\n')
        path.write_text(json.dumps(ids, ensure_ascii=False, indent=2)+'\n')
    print('PASS calendar lock entry, identical client/server hashes, ADM integrity and preservation of other records')


if __name__ == '__main__':
    main()
