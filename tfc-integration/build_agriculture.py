#!/usr/bin/env python3
"""Build the agriculture overlay against the installed Mohist and mod APIs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

from build import REPO, ROOT, VERSION, compile, pack

BASE_SHA256 = '6d14fe9ba0232779e13878f91311a2e9239c93973b55c65c333c6d55492c851f'
SOURCES = {
    'Agriculture', 'Chernozem', 'CropAgronomy', 'CropProfiles', 'Integration', 'NaturalMaterials',
    'OneTimeTerrain', 'RegionalSoilPalette', 'RegionalSoils', 'RootFertilizer',
    'ChernozemFarmlandMixin', 'CropRandomTickMixin', 'RiceSoilMixin',
}


def read_jar(path, classes_only=False):
    with zipfile.ZipFile(path) as jar:
        return {name: jar.read(name) for name in jar.namelist()
                if name.endswith('.class') or (not classes_only and not name.endswith('/'))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', required=True, type=Path, help='Empirecraft TFC Integration 1.0.6')
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--fixture', action='store_true', help='also build the isolated runtime probe')
    args = parser.parse_args()
    base = args.base.resolve()
    if hashlib.sha256(base.read_bytes()).hexdigest() != BASE_SHA256:
        parser.error('--base must be the original Empirecraft TFC Integration 1.0.6 JAR')
    java = args.java_home / 'bin/java'
    javac = args.java_home / 'bin/javac'
    libs = REPO / 'server/libraries'
    mods = REPO / 'mods'

    def need(pattern, folder=libs):
        paths = list(folder.rglob(pattern))
        if len(paths) != 1:
            raise ValueError(f'Expected exactly one {pattern} in {folder}')
        return paths[0]

    def run(*command):
        subprocess.run(list(map(str, command)), check=True)

    build = ROOT / 'build'
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=build) as directory:
        work = Path(directory)
        srgutils = need('srgutils-0.5.4.jar')
        compile([REPO / 'sails/tools/OfflineMappings.java'], work / 'tools', [srgutils], javac)
        joined = work / 'joined.tsrg'
        with zipfile.ZipFile(need('mcp_config-1.20.1-20230612.114412.zip')) as archive:
            joined.write_bytes(archive.read('config/joined.tsrg'))
        mapping = work / 'named-srg.srg'
        run(java, '-cp', os.pathsep.join(map(str, [work / 'tools', srgutils])), 'OfflineMappings',
            need('server-1.20.1-20230612.114412-mappings.txt'), joined, mapping)

        libraries = sorted(libs.rglob('*.jar'))
        runtime = work / 'runtime-srg.jar'
        runtime_entries = {}
        for pattern in ('server-1.20.1-20230612.114412-srg.jar', 'forge-1.20.1-47.4.23-server.jar',
                        'forge-1.20.1-47.4.23-universal.jar'):
            runtime_entries.update(read_jar(need(pattern), classes_only=True))
        pack(runtime, runtime_entries)

        def remap(source, target, classpath, reverse=False):
            run(java, '-Xmx2G', '-cp', os.pathsep.join(map(str, [*classpath, *libraries])),
                'net.md_5.specialsource.SpecialSource', '-i', source, '-o', target, '-m', mapping,
                '-l', '--quiet', *(['-r'] if reverse else []))

        named = work / 'runtime-named.jar'
        remap(runtime, named, [runtime], reverse=True)
        asm = sorted((libs / 'org/ow2/asm').rglob('*.jar'))
        compile([ROOT / 'tools/CompileAccess.java'], work / 'tools', asm, javac)
        access = work / 'compile-access.jar'
        run(java, '-cp', os.pathsep.join(map(str, [work / 'tools', *asm])),
            'CompileAccess', named, access)
        dependencies = [need('TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.3.jar', mods),
                        need('legendarysurvivaloverhaul-*.jar', mods),
                        need('empirecraft-calendar-*.jar', mods),
                        need('empirecraft-earth-*.jar', mods)]
        named_dependencies = []
        for dependency in dependencies:
            source = work / (dependency.stem + '-classes.jar')
            pack(source, read_jar(dependency, classes_only=True))
            target = work / dependency.name
            remap(source, target, [source, runtime, *dependencies], reverse=True)
            named_dependencies.append(target)

        nested = work / 'nested'
        nested.mkdir()
        for dependency in sorted(mods.glob('*.jar')):
            with zipfile.ZipFile(dependency) as archive:
                for name in archive.namelist():
                    if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                        target = nested / Path(name).name
                        if not target.exists():
                            target.write_bytes(archive.read(name))

        sources = sorted(file for file in (ROOT / 'src/main/java').rglob('*.java') if file.stem in SOURCES)
        assert len(sources) == len(SOURCES)
        classpath = [access, named, *named_dependencies, base, *libraries, *sorted(nested.glob('*.jar')),
                     *sorted(mods.glob('*.jar'))]

        def compile_overlay(name, sources, api):
            classes = work / (name + '-classes')
            compile(sources, classes, api, javac)
            overlay = work / (name + '-named.jar')
            pack(overlay, {file.relative_to(classes).as_posix(): file.read_bytes()
                           for file in classes.rglob('*.class')})
            patched = work / (name + '-srg.jar')
            remap(overlay, patched, [overlay, *api])
            return overlay, read_jar(patched, classes_only=True)

        overlay, patched = compile_overlay('agriculture', sources, classpath)
        if args.fixture:
            _, assembly = compile_overlay('agriculture-test', [ROOT / 'tests/AgricultureChecks.java'],
                                          [overlay, *classpath])
            assembly['META-INF/mods.toml'] = b"""modLoader="javafml"
loaderVersion="[47,)"
license="EUPL-1.2"
[[mods]]
modId="empirecraft_agriculture_test"
version="1"
displayName="TEST ONLY agriculture"
"""
            pack(build / 'libs/empirecraft-agriculture-test.jar', assembly)

        files = read_jar(base)
        files.update(patched)
        resources = ROOT / 'src/main/resources'
        for file in resources.rglob('*'):
            if not file.is_file():
                continue
            path = file.relative_to(resources).as_posix()
            if path.startswith('assets/empirecraft_tfc/lang/') and path in files:
                entries = json.loads(files[path])
                entries.update(json.loads(file.read_bytes()))
                files[path] = (json.dumps(entries, ensure_ascii=False, indent=2) + '\n').encode()
            else:
                files[path] = file.read_bytes()
        manifest = 'META-INF/MANIFEST.MF'
        files[manifest] = files[manifest].replace(b'Implementation-Version: 1.0.6',
                                                 f'Implementation-Version: {VERSION}'.encode())
        output = build / 'libs' / f'empirecraft-tfc-integration-{VERSION}.jar'
        pack(output, files)
        print('Staged:', output)


if __name__ == '__main__':
    main()
