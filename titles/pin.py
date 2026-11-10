#!/usr/bin/env python3
"""Pin the resource mod in the modpack and ADM without changing other entries."""
import argparse
import copy
import hashlib
import json
import time
import build


def integrity(data):
    def text(value): return 'null' if value is None else str(value)
    value = ''.join(text(data[key]) + '|' for key in
        ('osName', 'cpuInfo', 'ramInfo', 'gpuInfo', 'anticheatVersion', 'lastUpdated'))
    value += ''.join('{}@{}[{}]{{{}}};'.format(text(e['id']), text(e['version']), text(e['sha256']),
        1 if e['filePresent'] else 0) for e in data['modIds'])
    value += ''.join('{}[{}];'.format(text(e['filename']), text(e['sha256'])) for e in data['resourcePackIds'])
    return hashlib.sha256(value.encode()).hexdigest()


def prepare_adm(original, sha256):
    if integrity(original) != original['integrityHash']: raise ValueError('Existing ADM integrityHash mismatch')
    result = copy.deepcopy(original)
    entries = [entry for entry in result['modIds'] if entry['id'] == build.MOD_ID]
    if len(entries) > 1: raise ValueError('Duplicate title mod IDs in ADM')
    entry = dict(id=build.MOD_ID, version=build.RESOURCE_VERSION, sha256=sha256, filePresent=True)
    if entries and all(entries[0].get(key) == value for key, value in entry.items()): return result
    if entries: entries[0].update(entry)
    else: result['modIds'].append(entry)
    result['modIds'].sort(key=lambda item: item['id'])
    result['lastUpdated'] = int(time.time() * 1000)
    result['integrityHash'] = integrity(result)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    jar = (build.ROOT / 'mods' / build.MOD_NAME).read_bytes()
    sha = hashlib.sha256(jar).hexdigest()
    adm_path = build.ROOT / 'server/config/admanticheat/idfile/admanticheat_id_list.json'
    lock_path = build.ROOT / 'modpack-lock.json'
    original_adm = json.loads(adm_path.read_text(encoding='utf-8-sig'))
    adm = prepare_adm(original_adm, sha)
    original_lock = json.loads(lock_path.read_text())
    lock = copy.deepcopy(original_lock)
    matches = [entry for entry in lock['mods'] if entry['id'] == build.MOD_ID]
    if len(matches) > 1: raise ValueError('Duplicate title mod IDs in modpack lock')
    entry = dict(id=build.MOD_ID, file=build.MOD_NAME, sides=['client', 'server'], version=build.RESOURCE_VERSION,
        added=True, updated=False, source='local:titles', sha256=sha, sha512=hashlib.sha512(jar).hexdigest(),
        size=len(jar), pin_reason='Original Titles.rar font art; resource-only lowcodefml mod. Server transactions in titles/.')
    if matches: matches[0].update(entry)
    else:
        lock['mods'].append(entry)
        lock['client_jar_count'] += 1
        lock['server_jar_count'] += 1
    lock['titles_notes'] = '137 cosmetic titles via /titles; 135 earned through server progression; install matching resource JAR on every client. Does not replace political /title.'
    if args.check:
        if original_adm != adm or original_lock != lock: raise ValueError('Title mod pins need updating')
        print('ADM integrity and title mod pins verified.')
        return
    for path, data in [(adm_path, adm), (lock_path, lock)]:
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Pinned title resource mod; other ADM mods and resource packs retained.')


if __name__ == '__main__': main()
