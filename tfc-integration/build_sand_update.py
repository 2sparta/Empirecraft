#!/usr/bin/env python3
"""Stage the sand update on the exact installed integration without rebuilding unrelated classes."""
import argparse
import hashlib
import json
import os
import subprocess
import tempfile
import zipfile
from pathlib import Path

from build import REPO, ROOT, VERSION, pack
from natural_materials import RETIRED_SAND_RECIPES, SAND_ALIASES, sand_resources
from resources import raw

BASE_SHA256 = '61f31aec3c7e3d64560fa6d2f0d3952bebe325ebe0e1e205f1e0be962f2962d0'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    args = parser.parse_args()
    if hashlib.sha256(args.base.read_bytes()).hexdigest() != BASE_SHA256:
        parser.error('--base must be the original Empirecraft TFC Integration 1.0.5 JAR')
    sdk = args.sdk.resolve()
    libs = REPO / 'server/libraries'
    earth, = (REPO / 'mods').glob('empirecraft-earth-*.jar')
    cp = [sdk / 'runtime-named.jar', args.base, earth, *sorted(libs.rglob('*.jar'))]
    tool = [sdk / 'tiny-remapper.jar', sdk / 'mapping-io.jar', *sorted((libs / 'org/ow2/asm').rglob('*.jar'))]
    source = ROOT / 'src/main/java/ua/empirecraft/tfc'
    with zipfile.ZipFile(args.base) as archive:
        files = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
    build = ROOT / 'build'
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=build) as scratch:
        work = Path(scratch)
        classes = work / 'classes'
        subprocess.run(['java', 'com.sun.tools.javac.Main', '-source', '17', '-target', '17', '-proc:none',
                        '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, cp)), '-d', str(classes),
                        *[str(source / (name + '.java')) for name in ['NaturalMaterials', 'MaterialPalette', 'SandPalette']]],
                       check=True)
        named, srg = work / 'sand-named.jar', work / 'sand-srg.jar'
        pack(named, {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')})
        subprocess.run(['java', '-cp', os.pathsep.join(map(str, tool)), 'dev.architectury.tinyremapper.Main',
                        str(named), str(srg), str(sdk / 'named-srg.tiny'), 'named', 'srg', *map(str, cp),
                        '--ignoreConflicts'], check=True)
        with zipfile.ZipFile(srg) as archive:
            files.update({name: archive.read(name) for name in archive.namelist() if name.endswith('.class')})

    policy_name = 'empirecraft-tfc-policy.json'
    policy = json.loads(files[policy_name])
    policy['aliases'].pop('tfc:sand/white', None)
    policy['aliases'].update(SAND_ALIASES)
    files.pop('data/tfc/loot_tables/blocks/sand/white.json', None)
    for recipe in RETIRED_SAND_RECIPES:
        namespace, path = recipe.split(':', 1)
        policy['overrides'].pop(recipe, None)
        files.pop(f'data/{namespace}/recipes/{path}.json', None)
    files[policy_name] = raw(policy)

    def add(name, value):
        files[name] = raw(value)

    def tag(namespace, path, items):
        add(f'data/{namespace}/tags/items/{path}.json', {'replace': False, 'values': items})

    sand_resources(add, tag)
    files['META-INF/mods.toml'] = (ROOT / 'src/main/resources/META-INF/mods.toml').read_bytes()
    manifest = 'META-INF/MANIFEST.MF'
    files[manifest] = files[manifest].replace(b'Implementation-Version: 1.0.5', f'Implementation-Version: {VERSION}'.encode())
    output = build / 'libs' / f'empirecraft-tfc-integration-{VERSION}.jar'
    pack(output, files)
    print('Staged:', output)


if __name__ == '__main__':
    main()
