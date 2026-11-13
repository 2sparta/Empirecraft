#!/usr/bin/env python3
"""Reproducible Java-17 SRG build against the installed Mohist 1.20.1 runtime."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
VERSION='1.0.3'

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--classpath-dir',type=Path,action='append',help='May be repeated for separate runtime/mod directories.')
    p.add_argument('--staged',action='store_true',help='Build without replacing the installed server JAR')
    args=p.parse_args()
    roots=args.classpath_dir or [REPO/'server']
    jars=sorted({f.resolve() for root in roots for f in root.rglob('*.jar') if not f.name.startswith('empirecraft-world-rules-')})
    required=['forge-1.20.1-47.4.23-server.jar','server-1.20.1-20230612.114412-srg.jar','combatlog-2.8.4-forge+1.20.1.jar']
    for name in required:
        if not any(f.name==name for f in jars):raise SystemExit('Missing runtime dependency: '+name)
    jars.sort(key=lambda f:(0 if f.name==required[0] else 1,str(f)))
    classes=ROOT/'build/classes'
    if classes.exists():shutil.rmtree(classes)
    classes.mkdir(parents=True)
    java_home=os.environ.get('JAVA_HOME')
    compiler=[str(Path(java_home)/'bin/javac')] if java_home else ([shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main'])
    flags=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),*map(str,sorted((ROOT/'src/main/java').rglob('*.java')))]
    argfile=ROOT/'build/javac.args'
    argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in flags)+'\n')
    subprocess.run([*compiler,'@'+str(argfile)],check=True)
    target=(ROOT/'build/libs' if args.staged else REPO/'server/mods')/('empirecraft-world-rules-'+VERSION+'.jar')
    target.parent.mkdir(parents=True,exist_ok=True)
    files={str(f.relative_to(classes)):f.read_bytes() for f in classes.rglob('*.class')}
    resources=ROOT/'src/main/resources'
    files.update({str(f.relative_to(resources)):f.read_bytes() for f in resources.rglob('*') if f.is_file()})
    files['META-INF/MANIFEST.MF']=('Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_world_rules.mixins.json\r\nImplementation-Version: '+VERSION+'\r\n\r\n').encode()
    files['META-INF/LICENSE.txt']=(ROOT/'LICENSE').read_bytes()
    with zipfile.ZipFile(target,'w') as out:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name.replace(os.sep,'/'),(2026,10,24,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            out.writestr(info,data)
    # Share the existing overlay builder: preserve current retirement guards after source rebuilds.
    sys.path.insert(0,str(REPO/'forging-balance'))
    from native_overlay import apply
    raw,_=apply(ROOT,target.read_bytes(),target.name)
    target.write_bytes(raw)
    if not args.staged:
        for previous in target.parent.glob('empirecraft-world-rules-*.jar*'):
            if previous.name not in (target.name,target.name+'.sha256'):previous.unlink()
    digest=hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.jar.sha256').write_text(digest+'  '+target.name+'\n')
    print(target);print('SHA-256:',digest)

if __name__=='__main__':main()


