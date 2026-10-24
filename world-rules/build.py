#!/usr/bin/env python3
"""Reproducible Java-17 SRG build against the installed Mohist 1.20.1 runtime."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--classpath-dir',type=Path,action='append',help='May be repeated for separate runtime/mod directories.')
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
    target=REPO/'server/mods/empirecraft-world-rules-1.0.0.jar'
    target.parent.mkdir(parents=True,exist_ok=True)
    files={str(f.relative_to(classes)):f.read_bytes() for f in classes.rglob('*.class')}
    resources=ROOT/'src/main/resources'
    files.update({str(f.relative_to(resources)):f.read_bytes() for f in resources.rglob('*') if f.is_file()})
    files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_world_rules.mixins.json\r\nImplementation-Version: 1.0.0\r\n\r\n'
    files['META-INF/LICENSE.txt']=(ROOT/'LICENSE').read_bytes()
    with zipfile.ZipFile(target,'w') as out:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name.replace(os.sep,'/'),(2026,10,24,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            out.writestr(info,data)
    digest=hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.jar.sha256').write_text(digest+'  '+target.name+'\n')
    print(target);print('SHA-256:',digest)

if __name__=='__main__':main()
