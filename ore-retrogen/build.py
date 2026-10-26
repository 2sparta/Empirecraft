#!/usr/bin/env python3
"""Compile named sources and reobfuscate with the repository's pinned Sails toolchain."""
import argparse
import hashlib
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
VERSION = '1.1.0'
NAME = f'empirecraft-ore-retrogen-{VERSION}.jar'

def jar(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026,10,26,0,0,0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, data)

def toolchain(path, server):
    if not (path/'javac.args').exists():
        subprocess.run([sys.executable, ROOT/'toolchain.py', path, server],check=True)
    flags = shlex.split((path/'javac.args').read_text())
    deps = flags[flags.index('-cp')+1].split(os.pathsep)
    if not all(Path(p).exists() for p in deps): raise SystemExit('Toolchain contains missing classpath entries; rebuild sails/build.py')
    return deps

def compile_jar(sources, output, shared, server, resources=None):
    deps = toolchain(shared, server)
    build = ROOT/'build'; build.mkdir(exist_ok=True)
    classes = build/(output.stem+'-classes'); shutil.rmtree(classes,ignore_errors=True); classes.mkdir()
    java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    javac = [str(Path(os.environ['JAVA_HOME'])/'bin/javac')] if os.environ.get('JAVA_HOME') else ([shutil.which('javac')] if shutil.which('javac') else [java,'com.sun.tools.javac.Main'])
    cp = os.pathsep.join([str(ROOT/'build/named.jar'),*deps]) if (ROOT/'build/named.jar').exists() else os.pathsep.join(deps)
    args = ['--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',classes,*sources]
    argfile = build/'javac.args'
    argfile.write_text('\n'.join('"'+str(s).replace('\\','\\\\').replace('"','\\"')+'"' for s in args)+'\n')
    subprocess.run([*javac,'@'+str(argfile)],check=True)
    named = build/('fixture-named.jar' if resources is None else 'named.jar')
    jar(named,{str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')})
    srg = build/(output.stem+'-srg.jar'); srg.unlink(missing_ok=True)
    remapper = os.pathsep.join([str(shared/'tiny-remapper.jar'),str(shared/'mapping-io.jar'),*[p for p in deps if Path(p).name.startswith('asm')]])
    subprocess.run([java,'-cp',remapper,'dev.architectury.tinyremapper.Main',named,srg,shared/'named-srg.tiny','named','srg',shared/'runtime-named.jar',shared/'client-named.jar','--ignoreConflicts'],check=True)
    with zipfile.ZipFile(srg) as z: entries = {n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    if resources:
        entries.update({str(p.relative_to(resources)):p.read_bytes() for p in resources.rglob('*') if p.is_file()})
        entries['META-INF/MANIFEST.MF'] = f'Manifest-Version: 1.0\r\nImplementation-Version: {VERSION}\r\n\r\n'.encode()
        entries['META-INF/LICENSE.txt'] = (ROOT/'LICENSE').read_bytes()
    else:
        entries['pack.mcmeta'] = b'{"pack":{"pack_format":15,"description":"TEST ONLY ore fixture"}}'
        entries['META-INF/mods.toml'] = b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_ores_test"\nversion="1.0.0"\ndisplayName="TEST ONLY ore retrogen fixture"\n'
    jar(output,entries)
    return output

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--toolchain',type=Path,default=ROOT/'build/toolchain')
    p.add_argument('--server-home',type=Path,default=REPO/'server')
    p.add_argument('--fixture',action='store_true')
    a = p.parse_args()
    sources = sorted((ROOT/('tests' if a.fixture else 'src/main/java')).rglob('*.java'))
    if a.fixture: sources = [p for p in sources if p.name=='OreSmokeMod.java']
    target = ROOT/'build/empirecraft-ores-test.jar' if a.fixture else REPO/'server/mods'/NAME
    compile_jar(sources,target,a.toolchain.resolve(),a.server_home.resolve(),None if a.fixture else ROOT/'src/main/resources')
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    if not a.fixture: target.with_suffix('.jar.sha256').write_text(digest+'  '+target.name+'\n')
    print(target.name,digest)
if __name__ == '__main__': main()
