#!/usr/bin/env python3
"""Install, validate and package the matching city fleet client/server components."""
import argparse
import hashlib
import io
import json
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
sys.path.insert(0, str(REPO / 'feudal'))
import city_release as city

SAILS = 'vs_sails-1.20.1-0.3.2-empirecraft.18-forge.jar'
PHYSICS = 'empirecraft-ship-physics-1.2.2.jar'
REPORT = REPO / 'validation/city-fleet-17.json'
OUTPUT = ROOT / 'dist/Empirecraft-City-Fleet-17.zip'

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def run(*args):
    subprocess.run([sys.executable, *map(str, args)], check=True)

def artifacts():
    return [REPO / 'server/plugins' / city.PLUGIN,
            *[REPO / side / name for side in ('mods', 'server/mods')
              for name in (SAILS, city.BRIDGE, PHYSICS,city.RECRUITS,'empirecraft-engagement-1.1.0.jar')]]

def install(plugin, bridge):
    city.verify(plugin, bridge)
    # This also retires the previous Sails JAR and records its native input.
    run(REPO / 'ship-physics/release.py', '--apply')
    for directory, source, name, pattern in (
        ('server/plugins', plugin, city.PLUGIN, 'empirecraft-feudal-*.jar'),
        ('mods', bridge, city.BRIDGE, 'empirecraft-towny-journeymap-*.jar'),
        ('server/mods', bridge, city.BRIDGE, 'empirecraft-towny-journeymap-*.jar')):
        folder = REPO / directory
        for old in folder.glob(pattern):
            if old.name != name:
                old.unlink()
                old.with_suffix('.jar.sha256').unlink(missing_ok=True)
        target = folder / name
        if source.resolve() != target.resolve():
            shutil.copyfile(source, target)
        city.checksum(target)
    city.refresh_inventory()
    lock = json.loads((REPO / 'modpack-lock.json').read_text())
    next(row for row in lock['mods'] if row['id'] == 'empirecraft_townymap')['pin_reason'] = (
        'Identical client/server 1.10.0: city fleet menus, bounded sender-bound actions, '
        'private visible/last-seen allied ship markers and JourneyMap hide persistence; '
        'retains visited claims, Contact fair mail and localized interactive chat.')
    write(REPO / 'modpack-lock.json', lock)
    run(REPO / 'validation/reconcile_modpack.py', '--apply')
    # Paired distributions must embed the final ADM, including the new bridge.
    run(REPO / 'sails/release_smooth.py')
    run(REPO / 'ship-physics/release.py')

def record(log, reports, native_report):
    backend = log.read_text()
    assert 'Готово:' in backend and 'Exception in thread' not in backend
    fleet_cases = int(re.search(r'FLEET_SERVICE_PASS cases=(\d+)', backend)[1])
    assert fleet_cases >= 13
    suites = [ET.parse(p).getroot() for p in sorted(reports.glob('TEST-*.xml'))]
    assert suites and all(int(s.get('failures', 0)) == int(s.get('errors', 0)) == int(s.get('skipped', 0)) == 0 for s in suites)
    assert any(s.get('name', '').endswith('FleetPacketTest') and int(s.get('tests', 0)) >= 3 for s in suites)
    native = json.loads(native_report.read_text())
    assert native['server_restarts'] == 1 and len(native['results']) == 2
    assert all('FLEET_NATIVE_PASS' in line for line in native['results'])
    for key, path in [('plugin', REPO / 'server/plugins' / city.PLUGIN),
                      ('bridge', REPO / 'mods' / city.BRIDGE), ('sails', REPO / 'mods' / SAILS)]:
        assert native[key + '_sha256'] == sha(path.read_bytes()), 'Native test used a different ' + key
    native['results'] = [re.search(r'FLEET_NATIVE_PASS [^\x1b\r\n]*', line)[0].strip() for line in native['results']]
    write(REPORT, dict(status='passed', fleet_backend_cases=fleet_cases,
        backend_scenarios=len(re.findall(r'^✓ ', backend, re.M)),
        bridge_tests=sum(int(s.get('tests', 0)) for s in suites), native=native,
        graphical_client='not_run', production_world='not_run',
        artifacts={str(p.relative_to(REPO)): sha(p.read_bytes()) for p in artifacts()}))

