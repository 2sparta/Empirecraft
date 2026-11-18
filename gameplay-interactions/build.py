#!/usr/bin/env python3
"""Stage gameplay interactions and tightly scoped Alex/Armory updates against the pack runtime."""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
BUILD = ROOT / 'build'
BASES = {
    'empirecraft-armory-1.1.0.jar': '79bdd335fcd4a15a568e9415df03f981660c7ce643a8aec396a70189050ee2af',
    'alexsmobs-1.22.9-empirecraft.1.jar': '0e6a93372c53db215f5c5a9e7b2233699021c0665733fa0ae34f490cd54e21d8',
    'empirecraft-wildlife-1.0.0.jar': '493175ced73e1034201e729582be8705476dfb9aacd3cbaf9377f29a64a94faa',
}
OUTPUTS = {
    'interactions': 'empirecraft-interactions-1.0.0.jar',
    'armory': 'empirecraft-armory-1.1.1.jar',
    'alex': 'alexsmobs-1.22.9-empirecraft.2.jar',
    'wildlife': 'empirecraft-wildlife-1.0.1.jar',
}


def entries(path):
    with zipfile.ZipFile(path) as archive:
        return {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}


def pack(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as archive:
        for name, contents in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 18, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, contents)
    checksum = hashlib.sha256(path.read_bytes()).hexdigest()
    path.with_suffix('.jar.sha256').write_text(f'{checksum}  {path.name}\n')


def compile_classes(work, sdk, libs, classpath, sources):
    classes = work / 'classes'
    subprocess.run(['java', 'com.sun.tools.javac.Main', '-source', '17', '-target', '17', '-proc:none',
                    '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, classpath)), '-d', str(classes),
                    *map(str, sources)], check=True)
    main, patched = work / 'named.jar', work / 'srg.jar'
    pack(main, {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')})
    tooling = [sdk / 'tiny-remapper.jar', sdk / 'mapping-io.jar', *sorted((libs / 'org/ow2/asm').rglob('*.jar'))]
    subprocess.run(['java', '-cp', os.pathsep.join(map(str, tooling)), 'dev.architectury.tinyremapper.Main',
                    str(main), str(patched), str(sdk / 'named-srg.tiny'), 'named', 'srg',
                    *map(str, classpath), '--ignoreConflicts'], check=True)
    return {name: raw for name, raw in entries(patched).items() if name.endswith('.class')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', required=True, type=Path)
    parser.add_argument('--bases', type=Path, default=REPO / 'disabled-mods/replaced-2026-11-01/mods')
    args = parser.parse_args()
    for name, digest in BASES.items():
        if hashlib.sha256((args.bases / name).read_bytes()).hexdigest() != digest:
            parser.error(f'Expected pinned input {name}')

    libs = REPO / 'server/libraries'
    client = sorted((REPO / 'mods').glob('*.jar'))
    server = sorted((REPO / 'server/mods').glob('empirecraft-*.jar'))
    cp = [args.sdk / 'runtime-named.jar', *sorted(libs.rglob('*.jar')), *client, *server]
    classes = ROOT / 'src/main/java'
    resources = ROOT / 'src/main/resources'
    BUILD.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=BUILD) as scratch:
        work = Path(scratch)
        for folder in ['game', 'armory']:
            (work / folder).mkdir()
        game = compile_classes(work / 'game', args.sdk, libs, cp, sorted(classes.rglob('*.java')))
        source = REPO / 'armory/src/main/java/ua/empirecraft/armory'
        armory_classes = compile_classes(work / 'armory', args.sdk, libs, cp,
                                         [source / 'AssemblyRecipe.java', source / 'Profile.java'])
    game.update({p.relative_to(resources).as_posix(): p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    material_names = set()
    for row in json.loads(game['material-balance.json']):
        for block in row['blocks']:
            assert block not in material_names
            material_names.add(block)
            namespace, path = block.split(':', 1)
            game[f'data/{namespace}/block_armor/{path}.json'] = json.dumps(
                {'hardness': row['cannon_hardness'], 'toughness': row['cannon_toughness']}, indent=2).encode()
    game['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_interactions.mixins.json\r\n\r\n'
    game['pack.mcmeta'] = b'{"pack":{"pack_format":15,"description":"Empirecraft gameplay interactions"}}'

    armory = entries(args.bases / 'empirecraft-armory-1.1.0.jar')
    armory.update(armory_classes)
    armory['META-INF/mods.toml'] = (REPO / 'armory/src/main/resources/META-INF/mods.toml').read_bytes()
    armory['META-INF/MANIFEST.MF'] = armory['META-INF/MANIFEST.MF'].replace(b'Implementation-Version: 1.1.0', b'Implementation-Version: 1.1.1')
    wildlife = entries(args.bases / 'empirecraft-wildlife-1.0.0.jar')
    policy = (REPO / 'alexs-wildlife/src/main/resources/wildlife-policy.json').read_bytes()
    wildlife['wildlife-policy.json'] = policy
    wildlife['META-INF/mods.toml'] = (REPO / 'alexs-wildlife/src/main/resources/META-INF/mods.toml').read_bytes()
    wildlife['META-INF/MANIFEST.MF'] = wildlife['META-INF/MANIFEST.MF'].replace(b'Implementation-Version: 1.0.0', b'Implementation-Version: 1.0.1')
    sys.path.insert(0, str(REPO / 'alexs-wildlife'))
    from content import trim
    alex = trim(entries(args.bases / 'alexsmobs-1.22.9-empirecraft.1.jar'), json.loads(policy))
    alex['META-INF/mods.toml'] = alex['META-INF/mods.toml'].replace(b'version="1.22.9-empirecraft.1"', b'version="1.22.9-empirecraft.2"')

    target = BUILD / 'libs'
    for key, contents in [('interactions', game), ('armory', armory), ('alex', alex), ('wildlife', wildlife)]:
        pack(target / OUTPUTS[key], contents)
    print('Staged four paired JARs in', target)


if __name__ == '__main__':
    main()
