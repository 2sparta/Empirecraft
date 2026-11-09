#!/usr/bin/env python3
"""Record exact executed fleet/startup proof and package a cumulative hotfix."""
import argparse
import hashlib
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
PLUGIN = 'empirecraft-feudal-1.19.2.jar'
PREVIOUS = 'empirecraft-feudal-1.19.1.jar'
CAUPONA = 'caupona-1.20.1-0.4.14.jar'
REPORT = REPO / 'validation/startup-fixes-1.json'
RETIRED = ['server/mods/worldedit-mod-7.2.15.jar',
           'server/plugins/PlaceholderAPI/expansions/Expansion-townychat.jar']


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checksum(path):
    path.with_suffix(path.suffix + '.sha256').write_text(sha(path) + '  ' + path.name + '\n')


def clean_log(text):
    plain = re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]', '', text)
    return '\n'.join(line.rstrip() for line in plain.splitlines()) + '\n'


def archive(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, raw in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 11, 9, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            z.writestr(entry, raw)
    checksum(path)


def verify():
    report = json.loads(REPORT.read_text())
    assert report['status'] == 'passed'
    plugin = REPO / 'server/plugins' / PLUGIN
    assert report['plugin']['sha256'] == report['native']['plugin_sha256'] == sha(plugin)
    assert report['supersedes'][PREVIOUS] == sha(REPO / 'naval/dist/fleet-fixes-1.1' / PREVIOUS)
    assert list((REPO / 'server/plugins').glob('empirecraft-feudal-*.jar')) == [plugin]
    assert (REPO / 'mods' / CAUPONA).read_bytes() == (REPO / 'server/mods' / CAUPONA).read_bytes()
    assert sha(REPO / 'mods' / CAUPONA) == report['caupona']['sha256']
    assert len(list((REPO / 'mods').glob('caupona-*.jar'))) == len(list((REPO / 'server/mods').glob('caupona-*.jar'))) == 1
    for name in RETIRED:
        assert not (REPO / name).exists()
        assert sha(REPO / 'disabled-mods/startup-fixes-1' / name) == report['disabled'][name]
    for name, digest in report['native']['plugin_sha256_inventory'].items():
        assert sha(REPO / 'server/plugins' / name) == digest
    return report


def verify_package():
    target = ROOT / 'dist/Empirecraft-Startup-Hotfix-1.zip'
    assert target.with_suffix('.zip.sha256').read_text() == sha(target) + '  ' + target.name + '\n'
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        expected = ''.join(hashlib.sha256(z.read(name)).hexdigest() + '  ' + name + '\n' for name in sorted(z.namelist()) if name != 'SHA256SUMS.txt')
        assert z.read('SHA256SUMS.txt').decode() == expected
        assert z.read('verification-startup.json') == REPORT.read_bytes()
        assert z.read('reference/modpack-lock.json') == (REPO / 'modpack-lock.json').read_bytes()
        assert z.read('server/plugins/' + PLUGIN) == (REPO / 'server/plugins' / PLUGIN).read_bytes()
        assert z.read('client/mods/' + CAUPONA) == z.read('server/mods/' + CAUPONA) == (REPO / 'mods' / CAUPONA).read_bytes()
        assert not any(name.startswith('server/plugins/empirecraft-feudal-') and name not in ('server/plugins/' + PLUGIN, 'server/plugins/' + PLUGIN + '.sha256') for name in z.namelist())
        assert not any(name in z.namelist() for name in RETIRED)


def record(log):
    text = log.read_text()
    assert 'DISCORD_TESTS_PASS' in text and 'Готово:' in text and 'Exception in thread' not in text
    native = json.loads((REPO / 'naval/build/logs/result.json').read_text())
    assert native['startup_plugins'] and native['server_restarts'] == 1
    assert len(native['results']) == 2 and all('FLEET_NATIVE_PASS' in row for row in native['results'])
    native['results'] = [re.search(r'FLEET_NATIVE_PASS[^\x1b\r\n]*', row)[0].strip() for row in native['results']]
    log_hashes = {}
    for phase in ('first', 'restart'):
        path = REPO / 'naval/build/logs' / (phase + '.log')
        body = path.read_text()
        assert 'FLEET_NATIVE_FAIL' not in body and 'Holding not found' not in body and 'Флот:' not in body
        assert body.count('retains unavailable owner') == 1
        log_hashes[phase] = dict(raw_sha256=sha(path), audit_sha256=hashlib.sha256(clean_log(body).encode()).hexdigest())
    assert native['plugin_sha256'] in text
    report = dict(status='passed', plugin=dict(file=PLUGIN, sha256=sha(REPO / 'server/plugins' / PLUGIN)),
        supersedes={PREVIOUS: sha(REPO / 'naval/dist/fleet-fixes-1.1' / PREVIOUS)},
        supersedes_archives={name: sha(REPO / name) for name in ('naval/dist/fleet-fixes-1.1/Empirecraft-Fleet-Fixes-1.1-source.zip',)},
        backend=dict(suites=32, scenarios=len(re.findall(r'^✓ ', text, re.M)), log_sha256=sha(log), audit_log_sha256=hashlib.sha256(clean_log(text).encode()).hexdigest()),
        native=native, fixture_source_sha256=sha(REPO / 'naval/tests/FleetSmokeMod.java'),
        native_logs=log_hashes, audit_log_normalization='ANSI escape sequences removed; trailing whitespace stripped; raw and normalized hashes recorded separately',
        disabled={name: sha(REPO / 'disabled-mods/startup-fixes-1' / name) for name in RETIRED},
        caupona=dict(file=CAUPONA, sha256=sha(REPO / 'mods' / CAUPONA), same_bytes_on_both_sides=True,
                     remote_client_file_known=False, handshake_checks_disabled=False),
        graphical_client_tested=False, production_world_tested=False)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    audit = ROOT / 'audit';audit.mkdir(exist_ok=True)
    (audit / 'feudal-build.log').write_text(clean_log(text))
    for name in ('first.log', 'restart.log', 'result.json'):
        source = REPO / 'naval/build/logs' / name
        if name.endswith('.log'):
            (audit / name).write_text(clean_log(source.read_text()))
        else:
            shutil.copyfile(source, audit / name)
    return verify()


def package():
    verify()
    # Start with the preceding combined update. Never reinstall its retired plugin.
    with zipfile.ZipFile(REPO / 'survival-overhaul/dist/Empirecraft-Survival-Update-1.zip') as z:
        files = {n: z.read(n) for n in z.namelist() if n != 'SHA256SUMS.txt' and not n.startswith('server/plugins/empirecraft-feudal-')}
    for side in ('client', 'server'):
        for suffix in ('', '.sha256'):
            files[side + '/mods/' + CAUPONA + suffix] = (REPO / ('mods' if side == 'client' else 'server/mods') / (CAUPONA + suffix)).read_bytes()
    for name in (PLUGIN, PLUGIN + '.sha256', 'worldedit-bukkit-7.3.1 (2).jar'):
        files['server/plugins/' + name] = (REPO / 'server/plugins' / name).read_bytes()
    files['INSTALL-STARTUP-UA.md'] = (ROOT / 'README.md').read_bytes()
    files['repair_worlds.py'] = (ROOT / 'repair_worlds.py').read_bytes()
    files['verification-startup.json'] = REPORT.read_bytes()
    files['reference/modpack-lock.json'] = (REPO / 'modpack-lock.json').read_bytes()
    files['REMOVE-OLD.txt'] += ('\nSTARTUP HOTFIX 1:\nRemove all older server/plugins/empirecraft-feudal-*.jar and checksums; keep only 1.19.2.\n'
        'Remove server mods/worldedit-mod-7.2.15.jar and checksum; KEEP the bundled Bukkit WorldEdit.\n'
        'Remove server plugins/PlaceholderAPI/expansions/Expansion-townychat.jar (requires absent TownyChat).\n'
        'Remove other caupona-*.jar from BOTH mods folders and install the identical included 0.4.14 JAR.\n').encode()
    files['SHA256SUMS.txt'] = ''.join(hashlib.sha256(raw).hexdigest() + '  ' + name + '\n' for name, raw in sorted(files.items())).encode()
    target = ROOT / 'dist/Empirecraft-Startup-Hotfix-1.zip'
    archive(target, files)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        assert 'server/plugins/' + PREVIOUS not in z.namelist()
        assert z.read('client/mods/' + CAUPONA) == z.read('server/mods/' + CAUPONA)
    sources = {}
    for folder in ('feudal/src', 'naval/tests', 'startup-fixes'):
        for path in sorted((REPO / folder).rglob('*')):
            if path.is_file() and not any(part in ('dist', 'audit', '__pycache__') for part in path.parts):
                sources[str(path.relative_to(REPO))] = path.read_bytes()
    sources['feudal/build.py'] = (REPO / 'feudal/build.py').read_bytes()
    sources['feudal/release.py'] = (REPO / 'feudal/release.py').read_bytes()
    sources['naval/smoke.py'] = (REPO / 'naval/smoke.py').read_bytes()
    archive(ROOT / 'dist/Empirecraft-Startup-Hotfix-1-sources.zip', sources)
    verify_package()
    print('PASS:', target)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend-log', type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.backend_log:
        record(args.backend_log)
    if args.check:
        verify();verify_package();print('PASS: exact fleet proof, successor, disabled conflicts, paired Caupona and complete archive checksums')
    else:
        package()