def verify():
    city.verify(REPO / 'server/plugins' / city.PLUGIN, REPO / 'mods' / city.BRIDGE)
    for name in (SAILS, city.BRIDGE, PHYSICS):
        assert (REPO / 'mods' / name).read_bytes() == (REPO / 'server/mods' / name).read_bytes()
    for path in artifacts():
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None
            assert not any('SmokeMod' in name or 'FleetTests' in name for name in archive.namelist())
        assert path.with_suffix('.jar.sha256').read_text() == sha(path.read_bytes()) + '  ' + path.name + '\n'
    with zipfile.ZipFile(REPO / 'mods' / SAILS) as archive:
        assert 'com/quintonc/vs_sails/protection/FleetNative.class' in archive.namelist()
    with zipfile.ZipFile(REPO / 'mods' / city.BRIDGE) as archive:
        assert all('ua/empirecraft/townymap/' + name + '.class' in archive.namelist()
                   for name in ('FleetNetwork', 'client/FleetScreen', 'client/FleetMapPlugin'))
    report = json.loads(REPORT.read_text())
    assert report['status'] == 'passed'
    assert report['artifacts'] == {str(p.relative_to(REPO)): sha(p.read_bytes()) for p in artifacts()}
    run(REPO / 'validation/reconcile_modpack.py')
    run(REPO / 'validation/check_installed_releases.py')

def package(check):
    files = {'INSTALL-UA.md': (ROOT / 'README.md').read_bytes(),
             'ALLIED-COMMAND-DISCORD.md': (REPO / 'feudal/ALLIED-COMMAND-DISCORD.md').read_bytes(),
             'server/discord-bot.env.example': (REPO / 'server/discord-bot.env.example').read_bytes(),
             'validation.json': REPORT.read_bytes(),
             'modpack-lock.json': (REPO / 'modpack-lock.json').read_bytes(),
             'server/config/admanticheat/idfile/admanticheat_id_list.json':
                 (REPO / 'server/config/admanticheat/idfile/admanticheat_id_list.json').read_bytes()}
    for path in artifacts():
        relative = str(path.relative_to(REPO))
        if relative.startswith('mods/'):
            relative = 'client/' + relative
        files[relative] = path.read_bytes()
        files[relative + '.sha256'] = path.with_suffix('.jar.sha256').read_bytes()
    for side, relative in [('client', 'config/valkyrienskies/valkyrienskies-client.toml'),
                           ('client', 'defaultconfigs/valkyrienskies/vs-core-server.toml'),
                           ('server', 'defaultconfigs/valkyrienskies/vs-core-server.toml'),
                           ('server', 'config/vs_sails/config.cfg'),
                           ('server', 'config/vs_sails/ship-protection.json')]:
        files[side + '/' + relative] = (REPO / ('server' if side == 'server' else '') / relative).read_bytes()
    # The bundled ADM also declares the combined seasonal client mod.
    seasonal=list((REPO/'mods').glob('empirecraft-seasonal-leaves-*.jar'))
    if seasonal:
        assert len(seasonal)==1
        leaf=seasonal[0]
        for side in ['client','server']:
            files[side+'/mods/'+leaf.name]=leaf.read_bytes()
            files[side+'/mods/'+leaf.name+'.sha256']=leaf.with_suffix('.jar.sha256').read_bytes()
        files['REMOVE-OLD-SEASONAL-LEAVES.txt']=b'Remove previous empirecraft-seasonal-leaves JARs and checksums from both mods folders. Install the same included version on client and server; preserve worlds and configs.\n'
    files['SHA256SUMS'] = ''.join(sha(raw) + '  ' + name + '\n' for name, raw in sorted(files.items())).encode()
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name, raw in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 11, 5, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, raw)
    raw = buffer.getvalue()
    checksum = sha(raw) + '  ' + OUTPUT.name + '\n'
    if check:
        assert OUTPUT.read_bytes() == raw
        assert OUTPUT.with_suffix('.zip.sha256').read_text() == checksum
    else:
        OUTPUT.parent.mkdir(exist_ok=True)
        OUTPUT.write_bytes(raw)
        OUTPUT.with_suffix('.zip.sha256').write_text(checksum)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--install', action='store_true')
    modes.add_argument('--check', action='store_true')
    parser.add_argument('--plugin', type=Path, default=REPO / 'work/town-fleet/plugin' / city.PLUGIN)
    parser.add_argument('--bridge', type=Path, default=REPO / 'bridge/build/libs' / city.BRIDGE)
    parser.add_argument('--backend-log', type=Path)
    parser.add_argument('--bridge-reports', type=Path, default=REPO / 'bridge/build/test-results/test')
    parser.add_argument('--native-report', type=Path, default=ROOT / 'build/logs/result.json')
    args = parser.parse_args()
    if args.install:
        install(args.plugin, args.bridge)
    if args.backend_log:
        record(args.backend_log, args.bridge_reports, args.native_report)
    verify()
    package(args.check)
    print('CITY_FLEET_RELEASE_PASS', sha(OUTPUT.read_bytes()))

if __name__ == '__main__':
    main()
