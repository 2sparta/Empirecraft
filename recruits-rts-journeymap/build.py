#!/usr/bin/env python3
"""Build the Empirecraft JourneyMap edition from the published RTS Command JAR."""

import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
UPSTREAM_SHA256 = '6cfc2f006b3aab664c4454662cdea158ce81796f6cb7241a4e58623926fbfab4'
VERSION = '0.1.0-beta.3-empirecraft.1'
JAR_NAME = f'recruitsrtscommand-{VERSION}.jar'
PACKAGE = 'me/mss1r/recruitsrtscommand'
OVERLAY = f'{PACKAGE}/client/gui/worldmap/command/WorldMapCommandOverlay.class'
LIBRARIES = REPO / 'server/libraries'
FORGE = LIBRARIES / 'net/minecraftforge/forge/1.20.1-47.4.23'
JOURNEYMAP = REPO / 'mods/journeymap-forge-1.20.1-6.0.7.jar'
BRIDGE = REPO / 'mods/empirecraft-towny-journeymap-1.14.0.jar'
DEPENDENCY = '''
[[dependencies.recruitsrtscommand]]
modId = "journeymap"
mandatory = true
versionRange = "[1.20.1-6.0.7]"
ordering = "AFTER"
side = "CLIENT"

[[dependencies.recruitsrtscommand]]
modId = "empirecraft_townymap"
mandatory = true
versionRange = "[1.14.0,1.15)"
ordering = "AFTER"
side = "CLIENT"
'''


def build(upstream, output, java):
    if hashlib.sha256(upstream.read_bytes()).hexdigest() != UPSTREAM_SHA256:
        raise ValueError('Expected the original Recruits RTS Command 0.1.0-beta.3 JAR')
    with ZipFile(upstream) as jar:
        entries = {name: jar.read(name) for name in jar.namelist() if not name.endswith('/')}

    with tempfile.TemporaryDirectory(prefix='empirecraft-rts-') as work:
        work = Path(work)
        api = work / 'journeymap-api.jar'
        with ZipFile(JOURNEYMAP) as jar:
            api.write_bytes(jar.read('META-INF/jarjar/journeymap-api-forge-1.20.1-2.0.0.jar'))
        libraries = sorted(LIBRARIES.rglob('*.jar'))
        compile_api = [upstream, BRIDGE, api, FORGE / 'forge-1.20.1-47.4.23-server.jar',
                       FORGE / 'forge-1.20.1-47.4.23-universal.jar', *libraries]
        classes = work / 'classes'
        javac = [java, 'com.sun.tools.javac.Main', '--release', '17', '-proc:none', '-encoding', 'UTF-8']
        subprocess.run([*javac, '-cp', os.pathsep.join(map(str, compile_api)), '-d', classes,
                        *sorted((ROOT / 'src').rglob('*.java'))], check=True)
        entries.update({path.relative_to(classes).as_posix(): path.read_bytes() for path in classes.rglob('*.class')})

        asm = sorted((LIBRARIES / 'org/ow2/asm').glob('*/9.7/*.jar'))
        tools = work / 'tools'
        subprocess.run([*javac, '-cp', os.pathsep.join(map(str, asm)), '-d', tools,
                        ROOT / 'tools/PlaceToggle.java'], check=True)
        original = work / 'overlay-original.class'
        patched = work / 'overlay-patched.class'
        original.write_bytes(entries[OVERLAY])
        subprocess.run([java, '-cp', os.pathsep.join(map(str, [tools, *asm])),
                        'PlaceToggle', original, patched], check=True)
        entries[OVERLAY] = patched.read_bytes()

    # Empirecraft has already removed the class targeted by both upstream mixins.
    entries = {name: value for name, value in entries.items() if not name.startswith(f'{PACKAGE}/mixin/')}
    entries.pop('recruitsrtscommand.mixins.json')
    entries.pop('recruitsrtscommand.refmap.json')
    descriptor = entries['META-INF/mods.toml'].decode()
    descriptor = descriptor.split('[[mixins]]', 1)[0].replace('version = "0.1.0-beta.3"', f'version = "{VERSION}"')
    descriptor = descriptor.replace('the Villager Recruits tactical map', 'Empirecraft JourneyMap')
    entries['META-INF/mods.toml'] = (descriptor + DEPENDENCY).encode()
    manifest = entries['META-INF/MANIFEST.MF'].decode()
    manifest = manifest.replace('MixinConfigs: recruitsrtscommand.mixins.json\r\n', '')
    entries['META-INF/MANIFEST.MF'] = manifest.replace('Implementation-Version: 0.1.0-beta.3',
                                                        f'Implementation-Version: {VERSION}').encode()

    output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(output, 'w') as jar:
        for name, data in sorted(entries.items()):
            info = ZipInfo(name, (2026, 11, 21, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            jar.writestr(info, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('upstream', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--java', default=os.environ.get('JAVA_HOME', '') + '/bin/java' if 'JAVA_HOME' in os.environ else 'java')
    args = parser.parse_args()
    build(args.upstream, args.output, args.java)
    print(args.output)


if __name__ == '__main__':
    main()
