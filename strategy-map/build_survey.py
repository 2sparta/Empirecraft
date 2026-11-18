#!/usr/bin/env python3
"""Build the survey and town-profile update on the verified installed 1.13.0/1.20.0 JARs."""
import argparse
import os
import subprocess
import tempfile
from pathlib import Path

from build import BUILD, REPO, VERSION, FEUDAL_VERSION, archive, classes, readjar, runtime_source, sha

BRIDGE = f'empirecraft-towny-journeymap-{VERSION}.jar'
FEUDAL = f'empirecraft-feudal-{FEUDAL_VERSION}.jar'
BASE_BRIDGE_SHA = 'a1b3b19941cca16071c8c43ac085a9ef9a21443c7365b91a27fe2992f9f75d0a'
BASE_FEUDAL_SHA = '987155e39822c80bd7fed244cb849032795896438e4c5ee135a8b7a2e217f161'
SOURCES = ['BridgeService', 'ClaimKnowledge', 'ClimateObservations', 'EarthMapClimate', 'LocalSurvey', 'MapMode',
           'client/MapCardSelection', 'client/MapClickPlugin', 'client/StrategyLayout', 'client/StrategyMapUi']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bridge', required=True, type=Path)
    parser.add_argument('--feudal', required=True, type=Path)
    args = parser.parse_args()
    if sha(args.bridge) != BASE_BRIDGE_SHA or sha(args.feudal) != BASE_FEUDAL_SHA:
        parser.error('Expected the locked 1.13.0 bridge and Feudal 1.20.0 JARs.')

    libs = REPO / 'server/libraries'
    forge = libs / 'net/minecraftforge/forge/1.20.1-47.4.23'
    runtime = forge / 'forge-1.20.1-47.4.23-server.jar'
    srg = libs / 'net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar'
    plugins = REPO / 'server/plugins'
    mods = REPO / 'mods'
    mods_sources = REPO / 'bridge/src/main'
    feudal_sources = REPO / 'feudal/src/main'
    libs_cp = sorted(libs.rglob('*.jar'))
    BUILD.mkdir(exist_ok=True)
    out = BUILD / 'survey'
    with tempfile.TemporaryDirectory(dir=BUILD) as scratch:
        work = Path(scratch)
        deps = out / 'deps'
        deps.mkdir(parents=True, exist_ok=True)
        for path, raw in readjar(mods / 'journeymap-forge-1.20.1-6.0.7.jar').items():
            if path.startswith('META-INF/jarjar/') and path.endswith('.jar'):
                (deps / Path(path).name).write_bytes(raw)
        cp = [args.bridge, runtime, srg, *sorted(deps.glob('*.jar')), *libs_cp, *sorted(mods.glob('*.jar'))]
        source = mods_sources / 'java/ua/empirecraft/townymap'
        mapped = []
        for name in SOURCES:
            path = source / (name + '.java')
            target = work / path.name
            target.write_text(runtime_source(path))
            mapped.append(target)
        bridge = compile_overlay(args.bridge, cp, work / 'bridge', mapped, 'ua/empirecraft/townymap/', SOURCES)
        bridge['META-INF/mods.toml'] = (mods_sources / 'resources/META-INF/mods.toml').read_bytes()
        archive(out / BRIDGE, bridge)
        (out / 'classpath.txt').write_text(os.pathsep.join(map(str, cp[1:])))

        bukkit = work / 'bukkit-api.jar'
        archive(bukkit, {name: raw for name, raw in readjar(forge / 'forge-1.20.1-47.4.23-universal.jar').items()
                         if name.startswith('org/bukkit/') and not name.startswith('org/bukkit/craftbukkit/') and name.endswith('.class')})
        cp = [bukkit, args.feudal, *sorted(plugins.glob('*.jar')), *libs_cp]
        source = feudal_sources / 'java/ua/empirecraft/feudal/MapProfiles.java'
        feudal = compile_overlay(args.feudal, cp, work / 'feudal', [source], 'ua/empirecraft/feudal/', ['MapProfiles'])
        feudal['plugin.yml'] = (feudal_sources / 'resources/plugin.yml').read_bytes()
        archive(out / FEUDAL, feudal)
    print('Staged both map update JARs in', out)


def compile_overlay(base, cp, output, source, prefix, targets):
    subprocess.run(['java', 'com.sun.tools.javac.Main', '-source', '17', '-target', '17',
                    '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, cp)),
                    '-d', str(output), *map(str, source)], check=True)
    prefixes = {prefix + name for name in targets}
    files = {name: raw for name, raw in readjar(base).items()
             if not name.endswith('.class') or name.removesuffix('.class').split('$', 1)[0] not in prefixes}
    files.update(classes(output))
    return files


if __name__ == '__main__':
    main()
