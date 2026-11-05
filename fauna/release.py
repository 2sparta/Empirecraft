#!/usr/bin/env python3
"""Install the tested server artifact and create/verify a deterministic release ZIP."""
import argparse
import hashlib
import io
import json
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
NAME = 'empirecraft-fauna-1.0.0.jar'
MID = 'empirecraft_fauna'
INSTALLED = REPO / 'server/mods' / NAME
CONFIG = REPO / 'server/config/empirecraft-fauna.json'
ARCHIVE = HERE / 'dist/Empirecraft-Fauna-1.0.0.zip'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def checksum(path):
    path.with_suffix(path.suffix + '.sha256').write_text(digest(path.read_bytes()) + '  ' + path.name + '\n')


def package_bytes():
    files = {
        'server/mods/' + NAME: INSTALLED.read_bytes(),
        'server/mods/' + NAME + '.sha256': INSTALLED.with_suffix('.jar.sha256').read_bytes(),
        'server/config/empirecraft-fauna.json': CONFIG.read_bytes(),
        'README.uk.md': (HERE / 'README.uk.md').read_bytes(),
        'README.ru.md': (HERE / 'README.ru.md').read_bytes(),
    }
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, raw in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 4, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    return out.getvalue()


def install():
    built = HERE / 'build/libs' / NAME
    native = json.loads((HERE / 'build/native/result.json').read_text())
    assert native['fauna_sha256'] == digest(built.read_bytes()), 'Native checks must exercise the release JAR'
    assert len(native['phases']) == 2 and all('PASS' in phase for phase in native['phases'])
    shutil.copyfile(built, INSTALLED)
    checksum(INSTALLED)
    defaults = HERE / 'build/defaults/empirecraft-fauna.json'
    if CONFIG.exists():
        assert CONFIG.read_bytes() == defaults.read_bytes(), 'Review existing settings before replacing them'
    else:
        shutil.copyfile(defaults, CONFIG)
    lock_path = REPO / 'modpack-lock.json'
    lock = json.loads(lock_path.read_text())
    rows = [row for row in lock['mods'] if row['id'] == MID]
    if not rows:
        rows = [dict(id=MID, file=NAME, version='1.0.0')]
        lock['mods'].extend(rows)
    rows[0].update(source='local:fauna', updated=True,
                   pin_reason='Server-only seasonal density and persistent Towny livestock capacity; fauna/ contains source, locales and native checks.')
    write_json(lock_path, lock)
    subprocess.run([sys.executable, str(REPO / 'validation/reconcile_modpack.py'), '--apply'], check=True)

    raw = INSTALLED.read_bytes()
    inventory_path = REPO / 'seasonal-calendar/installed-mods.json'
    inventory = [row for row in json.loads(inventory_path.read_text()) if row['name'] != NAME]
    inventory.append(dict(name=NAME, path='server/mods/' + NAME, size=len(raw),
                          sha=hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()))
    write_json(inventory_path, sorted(inventory, key=lambda row: row['name']))
    coverage_path = REPO / 'seasonal-calendar/audit/coverage.json'
    coverage = json.loads(coverage_path.read_text())
    coverage['jar_sha256'][NAME] = digest(raw)
    coverage['jar_sha256'] = dict(sorted(coverage['jar_sha256'].items()))
    coverage['jar_count'] = len(coverage['jar_sha256'])
    write_json(coverage_path, coverage)
    retained_path = REPO / 'native-content/retained-code.json'
    retained = json.loads(retained_path.read_text())
    with zipfile.ZipFile(INSTALLED) as jar:
        retained[NAME] = {name: digest(jar.read(name)) for name in sorted(jar.namelist()) if name.endswith('.class')}
    write_json(retained_path, retained)
    manifest_path = REPO / 'validation/installed-releases-2026-11-01.json'
    manifest = json.loads(manifest_path.read_text())
    row = next(row for row in json.loads(lock_path.read_text())['mods'] if row['id'] == MID)
    manifest['releases'] = [entry for entry in manifest['releases'] if entry['id'] != MID]
    manifest['releases'].append(dict(row, artifact='server/mods/' + NAME))
    write_json(manifest_path, manifest)
    suites = [ET.parse(path).getroot() for path in (HERE / 'build/test-results/test').glob('TEST-*.xml')]
    assert suites and all(int(suite.attrib['failures']) == int(suite.attrib['errors']) == 0 for suite in suites)
    report = dict(version='1.0.0', server_only=True, fauna_sha256=digest(raw),
                  config_sha256=digest(CONFIG.read_bytes()), junit_tests=sum(int(s.attrib['tests']) for s in suites), native=native)
    write_json(HERE / 'audit/release.json', report)
    ARCHIVE.parent.mkdir(parents=True, exist_ok=True);ARCHIVE.write_bytes(package_bytes());checksum(ARCHIVE)


def check():
    raw = INSTALLED.read_bytes()
    report = json.loads((HERE / 'audit/release.json').read_text())
    assert report['fauna_sha256'] == report['native']['fauna_sha256'] == digest(raw)
    assert report['config_sha256'] == digest(CONFIG.read_bytes())
    assert INSTALLED.with_suffix('.jar.sha256').read_text() == digest(raw) + '  ' + NAME + '\n'
    assert not (REPO / 'mods' / NAME).exists(), 'Fauna is server-only'
    with zipfile.ZipFile(INSTALLED) as jar:
        assert jar.testzip() is None
        assert not any('faunasmoke' in entry for entry in jar.namelist())
        uk = json.loads(jar.read('assets/empirecraft_fauna/lang/uk_ua.json'))
        ru = json.loads(jar.read('assets/empirecraft_fauna/lang/ru_ru.json'))
        assert uk.keys() == ru.keys()
        assert 'СВІЙСЬКІ' in uk['domestic'] and 'СЕЛЬСКОХОЗЯЙСТВЕННЫЕ' in ru['domestic']
        for name in jar.namelist():
            if name.endswith('/'):
                continue
            if name.startswith('assets/') or name.endswith(('.toml', '.mcmeta', 'mixins.json')):
                assert jar.read(name) == (HERE / 'src/main/resources' / name).read_bytes(), name
    assert ARCHIVE.read_bytes() == package_bytes(), 'Stale release ZIP'
    assert ARCHIVE.with_suffix('.zip.sha256').read_text() == digest(ARCHIVE.read_bytes()) + '  ' + ARCHIVE.name + '\n'
    print('PASS: tested server JAR, both locales, native restart report and deterministic release ZIP')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.install:
        install()
    check()
