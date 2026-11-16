#!/usr/bin/env python3
"""Build the 1.12.0 server overlay against installed Forge SRG APIs, preserving every client class.

This offline alternative to ForgeGradle uses the exact 1.11.2 release and genuine installed
Minecraft, Forge and JourneyMap API classes. Only generated build sources use SRG names.
The checked-in sources remain in Mojang names for the normal Gradle build.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
VERSION = '1.12.0'
BASE_SHA = 'c40e8ff6b6acb7124aaad73ee5f612ca3ee0e039e9fff1de826104dbccec3bb0'
CHANGED = ['Claims', 'BanditNames', 'BanditTerritories', 'ClaimIndex', 'ClaimKnowledge',
           'OverlaySync', 'TownyAccess', 'BridgeService']
# Exact Forge 1.20.1 names. The compiler resolves each reference against genuine runtime classes.
METHODS = {'getUUID': 'm_20148_', 'getWorldPath': 'm_129843_', 'getPlayerList': 'm_6846_',
           'getPlayers': 'm_11314_', 'isRunning': 'm_130010_', 'serverLevel': 'm_284548_',
           'getBlockX': 'm_146903_', 'getBlockZ': 'm_146907_', 'getX': 'm_20185_',
           'getZ': 'm_20189_', 'location': 'm_135782_', 'tryParse': 'm_135820_', 'asLong': 'm_121882_'}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def readjar(path):
    with zipfile.ZipFile(path) as jar:
        return {n: jar.read(n) for n in jar.namelist() if not n.endswith('/')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--reference-repo', type=Path, default=REPO)
    parser.add_argument('--junit', type=Path, required=True, help='JUnit Platform standalone 1.10.3')
    args = parser.parse_args()
    ref = args.reference_repo.resolve()
    build = ROOT / 'build/bandits'
    build.mkdir(parents=True, exist_ok=True)
    logs = build / 'logs'
    logs.mkdir(exist_ok=True)

    def run(command, name):
        result = subprocess.run(list(map(str, command)), capture_output=True, text=True)
        (logs / (name + '.log')).write_text(result.stdout + result.stderr)
        if result.returncode:
            raise RuntimeError(name + '\n' + result.stdout[-4000:] + result.stderr[-6000:])
        print(name + '_PASS', flush=True)
        return result.stdout

    base = ROOT / 'native-inputs/empirecraft-towny-journeymap-1.11.2.jar'
    assert sha(base) == BASE_SHA, 'Unexpected bridge baseline'
    assert sha(args.junit) == '1455afad75d4eb4f44b493febc4a1bc32bc5f02eb25679361abd55e0f6421050', 'Unexpected JUnit distribution'
    runtime = ref / 'server/libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-server.jar'
    srg = ref / 'server/libraries/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar'
    libs = sorted((ref / 'server/libraries').rglob('*.jar'))
    journey = next((ref / 'mods').glob('journeymap-forge-1.20.1-6.0.7.jar'))
    api = build / 'journeymap-api.jar'
    api.write_bytes(readjar(journey)['META-INF/jarjar/journeymap-api-forge-1.20.1-2.0.0.jar'])
    cp = os.pathsep.join(map(str, [base, runtime, srg, api, *libs]))
    source = ROOT / 'src/main/java/ua/empirecraft/townymap'
    mapped = build / 'mapped'
    mapped.mkdir(exist_ok=True)
    fingerprint = {str((source / (n + '.java')).relative_to(REPO)): sha(source / (n + '.java')) for n in CHANGED}
    paths = []
    for name in CHANGED:
        text = (source / (name + '.java')).read_text()
        if name in ['TownyAccess', 'BridgeService']:
            for old, new in METHODS.items():
                text = re.sub(r'\.' + old + r'\(', '.' + new + '(', text)
            text = text.replace('.dimension().m_135782_()', '.m_46472_().m_135782_()')
            text = text.replace('LevelResource.ROOT', 'LevelResource.f_78182_')
            text = text.replace('Registries.DIMENSION', 'Registries.f_256858_')
            text = text.replace('ResourceKey.create(', 'ResourceKey.m_135785_(')
        path = mapped / (name + '.java')
        path.write_text(text)
        paths.append(path)
    classes = build / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir()
    compiler = [args.jdk / 'bin/javac', '--release', '17', '-proc:none', '-encoding', 'UTF-8']
    run([*compiler, '-cp', cp, '-d', classes, *paths], 'compile-runtime')
    baseline = readjar(base)
    prefixes = ['ua/empirecraft/townymap/' + n for n in CHANGED]
    replaced = lambda n: any(n == p + '.class' or n.startswith(p + '$') for p in prefixes)
    files = {n: raw for n, raw in baseline.items() if not replaced(n)}
    files.update({p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')})
    files['META-INF/mods.toml'] = (ROOT / 'src/main/resources/META-INF/mods.toml').read_bytes()
    output = ROOT / 'dist' / ('empirecraft-towny-journeymap-' + VERSION + '.jar')
    with zipfile.ZipFile(output, 'w') as jar:
        for name, raw in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 11, 16, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            jar.writestr(entry, raw)
    output.with_suffix('.jar.sha256').write_text(sha(output) + '  ' + output.name + '\n')
    preserved = [n for n in baseline if n.endswith('.class') and not replaced(n)]
    assert all(files[n] == baseline[n] for n in preserved)
    assert all(sha(REPO / name) == digest for name, digest in fingerprint.items())

    # Run tests against the assembled release, so no locally compiled production class can shadow it.
    tests = build / 'tests'
    shutil.rmtree(tests, ignore_errors=True)
    tests.mkdir()
    suites = ['BanditTerritoriesTest', 'ClaimKnowledgeTest', 'ClaimGeometryTest',
              'OverlaySyncTest', 'KnowledgeStoreTest', 'TownyAccessTest']
    testsrc = ROOT / 'src/test/java'
    fixtures = [*sorted((testsrc / 'org').rglob('*.java')), *sorted((testsrc / 'com').rglob('*.java'))]
    testcp = os.pathsep.join(map(str, [output, args.junit])) + os.pathsep + cp
    run([*compiler, '-cp', testcp, '-d', tests, *fixtures,
         *[testsrc / 'ua/empirecraft/townymap' / (n + '.java') for n in suites]], 'compile-tests')
    reportdir = build / 'junit'
    testout = run([args.jdk / 'bin/java', '-jar', args.junit, 'execute', '--class-path',
                  str(tests) + os.pathsep + testcp, '--scan-class-path', '--include-package',
                  'ua.empirecraft.townymap', '--details', 'summary', '--reports-dir', reportdir], 'packaged-tests')
    print(testout)
    tests_run = sum(int(ET.parse(p).getroot().get('tests', '0')) for p in reportdir.glob('TEST-*.xml'))
    report = dict(status='passed', version=VERSION, sha256=sha(output), base_sha256=BASE_SHA,
                  source_sha256=fingerprint, preserved_classes=len(preserved),
                  test_suites=suites, tests_passed=tests_run, graphical_client_tested=False, production_world_opened=False)
    (ROOT / 'dist/bandits-validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print('BANDIT_MAP_BUILD_PASS', sha(output))


if __name__ == '__main__':
    main()
