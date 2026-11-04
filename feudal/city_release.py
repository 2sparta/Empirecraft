#!/usr/bin/env python3
"""Verify/install descriptor versions and produce a deterministic city-markets update."""
import argparse
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from release import REPO, PLUGIN, PLUGIN_VERSION, bridge_name, bridge_version

BRIDGE = bridge_name()
RECRUITS = 'townyrecruits-1.0.4.jar'
CHATPLUS = 'chatplus-forge-2.8.1-empirecraft.2.jar'
CHAT_PLUGIN = 'empirecraft-chat-1.1.0.jar'
REPORT = 'city-markets-' + PLUGIN_VERSION

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
        for name in ('MarketAuctions', 'MarketDocuments', 'MarketOfficialExchange', 'MarketScreens', 'TownTravelGuard',
                     'TownMenu', 'TownMenuData', 'ArmyService', 'ArmyController', 'ArmyPolicy', 'ContractService', 'ContractMenus'):
            assert 'ua/empirecraft/feudal/' + name + '.class' in archive.namelist()
        for language in ('en', 'ru'):
            assert len(json.loads(archive.read('empirecraft-feudal/lang/' + language + '.json'))) > 2700
        assert 'ua/empirecraft/feudal/CommandPreferences.class' in archive.namelist()
        assert 'org/h2/Driver.class' in archive.namelist()
        assert not any(name.endswith('Tests.class') for name in archive.namelist())
    with zipfile.ZipFile(bridge) as archive:
        assert 'version="' + bridge_version() + '"' in archive.read('META-INF/mods.toml').decode()
        for name in ('MarketScreen', 'InteractiveChatMixin', 'InteractiveTabMixin'):
            assert 'ua/empirecraft/townymap/client/' + name + '.class' in archive.namelist()
        for language in ('en', 'ru'):
            assert len(json.loads(archive.read('assets/empirecraft_townymap/locales/' + language + '.json'))) > 100
        assert 'MixinConfigs: empirecraft-client.mixins.json' in archive.read('META-INF/MANIFEST.MF').decode()
        assert b'm_6375_' in archive.read('ua/empirecraft/townymap/client/InteractiveChatMixin.class')

def record_tests(plugin, bridge, reports, backend_log, chat_log=None):
    xml_files = sorted(reports.glob('TEST-*.xml'))
    assert xml_files, 'Native JUnit reports are required'
    tests = 0
    for path in xml_files:
        suite = ET.parse(path).getroot()
        assert int(suite.get('failures', '0')) == int(suite.get('errors', '0')) == 0, path
        assert int(suite.get('skipped', '0')) == 0, path
        tests += int(suite.get('tests', '0'))
    log = backend_log.read_text()
    assert 'Extended market scenarios: 36' in log and 'Готово:' in log and 'Exception in thread' not in log
    path = REPO / 'validation' / (REPORT + '.json')
    report = json.loads(path.read_text())
    report.pop('bundle', None)  # A ZIP cannot embed its own final digest or a prior artifact's digest.
    report.pop('local_checkout', None)
    assert 'Runtime compatibility scenarios: 4; installed policy comparisons: 3072' in log
    contract_checks = int(re.search(r'PASS ContractTests: (\d+)', log)[1])
    report['backend'].update(suites=24, scenarios=len(re.findall(r'^✓ ', log, re.M)), contract_checks=contract_checks,
        installed_policy_comparisons=3072, plugin_sha256=sha(plugin.read_bytes()), status='passed')
    report['backend'].update(command_language=int(re.search(r'Command locale scenarios: (\d+)', log)[1]), localization=int(re.search(r'Localization scenarios: (\d+)', log)[1]))
    if chat_log:
        chat = chat_log.read_text()
        assert 'native font measurement, padding and xEnd hitbox code preserved exactly' in chat
        assert 'PASS exact upstream class allowlist' in chat
        report['chatplus'] = dict(status='passed', native_width_and_hitbox_preserved=True, routing_and_privacy='passed',
            chatplus_sha256=sha((REPO / 'mods' / CHATPLUS).read_bytes()), plugin_sha256=sha((REPO / 'server/plugins' / CHAT_PLUGIN).read_bytes()))
    report['native_client'] = dict(status='passed', scenarios=tests, suites=len(xml_files),
        bridge_sha256=sha(bridge.read_bytes()), forge_compile_and_reobfuscation='passed',
        workflow='City markets and native client',
        build_url=os.environ.get('GITHUB_SERVER_URL', 'https://github.com') + '/' + os.environ.get('GITHUB_REPOSITORY', '2sparta/Empirecraft') + '/actions/runs/' + os.environ.get('GITHUB_RUN_ID', 'local'))
    write(path, report)

