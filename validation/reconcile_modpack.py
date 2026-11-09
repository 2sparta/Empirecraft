#!/usr/bin/env python3
"""Inventory installed JAR bytes, synchronize lock/ADM and reject divergent shared mods."""
import argparse
import copy
import hashlib
import importlib.util
import json
import re
import time
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def inventory():
    old = json.loads((ROOT / 'modpack-lock.json').read_text())
    previous = {row['id']: row for row in old['mods']}
    by_file = {row['file']: row for row in old['mods']}
    rows, declared, counts = {}, {}, {}
    for side, folder in [('client', 'mods'), ('server', 'server/mods')]:
        jars = sorted((ROOT / folder).glob('*.jar'))
        counts[side + '_jar_count'] = len(jars)
        for jar in jars:
            raw = jar.read_bytes()
            sha = hashlib.sha256(raw).hexdigest()
            with zipfile.ZipFile(jar) as archive:
                assert archive.testzip() is None, jar
                if 'META-INF/mods.toml' in archive.namelist():
                    mods = tomllib.loads(archive.read('META-INF/mods.toml').decode())['mods']
                else:
                    known = by_file.get(jar.name)
                    assert known is not None, ('Unrecognized library', jar)
                    mods = [dict(modId=known['id'], version=known['version'])]
                manifest = archive.read('META-INF/MANIFEST.MF').decode(errors='replace') if 'META-INF/MANIFEST.MF' in archive.namelist() else ''
                version = re.search(r'^Implementation-Version: ([^\r\n]+)', manifest, re.M)
                for mod in mods:
                    mod['version'] = str(mod['version']).replace('${file.jarVersion}', version[1] if version else '')
                    assert mod['version'] and '${' not in mod['version'], jar
                    key = (side, mod['modId'])
                    assert key not in declared, ('Duplicate mod ID', key, jar)
                    declared[key] = dict(id=mod['modId'], version=mod['version'], sha256=sha, filePresent=True)
            primary = mods[0]
            mid = primary['modId']
            if mid in rows:
                assert rows[mid]['file'] == jar.name and rows[mid]['sha256'] == sha, ('Shared mod differs between sides', mid)
                rows[mid]['sides'].append(side)
                continue
            row = copy.deepcopy(previous.get(mid, by_file.get(jar.name, {})))
            row.update(id=mid, file=jar.name, version=primary['version'], sides=[side],
                       sha256=sha, sha512=hashlib.sha512(raw).hexdigest(), size=len(raw),
                       git_sha=hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest())
            row.setdefault('source', 'installed:repository')
            row.setdefault('updated', True)
            rows[mid] = row
    result = copy.deepcopy(old)
    result.update(counts)
    result['mods'] = sorted(rows.values(), key=lambda row: row['id'])
    result['updated_mod_count'] = sum(bool(row.get('updated')) for row in result['mods'])
    return old, result, declared


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    old, planned, declared = inventory()
    spec = importlib.util.spec_from_file_location('ship_release', ROOT / 'ship-cannons/release.py')
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    adm_path = ROOT / 'server/config/admanticheat/idfile/admanticheat_id_list.json'
    adm, _ = helper.parse_adm(adm_path.read_text())
    updated = copy.deepcopy(adm)
    client = {mid: row for (side, mid), row in declared.items() if side == 'client'}
    updated['modIds'] = [row for row in updated['modIds'] if not row['filePresent'] or row['id'] in client]
    indexed = {row['id']: row for row in updated['modIds']}
    for mid, row in client.items():
        if mid in indexed:
            indexed[mid].update(row)
        else:
            updated['modIds'].append(row)
    if updated['modIds'] != adm['modIds']:
        updated['lastUpdated'] = int(time.time() * 1000)
        updated['integrityHash'] = helper.integrity(updated)
    encode = lambda value: json.dumps(value, ensure_ascii=False, indent=2) + '\n'
    if args.apply:
        (ROOT / 'modpack-lock.json').write_text(encode(planned))
        if adm != updated:
            adm_path.write_text(encode(updated))
        for folder in ['mods', 'server/mods']:
            for jar in (ROOT / folder).glob('*.jar'):
                jar.with_suffix('.jar.sha256').write_text(hashlib.sha256(jar.read_bytes()).hexdigest() + '  ' + jar.name + '\n')
        manifest_path = ROOT / 'validation/installed-releases-2026-11-01.json'
        manifest = json.loads(manifest_path.read_text())
        for i, release in enumerate(manifest['releases']):
            row = next(row for row in planned['mods'] if row['id'] == release['id'])
            artifact = release['artifact']
            if row['id'] == 'townyrecruits':
                candidates = [Path(artifact), Path('towny-recruits/dist') / row['version'] / row['file'], Path('towny-recruits/dist') / row['file']]
                artifact = next(str(path) for path in candidates if (ROOT / path).is_file() and hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == row['sha256'])
            elif artifact.startswith(('server/mods/', 'mods/')):
                artifact = str(Path(artifact).parent / row['file'])
            assert hashlib.sha256((ROOT / artifact).read_bytes()).hexdigest() == row['sha256'], artifact
            manifest['releases'][i] = dict(row, artifact=artifact)
        manifest.update({key: planned[key] for key in ['client_jar_count', 'server_jar_count']})
        manifest_path.write_text(encode(manifest))
    else:
        assert old == planned, 'Stale modpack-lock.json; review inventory and run --apply'
        assert adm == updated, 'Stale client ADM inventory'
        for folder in ['mods', 'server/mods']:
            for jar in (ROOT / folder).glob('*.jar'):
                assert jar.with_suffix('.jar.sha256').read_text() == hashlib.sha256(jar.read_bytes()).hexdigest() + '  ' + jar.name + '\n', jar
    print('PASS: all installed JAR identities, versions, side allocation, shared bytes, hashes, counts and ADM')


if __name__ == '__main__':
    main()
