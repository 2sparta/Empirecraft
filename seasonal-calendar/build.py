#!/usr/bin/env python3
"""Reproducible Java 17 build against the installed Forge/Mohist runtime."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
VERSION = '1.2.2'
NAME = 'empirecraft-calendar-' + VERSION + '.jar'


def classpath(roots):
    import json
    installed = {row['name'] for row in json.loads((ROOT/'installed-mods.json').read_text())}
    jars = {f.resolve() for root in roots for f in root.rglob('*.jar')
            if not f.name.startswith('empirecraft-calendar-')
            and (f.parent.resolve() != (REPO/'mods').resolve() or f.name in installed)}
    def priority(path):
        if path.name == 'forge-1.20.1-47.4.23-server.jar': return 0
        # The repository also retains older, unused ASM releases. Match the active Mohist runtime.
        if path.name in {'asm-9.7.jar', 'asm-tree-9.7.jar', 'asm-commons-9.7.jar',
                         'asm-analysis-9.7.jar', 'asm-util-9.7.jar', 'mixin-0.8.5.jar'}: return 1
        return 2
    return sorted(jars, key=lambda p: (priority(p), str(p)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-dir', type=Path, action='append')
    parser.add_argument('--staged', action='store_true', help='Build without changing installed mods')
    args = parser.parse_args()
    jars = classpath(args.classpath_dir or [REPO / 'server/libraries', REPO / 'server/mods', REPO / 'mods'])
    for name in ['forge-1.20.1-47.4.23-server.jar', 'server-1.20.1-20230612.114412-srg.jar', 'SereneSeasons-forge-1.20.1-9.1.0.3.jar']:
        assert any(p.name == name for p in jars), 'Missing dependency: ' + name
    classes = ROOT / 'build/classes'
    if classes.exists(): shutil.rmtree(classes)
    classes.mkdir(parents=True)
    java_home = os.environ.get('JAVA_HOME')
    compiler = [str(Path(java_home)/'bin/javac')] if java_home else ([shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main'])
    flags = ['--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, jars)), '-d', str(classes), *map(str, sorted((ROOT/'src/main/java').rglob('*.java')))]
    argfile = ROOT / 'build/javac.args'
    argfile.write_text('\n'.join('"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"' for s in flags) + '\n')
    subprocess.run([*compiler, '@' + str(argfile)], check=True)
    files = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    resources = ROOT/'src/main/resources'
    files.update({p.relative_to(resources).as_posix(): p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    files['META-INF/MANIFEST.MF'] = ('Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_calendar.mixins.json\r\nImplementation-Version: ' + VERSION + '\r\n\r\n').encode()
    files['META-INF/LICENSE.txt'] = (ROOT/'LICENSE').read_bytes()
    target = ROOT/'build/libs'/NAME if args.staged else REPO/'mods'/NAME
    target.parent.mkdir(parents=True, exist_ok=True)
    if not args.staged:
        for folder in (REPO/'mods', REPO/'server/mods'):
            for previous in folder.glob('empirecraft-calendar-*.jar*'):
                if previous.name not in (NAME, NAME+'.sha256'): previous.unlink()
    with zipfile.ZipFile(target, 'w') as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 24, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    from native_overlay import apply
    target.write_bytes(apply(ROOT, target.read_bytes(), NAME))
    outputs = [target]
    if not args.staged:
        server = REPO/'server/mods'/NAME
        server.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(target, server); outputs.append(server)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    for output in outputs: output.with_suffix('.jar.sha256').write_text(digest + '  ' + NAME + '\n')
    print('PASS built', 'staged' if args.staged else 'identical client/server', 'JAR; SHA-256', digest)


if __name__ == '__main__':
    main()
