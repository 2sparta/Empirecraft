#!/usr/bin/env python3
"""Verify the installed November release artifacts, side allocation, lock and ADM."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def main():
    manifest = json.loads((ROOT/'validation/installed-releases-2026-11-01.json').read_text())
    lock = json.loads((ROOT/'modpack-lock.json').read_text())
    spec = importlib.util.spec_from_file_location('ship_release', ROOT/'ship-cannons/release.py')
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    adm, _ = helper.parse_adm((ROOT/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text())
    for row in manifest['releases']:
        source = ROOT/row['artifact']
        raw = source.read_bytes()
        assert hashlib.sha256(raw).hexdigest() == row['sha256'], source
        assert hashlib.sha512(raw).hexdigest() == row['sha512'], source
        assert len(raw) == row['size'], source
        assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest() == row['git_sha'], source
        assert source.with_suffix('.jar.sha256').read_text().split()[0] == row['sha256'], source
        with zipfile.ZipFile(source) as archive:
            assert archive.testzip() is None, source
            mods = tomllib.loads(archive.read('META-INF/mods.toml').decode())['mods']
            assert any(m['modId'] == row['id'] and m['version'] == row['version'] for m in mods), source
        matches = [m for m in lock['mods'] if m['id'] == row['id']]
        assert matches == [{k: v for k, v in row.items() if k != 'artifact'}], row['id']
        for side, folder in [('client', 'mods'), ('server', 'server/mods')]:
            installed = ROOT/folder/row['file']
            if side in row['sides']:
                assert installed.read_bytes() == raw, installed
                assert installed.with_suffix('.jar.sha256').read_text() == row['sha256']+'  '+row['file']+'\n', installed
            else:
                assert not installed.exists(), installed
            ids = []
            for jar in (ROOT/folder).glob('*.jar'):
                with zipfile.ZipFile(jar) as archive:
                    if 'META-INF/mods.toml' in archive.namelist():
                        ids.extend(m['modId'] for m in tomllib.loads(archive.read('META-INF/mods.toml').decode())['mods'])
            assert ids.count(row['id']) == int(side in row['sides']), (side, row['id'])
        if 'client' in row['sides']:
            assert [m for m in adm['modIds'] if m['id'] == row['id']] == [dict(
                id=row['id'], version=row['version'], sha256=row['sha256'], filePresent=True)], row['id']
        else:
            assert not any(m['id'] == row['id'] for m in adm['modIds']), row['id']
    for relative in manifest['retired']:
        assert not (ROOT/relative).exists(), relative
        assert (ROOT/'disabled-mods/replaced-2026-11-01'/relative).is_file(), relative
    for side, folder in [('client', 'mods'), ('server', 'server/mods')]:
        count = len(list((ROOT/folder).glob('*.jar')))
        assert lock[side+'_jar_count'] == manifest[side+'_jar_count'] == count, side
    assert lock['updated_mod_count'] == sum(bool(m.get('updated')) for m in lock['mods'])
    print('PASS: release bytes, versions, side allocation, duplicate IDs, SHA-256/SHA-512, lock, ADM integrity and retired bridges')


if __name__ == '__main__':
    main()
