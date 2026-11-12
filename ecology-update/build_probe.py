#!/usr/bin/env python3
"""Compile the test-only ecology probe against real installed game and mod APIs."""
import argparse, hashlib, os, subprocess, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def main():
    p=argparse.ArgumentParser();p.add_argument('--sdk',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
    build=ROOT/'build';classes=build/'classes';classes.mkdir(parents=True,exist_ok=True)
    sdk=a.sdk.resolve();libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    cp=[REPO/'fauna/build/classes/main',REPO/'work/seasonal-leaves/addon-named.jar',REPO/'seasonal-calendar/build/libs/empirecraft-calendar-1.2.2.jar',sdk/'runtime-named.jar',sdk/'client-named.jar',*libs,*sorted((REPO/'server/mods').glob('*.jar')),*sorted((REPO/'mods').glob('*.jar'))]
    args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(ROOT/'tests/EcologyProbe.java')]
    argfile=build/'javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in args))
    subprocess.run([str(a.java_home/'bin/javac'),'@'+str(argfile)],check=True)
    named=build/'probe-named.jar';srg=build/'probe-srg.jar'
    with zipfile.ZipFile(named,'w') as z:
        for file in classes.rglob('*.class'):z.writestr(file.relative_to(classes).as_posix(),file.read_bytes())
    srg.unlink(missing_ok=True);asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))
    subprocess.run([str(a.java_home/'bin/java'),'-Xmx1G','-cp',os.pathsep.join(map(str,[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm])),
        'dev.architectury.tinyremapper.Main',str(named),str(srg),str(sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
    target=build/'empirecraft-ecology-test.jar'
    with zipfile.ZipFile(srg) as src,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
        for name in src.namelist():
            if name.endswith('.class'):z.writestr(name,src.read(name))
        z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_ecology_test"\nversion="1.0.0"\ndisplayName="TEST ONLY ecology"\n')
        z.writestr('ecology-plants.json',(REPO/'seasonal-leaves/src/main/resources/vegetation-profiles.json').read_bytes())
    print('PROBE_BUILD_PASS',hashlib.sha256(target.read_bytes()).hexdigest())

if __name__=='__main__':main()
