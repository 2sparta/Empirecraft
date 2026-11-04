#!/usr/bin/env python3
"""Verify/install descriptor versions and produce a deterministic city-markets update."""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
from release import REPO, PLUGIN, PLUGIN_VERSION, bridge_name, bridge_version

BRIDGE = bridge_name()
RECRUITS = 'townyrecruits-1.0.4.jar'

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def checksum(path):
    path.with_suffix(path.suffix + '.sha256').write_text(sha(path.read_bytes()) + '  ' + path.name + '\n')

def verify(plugin, bridge):
    for path in (plugin, bridge):
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None, path
    with zipfile.ZipFile(plugin) as archive:
        assert 'version: ' + PLUGIN_VERSION + '\n' in archive.read('plugin.yml').decode()
        for name in ('MarketAuctions', 'MarketDocuments', 'MarketOfficialExchange', 'MarketScreens', 'TownTravelGuard'):
            assert 'ua/empirecraft/feudal/' + name + '.class' in archive.namelist()
        assert 'org/h2/Driver.class' in archive.namelist()
        assert not any(name.endswith('Tests.class') for name in archive.namelist())
    with zipfile.ZipFile(bridge) as archive:
        assert 'version="' + bridge_version() + '"' in archive.read('META-INF/mods.toml').decode()
        for name in ('MarketScreen', 'InteractiveChatMixin', 'InteractiveTabMixin'):
            assert 'ua/empirecraft/townymap/client/' + name + '.class' in archive.namelist()
        assert 'MixinConfigs: empirecraft-client.mixins.json' in archive.read('META-INF/MANIFEST.MF').decode()
        assert b'm_6375_' in archive.read('ua/empirecraft/townymap/client/InteractiveChatMixin.class')

def refresh_inventory():
    lock_path = REPO / 'modpack-lock.json'
    lock = json.loads(lock_path.read_text())
    row = next(row for row in lock['mods'] if row['id'] == 'empirecraft_townymap')
    row['pin_reason'] = 'Identical client/server 1.6.0: native city markets, currency charts and ChatPlus interactive components.'
    write(lock_path, lock)
    subprocess.run([sys.executable, str(REPO / 'validation/reconcile_modpack.py'), '--apply'], check=True)
    for folder, target in [('server/mods', 'seasonal-calendar/installed-mods.json'),
                           ('mods', 'forging-balance/installed-client-mods.json')]:
        rows = []
        for jar in sorted((REPO / folder).glob('*.jar')):
            raw = jar.read_bytes()
            rows.append(dict(name=jar.name, path=folder + '/' + jar.name, size=len(raw),
                sha=hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()))
        write(REPO / target, rows)
    path = REPO / 'seasonal-calendar/audit/coverage.json'
    coverage = json.loads(path.read_text())
    coverage['jar_sha256'] = {jar.name: sha(jar.read_bytes()) for jar in sorted((REPO / 'server/mods').glob('*.jar'))}
    coverage['jar_count'] = len(coverage['jar_sha256'])
    write(path, coverage)

def install(plugin, bridge):
    for folder, source, name, pattern in [('server/plugins', plugin, PLUGIN, 'empirecraft-feudal-*.jar'),
        ('mods', bridge, BRIDGE, 'empirecraft-towny-journeymap-*.jar'),
        ('server/mods', bridge, BRIDGE, 'empirecraft-towny-journeymap-*.jar')]:
        target = REPO / folder / name
        target.parent.mkdir(parents=True, exist_ok=True)
        for old in target.parent.glob(pattern):
            if old.name != name:
                old.unlink()
                old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        if source.resolve() != target.resolve():
            shutil.copyfile(source, target)
        checksum(target)
    refresh_inventory()
    for checker in ('reconcile_modpack.py', 'check_installed_releases.py'):
        subprocess.run([sys.executable, str(REPO / 'validation' / checker)], check=True)

def bundle(destination, plugin, bridge):
    assert (REPO / 'mods' / RECRUITS).read_bytes() == (REPO / 'server/mods' / RECRUITS).read_bytes()
    # A packaged ADM/lock must describe the exact included binary.
    lock = json.loads((REPO / 'modpack-lock.json').read_text())
    row = next(row for row in lock['mods'] if row['id'] == 'empirecraft_townymap')
    assert row['sha256'] == sha(bridge.read_bytes()) and row['version'] == bridge_version()
    files = {}
    for side in ('client', 'server'):
        for name, raw in ((BRIDGE, bridge.read_bytes()), (RECRUITS, (REPO / 'mods' / RECRUITS).read_bytes())):
            files[side + '/mods/' + name] = raw
            files[side + '/mods/' + name + '.sha256'] = (sha(raw) + '  ' + name + '\n').encode()
    files['server/plugins/' + PLUGIN] = plugin.read_bytes()
    files['server/plugins/' + PLUGIN + '.sha256'] = (sha(plugin.read_bytes()) + '  ' + PLUGIN + '\n').encode()
    for name in ('CITY-MARKETS.md', 'INSTALL-CITY-MARKETS.md'):
        files[name] = (REPO / 'feudal' / name).read_bytes()
    for name in ('config.yml', 'townyperms.yml'):
        files['reference-configs/Towny/settings/' + name] = (REPO / 'server/plugins/Towny/settings' / name).read_bytes()
    files['reference-configs/admanticheat_id_list.json'] = (REPO / 'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
    for name in ('city-markets-1.11.0.md', 'city-markets-1.11.0.json', 'installed-releases-2026-11-01.json'):
        files['validation/' + name] = (REPO / 'validation' / name).read_bytes()
    files['metadata/modpack-lock.json'] = (REPO / 'modpack-lock.json').read_bytes()
    files['apply_towny_settings.py'] = (REPO / 'feudal/apply_towny_settings.py').read_bytes()
    files['REMOVE-OLD-JARS.txt'] = ('Remove superseded empirecraft-feudal-*.jar from server/plugins and their checksums.\n'
        'Remove superseded empirecraft-towny-journeymap-*.jar and townyrecruits-*.jar from both mods directories.\n'
        'Keep exactly the versions present in this bundle. Preserve worlds, playerdata and the Feudal database.\n').encode()
    files['SHA256SUMS.txt'] = ''.join(sha(raw) + '  ' + name + '\n' for name, raw in sorted(files.items())).encode()
    destination.mkdir(parents=True, exist_ok=True)
    path = destination / ('Empirecraft-City-Markets-' + PLUGIN_VERSION + '-bundle.zip')
    with zipfile.ZipFile(path, 'w') as archive:
        for name, raw in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 4, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
    checksum(path)
    print(path, '\nSHA-256:', sha(path.read_bytes()))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plugin-jar', type=Path, default=REPO / 'server/plugins' / PLUGIN)
    parser.add_argument('--bridge-jar', type=Path, default=REPO / 'bridge/build/libs' / BRIDGE)
    parser.add_argument('--install', action='store_true', help='Install into this checkout and refresh inventories')
    parser.add_argument('--output-dir', type=Path, default=REPO / 'work/city-markets-release')
    args = parser.parse_args()
    verify(args.plugin_jar, args.bridge_jar)
    if args.install:
        install(args.plugin_jar, args.bridge_jar)
    bundle(args.output_dir, args.plugin_jar, args.bridge_jar)

if __name__ == '__main__':
    main()
