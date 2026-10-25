#!/usr/bin/env python3
"""Pin only this new addon, retaining all existing mods, packs and ADM settings."""
import argparse, copy, hashlib, json
from pathlib import Path
from build import ROOT, REPO, NAME

def integrity(data):
    def text(value): return 'null' if value is None else str(value)
    value = ''.join(text(data[key])+'|' for key in ('osName', 'cpuInfo', 'ramInfo', 'gpuInfo', 'anticheatVersion', 'lastUpdated'))
    value += ''.join('{}@{}[{}]{{{}}};'.format(text(e['id']), text(e['version']), text(e['sha256']), 1 if e['filePresent'] else 0) for e in data['modIds'])
    value += ''.join('{}[{}];'.format(text(e['filename']), text(e['sha256'])) for e in data['resourcePackIds'])
    return hashlib.sha256(value.encode()).hexdigest()

def update(check=False):
    raw = (REPO/'mods'/NAME).read_bytes()
    assert raw == (REPO/'server/mods'/NAME).read_bytes()
    sha256 = hashlib.sha256(raw).hexdigest()
    git_sha = hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
    changes = {}
    path = REPO/'modpack-lock.json'; old = json.loads(path.read_text()); lock = copy.deepcopy(old)
    entry = dict(id='empirecraft_forging', file=NAME, sides=['client', 'server'], version='1.0.0', old_version=None, added=True, updated=False,
        source='local:forging-balance', size=len(raw), sha256=sha256, sha512=hashlib.sha512(raw).hexdigest(), git_sha=git_sha,
        pin_reason='Armor blueprints and slower research, armor difficulty and miss penalties, arquebus gunpowder, automatic UK/RU calendar fixes; exact native Mixin tests in forging-balance/.')
    lock['mods'] = [item for item in lock['mods'] if item['id'] != entry['id']] + [entry]
    lock['forging_balance_notes'] = 'Install identical forging addon and refreshed calendar JAR on server and all clients. Original Overgeared/Boomsticks JARs and progression recipes are retained.'
    assert [x for x in old['mods'] if x['id'] != entry['id']] == [x for x in lock['mods'] if x['id'] != entry['id']]
    changes[path] = (old, lock)

    path = REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'
    old = json.loads(path.read_text()); adm = copy.deepcopy(old)
    assert integrity(old) == old['integrityHash'], 'Existing ADM integrityHash does not match'
    addition = dict(id=entry['id'], version=entry['version'], sha256=sha256, filePresent=True)
    current = next((x for x in old['modIds'] if x['id'] == entry['id']), None)
    if current != addition:
        adm['modIds'] = sorted([x for x in adm['modIds'] if x['id'] != entry['id']] + [addition], key=lambda x: x['id'])
        adm['lastUpdated'] += 1
        adm['integrityHash'] = integrity(adm)
    assert [x for x in old['modIds'] if x['id'] != entry['id']] == [x for x in adm['modIds'] if x['id'] != entry['id']]
    assert adm['resourcePackIds'] == old['resourcePackIds']
    changes[path] = (old, adm)

    calendar = next((x for x in lock['mods'] if x['id'] == 'empirecraft_calendar'), None)
    path = REPO/'seasonal-calendar/installed-mods.json'
    if path.exists():
        old = json.loads(path.read_text()); inventory = [copy.deepcopy(x) for x in old if x['name'] != NAME]
        if calendar:
            for row in inventory:
                if row['name'] == calendar['file']:
                    row.update(sha=calendar['git_sha'], size=calendar['size'])
        inventory.append(dict(name=NAME, sha=git_sha, size=len(raw), path='server/mods/'+NAME))
        inventory.sort(key=lambda x: x['name'])
        changes[path] = (old, inventory)
        lock['server_jar_count'] = len(inventory)
    path = ROOT/'installed-client-mods.json'
    old = json.loads(path.read_text()); inventory = [copy.deepcopy(x) for x in old if x['name'] != NAME]
    if calendar:
        for row in inventory:
            if row['name'] == calendar['file']:
                row.update(sha=calendar['git_sha'], size=calendar['size'])
    inventory.append(dict(name=NAME, sha=git_sha, size=len(raw), path='mods/'+NAME))
    inventory.sort(key=lambda x: x['name'])
    lock['client_jar_count'] = len(inventory)
    changes[path] = (old, inventory)
    for path, (before, after) in changes.items():
        if check: assert before == after, str(path)+' is stale'
        elif before != after: path.write_text(json.dumps(after, ensure_ascii=False, indent=2)+'\n')
    print('PASS modpack lock, server inventory and ADM integrity:', sha256)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--check', action='store_true'); args = parser.parse_args(); update(args.check)
