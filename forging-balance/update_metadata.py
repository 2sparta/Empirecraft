#!/usr/bin/env python3
"""Pin only this new addon, retaining all existing mods, packs and ADM settings."""
import argparse, copy, hashlib, json
from pathlib import Path
from build import ROOT, REPO, NAME, VERSION

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
    entry = dict(id='empirecraft_forging', file=NAME, sides=['client', 'server'], version=VERSION, old_version='1.1.2', added=False, updated=True,
        source='local:forging-balance', size=len(raw), sha256=sha256, sha512=hashlib.sha512(raw).hexdigest(), git_sha=git_sha,
        pin_reason='20% slower premium armor minigames and 10/20/40 blueprint research thresholds. Broken tool feedback, wrong-block mining correction and vanilla Efficiency III/V quality speeds; preserved native retirement of Epic Knights steel plate and 40 diamond/netherite weapons; fixed equipment quality stats, Maximilian anvil recipes, removal of ordinary steel armor and potion arrows; retained blueprint progression, minigame difficulty and arquebus powder. Plain tiered-arrow names ignore quality-only NBT; full-runtime trader checks in trader-fix/. Exact native tests in forging-balance/.')
    # A metadata check must not reorder unrelated modules appended by later releases.
    current_entry = next((item for item in lock['mods'] if item['id'] == entry['id']), None)
    if current_entry is None: lock['mods'].append(entry)
    else: current_entry.update(entry)
    lock['forging_balance_notes'] = 'Install identical forging addon and refreshed calendar JAR on server and all clients. Original Overgeared/Boomsticks JARs are retained; equipment rules and recipes are built into the forging addon.'
    assert [x for x in old['mods'] if x['id'] != entry['id']] == [x for x in lock['mods'] if x['id'] != entry['id']]
    changes[path] = (old, lock)

    path = REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json'
    old = json.loads(path.read_text()); adm = copy.deepcopy(old)
    assert integrity(old) == old['integrityHash'], 'Existing ADM integrityHash does not match'
    addition = dict(id=entry['id'], version=entry['version'], sha256=sha256, filePresent=True)
    current = next((x for x in old['modIds'] if x['id'] == entry['id']), None)
    if current != addition:
        if current is None:
            adm['modIds'].append(addition)
        else:
            adm['modIds'] = [addition if x['id'] == entry['id'] else x for x in adm['modIds']]
        adm['lastUpdated'] += 1
        adm['integrityHash'] = integrity(adm)
    assert [x for x in old['modIds'] if x['id'] != entry['id']] == [x for x in adm['modIds'] if x['id'] != entry['id']]
    assert adm['resourcePackIds'] == old['resourcePackIds']
    changes[path] = (old, adm)

    calendar = next((x for x in lock['mods'] if x['id'] == 'empirecraft_calendar'), None)
    path = REPO/'seasonal-calendar/installed-mods.json'
    if path.exists():
        old = json.loads(path.read_text()); inventory = [copy.deepcopy(x) for x in old if not x['name'].startswith('empirecraft-forging-')]
        if calendar:
            for row in inventory:
                if row['name'] == calendar['file']:
                    row.update(sha=calendar['git_sha'], size=calendar['size'])
        inventory.append(dict(name=NAME, sha=git_sha, size=len(raw), path='server/mods/'+NAME))
        inventory.sort(key=lambda x: x['name'])
        changes[path] = (old, inventory)
        lock['server_jar_count'] = len(inventory)
    path = ROOT/'installed-client-mods.json'
    old = json.loads(path.read_text()); inventory = [copy.deepcopy(x) for x in old if not x['name'].startswith('empirecraft-forging-')]
    if calendar:
        for row in inventory:
            if row['name'] == calendar['file']:
                row.update(sha=calendar['git_sha'], size=calendar['size'])
    inventory.append(dict(name=NAME, sha=git_sha, size=len(raw), path='mods/'+NAME))
    inventory.sort(key=lambda x: x['name'])
    lock['client_jar_count'] = len(inventory)
    changes[path] = (old, inventory)
    path=REPO/'seasonal-calendar/audit/coverage.json'
    if path.exists():
        old=json.loads(path.read_text());coverage=copy.deepcopy(old)
        for filename in list(coverage['jar_sha256']):
            if filename.startswith('empirecraft-forging-'):coverage['jar_sha256'].pop(filename)
        coverage['jar_sha256'][NAME]=sha256
        changes[path]=(old,coverage)
    native=ROOT/'build/native-release.json'
    if native.exists():
        release=json.loads(native.read_text());assert release['file']==NAME and release['sha256']==sha256
        for relative in ['native-content/inputs.json','native-content/audit/build.json']:
            path=REPO/relative;old=json.loads(path.read_text());data=copy.deepcopy(old)
            old_keys=[key for key in data if Path(key).name.startswith('empirecraft-forging-')]
            for key in old_keys:data.pop(key)
            for side in ['mods','server/mods']:
                key=side+'/'+NAME;data[key]=release['input_sha256'] if relative.endswith('inputs.json') else release['report']
            changes[path]=(old,data)
        path=REPO/'native-content/retained-code.json';old=json.loads(path.read_text());data=copy.deepcopy(old)
        for key in list(data):
            if key.startswith('empirecraft-forging-'):data.pop(key)
        import zipfile
        with zipfile.ZipFile(REPO/'mods'/NAME) as archive:
            data[NAME]={name:hashlib.sha256(archive.read(name)).hexdigest() for name in sorted(archive.namelist()) if name.startswith('ua/empirecraft/forging/') and name.endswith('.class')}
        changes[path]=(old,data)
    for path, (before, after) in changes.items():
        if check: assert before == after, str(path)+' is stale'
        elif before != after: path.write_text(json.dumps(after, ensure_ascii=False, indent=2)+'\n')
    print('PASS modpack lock, server inventory and ADM integrity:', sha256)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--check', action='store_true'); args = parser.parse_args(); update(args.check)