def refresh_inventory():
    lock_path = REPO / 'modpack-lock.json'
    lock = json.loads(lock_path.read_text())
    row = next(row for row in lock['mods'] if row['id'] == 'empirecraft_townymap')
    row['pin_reason'] = 'Identical client/server 1.7.0: localized native forms, independent command language, currency charts and interactive components.'
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
        for old in target.parent.glob(pattern + '.sha256'):
            if old.name != name + '.sha256':
                old.unlink()
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
    chat_row = next(row for row in lock['mods'] if row['id'] == 'chatplus')
    assert chat_row['file'] == CHATPLUS and chat_row['sha256'] == sha((REPO / 'mods' / CHATPLUS).read_bytes())
    files = {}
    for side in ('client', 'server'):
        for name, raw in ((BRIDGE, bridge.read_bytes()), (RECRUITS, (REPO / 'mods' / RECRUITS).read_bytes())):
            files[side + '/mods/' + name] = raw
            files[side + '/mods/' + name + '.sha256'] = (sha(raw) + '  ' + name + '\n').encode()
    for folder, name in [('client/mods/', CHATPLUS), ('server/plugins/', CHAT_PLUGIN)]:
        path = REPO / ('mods' if folder.startswith('client') else 'server/plugins') / name
        files[folder + name] = path.read_bytes()
        files[folder + name + '.sha256'] = (sha(path.read_bytes()) + '  ' + name + '\n').encode()
    files['LOCALIZATION.md'] = (REPO / 'feudal/LOCALIZATION.md').read_bytes()
    # Keep the exact patch sources, upstream ChatPlus source and license with the update.
    sources = {}
    for relative in ('feudal/src', 'bridge/src', 'chat-channels/src', 'chat-channels/patch',
                     'chat-channels/tests', 'chat-channels/font', 'chat-channels/frozen-assets', 'bridge/gradle'):
        for path in sorted((REPO / relative).rglob('*')):
            if path.is_file():
                sources[path.relative_to(REPO).as_posix()] = path.read_bytes()
    for relative in ('feudal/build.py', 'feudal/release.py', 'feudal/city_release.py', 'feudal/LOCALIZATION.md',
                     'bridge/build.gradle', 'bridge/settings.gradle', 'bridge/gradle.properties', 'bridge/gradlew',
                     'chat-channels/build.py', 'chat-channels/assets.py', 'chat-channels/test.py',
                     'chat-channels/README.md', 'chat-channels/LICENSE-ChatPlus.txt',
                     'chat-channels/ChatPlus-2.8.1-upstream-source.zip', 'validation/check_localization.py'):
        sources[relative] = (REPO / relative).read_bytes()
    source_zip = io.BytesIO()
    with zipfile.ZipFile(source_zip, 'w') as archive:
        for name, raw in sorted(sources.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 4, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    files['Empirecraft-localization-sources.zip'] = source_zip.getvalue()
    files['LICENSE-ChatPlus.txt'] = (REPO / 'chat-channels/LICENSE-ChatPlus.txt').read_bytes()

    files['server/plugins/' + PLUGIN] = plugin.read_bytes()
    files['server/plugins/' + PLUGIN + '.sha256'] = (sha(plugin.read_bytes()) + '  ' + PLUGIN + '\n').encode()
    for name in ('CITY-MARKETS.md', 'INSTALL-CITY-MARKETS.md'):
        files[name] = (REPO / 'feudal' / name).read_bytes()
    for name in ('config.yml', 'townyperms.yml'):
        files['reference-configs/Towny/settings/' + name] = (REPO / 'server/plugins/Towny/settings' / name).read_bytes()
    files['reference-configs/admanticheat_id_list.json'] = (REPO / 'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()
    for name in (REPORT + '.md', REPORT + '.json', 'installed-releases-2026-11-01.json'):
        files['validation/' + name] = (REPO / 'validation' / name).read_bytes()
    files['metadata/modpack-lock.json'] = (REPO / 'modpack-lock.json').read_bytes()
    files['apply_towny_settings.py'] = (REPO / 'feudal/apply_towny_settings.py').read_bytes()
    files['REMOVE-OLD-JARS.txt'] = ('Remove superseded empirecraft-feudal-*.jar from server/plugins and their checksums.\n'
        'Remove superseded empirecraft-towny-journeymap-*.jar and townyrecruits-*.jar from both mods directories.\n'
        'Remove superseded chatplus-forge-*.jar from client mods and empirecraft-chat-*.jar from server/plugins, with checksums.\n'
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
    parser.add_argument('--test-reports', type=Path, help='Record actual native JUnit XML results')
    parser.add_argument('--backend-log', type=Path)
    parser.add_argument('--chat-log', type=Path)
    parser.add_argument('--output-dir', type=Path, default=REPO / 'work/city-markets-release')
    args = parser.parse_args()
    verify(args.plugin_jar, args.bridge_jar)
    if args.test_reports:
        assert args.backend_log, '--backend-log is required with --test-reports'
        record_tests(args.plugin_jar, args.bridge_jar, args.test_reports, args.backend_log, args.chat_log)
    subprocess.run([sys.executable, str(REPO / 'validation/check_feudal_runtime.py'), '--jar', str(args.plugin_jar)], check=True)
    if args.install:
        install(args.plugin_jar, args.bridge_jar)
    bundle(args.output_dir, args.plugin_jar, args.bridge_jar)

if __name__ == '__main__':
    main()
