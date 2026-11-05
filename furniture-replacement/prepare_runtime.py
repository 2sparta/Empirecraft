#!/usr/bin/env python3
"""Build a test-only mod against the installed real Forge/Minecraft server APIs."""
import os, shutil, subprocess, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
runtime=REPO/'furniture-replacement/build/runtime'
classes=ROOT/'build/classes';classes.mkdir(parents=True,exist_ok=True)
jars=sorted((runtime/'libraries').rglob('*.jar'))
assert jars, 'Install the exact Forge server first'
flags=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(ROOT/'FurnitureAudit.java')]
argfile=ROOT/'build/javac.args';argfile.write_text('\n'.join('"'+v.replace('\\','\\\\').replace('"','\\"')+'"' for v in flags))
compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
subprocess.run([*compiler,'@'+str(argfile)],check=True)
mods=runtime/'mods';mods.mkdir(exist_ok=True)
for pattern in ['handcrafted-*.jar','timber_joinery-*.jar','resourcefullib-*.jar']:
    matches=list((REPO/'mods').glob(pattern));assert len(matches)==1,pattern
    shutil.copy2(matches[0],mods/matches[0].name)
with zipfile.ZipFile(mods/'furniture-audit-test-only.jar','w',zipfile.ZIP_DEFLATED) as z:
    for path in classes.rglob('*.class'):z.write(path,path.relative_to(classes).as_posix())
    z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="furniture_audit"\nversion="1.0"\n')
(runtime/'eula.txt').write_text('eula=true\n')
(runtime/'user_jvm_args.txt').write_text('-Xmx1536M\n')
(runtime/'server.properties').write_text('server-ip=127.0.0.1\nonline-mode=false\nlevel-type=minecraft:flat\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\n')
print('PASS test-only probe compiled against real Forge/Minecraft; disposable loopback-only test world prepared')
