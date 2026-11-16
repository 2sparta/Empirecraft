#!/usr/bin/env python3
"""Install the tested bandit-map release into this checkout and verify its lock, sums and ADM."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
ID = 'empirecraft_townymap'
VERSION = '1.12.0'
NAME = 'empirecraft-towny-journeymap-' + VERSION + '.jar'


def encode(data):
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    source = ROOT / 'dist' / NAME
    raw = source.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    report = json.loads((ROOT / 'dist/bandits-validation.json').read_text())
    assert report['status'] == 'passed' and report['sha256'] == digest and report['tests_passed'] > 0
    with zipfile.ZipFile(source) as jar:
        assert jar.testzip() is None
        assert any(m['modId'] == ID and m['version'] == VERSION
                   for m in tomllib.loads(jar.read('META-INF/mods.toml').decode())['mods'])
    spec = importlib.util.spec_from_file_location('fleet_installer', REPO / 'fleet-menu/update_installation.py')
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    lockpath = REPO / 'modpack-lock.json'
    manifestpath = REPO / 'validation/installed-releases-2026-11-01.json'
    admpath = REPO / 'server/config/admanticheat/idfile/admanticheat_id_list.json'
    lock = json.loads(lockpath.read_text())
    manifest = json.loads(manifestpath.read_text())
    admtext = admpath.read_text(encoding='utf-8-sig')
    adm, adm_is_report = helper.parse_adm(admtext)
    rows = [r for r in lock['mods'] if r['id'] == ID]
    releases = [r for r in manifest['releases'] if r['id'] == ID]
    assert len(rows) == len(releases) == 1
    entry = dict(rows[0])
    entry.update(file=NAME, version=VERSION, sha256=digest, sha512=hashlib.sha512(raw).hexdigest(),
                 size=len(raw), git_sha=hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest(),
                 pin_reason='1.12.0: temporary bandit-country overlays within 16 blocks; bridge/dist/bandits-validation.json.')
    release = dict(entry, artifact='mods/' + NAME)
    updated_adm = helper.adm_update(adm, entry)
    assert [r for r in updated_adm['modIds'] if r['id'] != ID] == [r for r in adm['modIds'] if r['id'] != ID]
    assert updated_adm['resourcePackIds'] == adm['resourcePackIds']
    oldfiles = [p for folder in ['mods', 'server/mods']
                for p in (REPO / folder).glob('empirecraft-towny-journeymap-*.jar') if p.name != NAME]
    baseline = ROOT / 'native-inputs/empirecraft-towny-journeymap-1.11.2.jar'
    assert all(p.read_bytes() == baseline.read_bytes() for p in oldfiles), 'Unexpected installed bridge; preserve it before upgrading'
    checksum = digest + '  ' + NAME + '\n'
    if args.check:
        assert not oldfiles
        assert rows[0] == entry and releases[0] == release
        assert adm == updated_adm
        for folder in ['mods', 'server/mods']:
            installed = REPO / folder / NAME
            assert installed.read_bytes() == raw
            assert installed.with_suffix('.jar.sha256').read_text() == checksum
        assert source.with_suffix('.jar.sha256').read_text() == checksum
        print('BANDIT_MAP_INSTALL_CHECK_PASS: client/server bytes, version, duplicate bridges, lock, SHA-256/SHA-512, Git blob and ADM integrity')
        return
    writes = {lockpath: None, manifestpath: None,
              admpath: helper.render_adm(admtext, adm, updated_adm, adm_is_report).encode()}
    for folder in ['mods', 'server/mods']:
        writes[REPO / folder / NAME] = raw
        writes[REPO / folder / (NAME + '.sha256')] = checksum.encode()
    rows[0].update(entry)
    releases[0].clear()
    releases[0].update(release)
    for old in oldfiles:
        relative = old.relative_to(REPO).as_posix()
        if relative not in manifest['retired']:
            manifest['retired'].append(relative)
        manifest['retired_artifacts'][relative] = baseline.relative_to(REPO).as_posix()
    writes[lockpath] = encode(lock)
    writes[manifestpath] = encode(manifest)
    for path, contents in writes.items():
        path.write_bytes(contents)
    for old in oldfiles:
        old.unlink()
        old.with_suffix('.jar.sha256').unlink(missing_ok=True)
    print('BANDIT_MAP_INSTALL_PASS: installed in this checkout; unrelated ADM rows and resource packs preserved')


if __name__ == '__main__':
    main()
