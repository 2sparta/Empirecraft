#!/usr/bin/env python3
"""Deterministic Java 17 addon built against the exact installed Forge and Overgeared."""
import argparse, hashlib, os, shutil, subprocess, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
VERSION = '1.1.4'
NAME = 'empirecraft-forging-'+VERSION+'.jar'

def classpath(roots):
    jars = {p.resolve() for root in roots for p in root.rglob('*.jar') if not p.name.startswith('empirecraft-forging-')}
    def priority(p):
        if p.name == 'forge-1.20.1-47.4.23-server.jar': return 0
        if p.name in ['asm-9.7.jar', 'asm-tree-9.7.jar', 'asm-analysis-9.7.jar', 'asm-util-9.7.jar', 'asm-commons-9.7.jar', 'mixin-0.8.5.jar']: return 1
        return 2
    return sorted(jars, key=lambda p: (priority(p), str(p)))

def compiler():
    home = os.environ.get('JAVA_HOME')
    return [str(Path(home)/'bin/javac')] if home else [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']

def archive(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as out:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 25, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            out.writestr(info, data)
    path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--classpath-dir', action='append', type=Path); args = parser.parse_args()
    jars = classpath(args.classpath_dir or [REPO/'server/libraries', REPO/'server/mods', REPO/'mods'])
    output = ROOT/'build/classes'
    if output.exists(): shutil.rmtree(output)
    output.mkdir(parents=True)
    flags = ['--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, jars)), '-d', str(output), *map(str, sorted((ROOT/'src/main/java').rglob('*.java')))]
    argfile = ROOT/'build/javac.args'
    argfile.write_text('\n'.join('"'+v.replace('\\', '\\\\').replace('"', '\\"')+'"' for v in flags)+'\n')
    subprocess.run([*compiler(), '@'+str(argfile)], check=True)
    entries = {p.relative_to(output).as_posix(): p.read_bytes() for p in output.rglob('*.class')}
    resources = ROOT/'src/main/resources'
    entries.update({p.relative_to(resources).as_posix(): p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    entries['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_forging.mixins.json\r\nImplementation-Version: 1.1.4\r\n\r\n'
    baseline=ROOT/'build'/NAME
    archive(baseline,entries)
    from native_overlay import apply
    raw,native_release=apply(ROOT,baseline.read_bytes(),NAME)
    for directory in ['mods', 'server/mods']:
        for old in (REPO/directory).glob('empirecraft-forging-*.jar*'):
            if old.name not in [NAME, NAME+'.sha256']: old.unlink()
        target=REPO/directory/NAME;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
        target.with_suffix('.jar.sha256').write_text(hashlib.sha256(raw).hexdigest()+'  '+NAME+'\n')
    print('PASS identical client/server addon:', hashlib.sha256((REPO/'mods'/NAME).read_bytes()).hexdigest())

if __name__ == '__main__': main()

