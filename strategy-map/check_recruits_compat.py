#!/usr/bin/env python3
"""Check the shipped Recruits JARs and run Forge's dependency sorter on each side."""

import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
from zipfile import ZipFile

from build_recruits_compat import BASE_JAR, DESCRIPTOR, HERE, RELEASE_NAME, build


ROOT = HERE.parent
LIBRARIES = Path(os.environ.get('EMPIRECRAFT_SERVER_LIBRARIES', ROOT / 'server/libraries'))
FORGE_VERSION = '1.20.1-47.4.23'


def main():
    client = ROOT / 'mods' / RELEASE_NAME
    server = ROOT / 'server/mods' / RELEASE_NAME
    bridges = sorted((ROOT / 'mods').glob('empirecraft-towny-journeymap-*.jar'))
    assert len(bridges) == 1, 'Expected one installed bridge'
    bridge = bridges[0]
    journeymap = ROOT / 'mods/journeymap-forge-1.20.1-6.0.7.jar'
    forge = LIBRARIES / f'net/minecraftforge/forge/{FORGE_VERSION}/forge-{FORGE_VERSION}-universal.jar'
    assert client.read_bytes() == server.read_bytes()
    with ZipFile(BASE_JAR) as original, ZipFile(client) as updated:
        assert original.namelist() == updated.namelist()
        assert [name for name in original.namelist() if original.read(name) != updated.read(name)] == [DESCRIPTOR]

    groups = (
        'com/electronwill/night-config', 'com/google/code/gson', 'com/google/guava', 'com/mojang/logging',
        'cpw/mods/modlauncher', 'cpw/mods/securejarhandler', 'net/minecraftforge/accesstransformers',
        'net/minecraftforge/fmlloader', 'net/minecraftforge/forgespi', 'net/minecraftforge/mergetool',
        'net/minecraftforge/unsafe', 'org/apache/commons/commons-lang3', 'org/apache/logging/log4j/*/2.20.0',
        'org/apache/maven/maven-artifact', 'org/ow2/asm/*/9.7', 'org/slf4j/slf4j-api/2.0.6', 'org/spongepowered/mixin',
    )
    libraries = sorted(jar for group in groups for directory in LIBRARIES.glob(group) for jar in directory.rglob('*.jar'))
    classpath = os.pathsep.join(map(str, libraries))
    java = Path(os.environ['JAVA_HOME']) / 'bin/java' if 'JAVA_HOME' in os.environ else 'java'
    with TemporaryDirectory(prefix='recruits-compat-') as temp:
        work = Path(temp)
        rebuilt = work / RELEASE_NAME
        build(rebuilt)
        assert rebuilt.read_bytes() == client.read_bytes()
        # Only the Minecraft identity is needed for dependency sorting. All mod descriptors are shipped JARs.
        minecraft = work / 'minecraft-1.20.1.jar'
        with ZipFile(minecraft, 'w') as fixture:
            fixture.writestr(DESCRIPTOR, 'modLoader="minecraft"\nloaderVersion="[1,)"\nlicense="All Rights Reserved"\n'
                             '[[mods]]\nmodId="minecraft"\nversion="1.20.1"\n')
        with ZipFile(journeymap) as archive:
            common_networking = work / 'common-networking-forge-1.0.6-1.20.1.jar'
            common_networking.write_bytes(archive.read(f'META-INF/jarjar/{common_networking.name}'))

        source = HERE / 'tools/ForgeRecruitsDependencyProbe.java'
        subprocess.run([java, 'com.sun.tools.javac.Main', '-source', '17', '-target', '17', '-proc:none', '-cp', classpath,
                        '-d', work, source], check=True)
        command = [java, '--add-opens', 'java.base/java.lang.invoke=ALL-UNNAMED',
                   '--add-opens', 'java.base/java.util.jar=ALL-UNNAMED', '-cp', f'{work}{os.pathsep}{classpath}',
                   'ForgeRecruitsDependencyProbe']
        for side, expected, recruits in (
            ('CLIENT', 'reject', BASE_JAR),
            ('DEDICATED_SERVER', 'accept', BASE_JAR),
            ('CLIENT', 'accept', client),
            ('DEDICATED_SERVER', 'accept', server),
        ):
            subprocess.run([*command, side, expected, minecraft, forge, journeymap, common_networking, bridge, recruits],
                           check=True)


if __name__ == '__main__':
    main()
