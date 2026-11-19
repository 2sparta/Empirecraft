#!/usr/bin/env python3
"""Add TFC surface categories to the exact installed TRMT walking implementation."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
SOURCE_SHA = 'f93ce88322cfe4e1636d83e2a536ec9acd032f217b5ed9a3312acbe6ae804d5c'
VERSION = '0.5.1b-1.20.1-forge-empirecraft.1'
NAME = f'trmt-{VERSION}.jar'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', required=True, type=Path)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--fixture', action='store_true', help='build the isolated walking test mod')
    args = parser.parse_args()
    assert hashlib.sha256(args.base.read_bytes()).hexdigest() == SOURCE_SHA, 'Changed upstream TRMT JAR'
    build = ROOT / 'build'
    build.mkdir(exist_ok=True)
    library_root = REPO / 'server/libraries'
    runtime_patterns = ['server-1.20.1-20230612.114412-srg.jar', 'forge-1.20.1-47.4.23-server.jar',
                        'forge-1.20.1-47.4.23-universal.jar']
    runtime = [next(library_root.rglob(pattern)) for pattern in runtime_patterns]
    libs = [*runtime, *sorted(library_root.rglob('*.jar'))]
    java, javac = [args.java_home / 'bin' / name for name in ('java', 'javac')]

    def run(*command):
        subprocess.run(list(map(str, command)), check=True)

    sources = sorted((ROOT / 'src').rglob('*.java'))
    classes = build / 'classes'
    run(javac, '--release', '17', '-proc:none', '-cp', os.pathsep.join(map(str, libs)),
        '-d', classes, *sources)
    asm = sorted((REPO / 'server/libraries/org/ow2/asm').rglob('*.jar'))
    run(javac, '--release', '17', '-cp', os.pathsep.join(map(str, asm)), '-d', build / 'tools',
        ROOT / 'tools/Patch.java')
    with zipfile.ZipFile(args.base) as archive:
        files = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
    for name in ('ServerPlayerEntityMixin', 'MobEntityMixin'):
        path = f'milkucha/trmt/mixin/{name}.class'
        source, target = build / 'input.class', build / 'output.class'
        source.write_bytes(files[path])
        run(java, '-cp', os.pathsep.join(map(str, [build / 'tools', *asm])), 'Patch', source, target)
        files[path] = target.read_bytes()
    for file in classes.rglob('*.class'):
        files[file.relative_to(classes).as_posix()] = file.read_bytes()
    files['META-INF/mods.toml'] = files['META-INF/mods.toml'].replace(
        b'version="0.5.1b-1.20.1-forge"', f'version="{VERSION}"'.encode(), 1)
    output = build / NAME
    with zipfile.ZipFile(output, 'w') as archive:
        for name, raw in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 11, 19, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    output.with_suffix('.jar.sha256').write_text(
        hashlib.sha256(output.read_bytes()).hexdigest() + '  ' + output.name + '\n')
    if args.fixture:
        test_classes = build / 'test-classes'
        run(javac, '--release', '17', '-proc:none', '-cp', os.pathsep.join(map(str, [output, *libs])),
            '-d', test_classes, ROOT / 'tests/WalkingProbe.java')
        with zipfile.ZipFile(build / 'empirecraft-trmt-test.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(test_classes.rglob('*.class')):
                archive.writestr(file.relative_to(test_classes).as_posix(), file.read_bytes())
            archive.writestr('META-INF/mods.toml',
                'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\n'
                'modId="empirecraft_trmt_test"\nversion="1"\ndisplayName="TEST ONLY TRMT walking"\n')
    print(output)


if __name__ == '__main__':
    main()
