#!/usr/bin/env python3
"""Verify exact existing World Rules + Calendar Mixin composition without loading a world."""
import argparse,os,shlex,shutil,subprocess,json,hashlib
from pathlib import Path
import build
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--world-rules',type=Path,required=True)
p.add_argument('--calendar',type=Path,required=True)
a=p.parse_args()
for name,row in json.loads((build.ROOT/'compat-inputs.json').read_text()).items():
    raw=getattr(a,name).read_bytes()
    assert hashlib.sha256(raw).hexdigest()==row['sha256'] and len(raw)==row['size'], 'Compatibility input changed: '+name
flags=shlex.split((build.SHARED/'javac.args').read_text());deps=flags[flags.index('-cp')+1].split(os.pathsep)
# Put modern ASM before legacy tool JARs that bundle an older ASM.
asm=sorted([p for p in deps if Path(p).name.startswith('asm')],reverse=True)
# Exact SRG runtime precedes named classes during the offline transformation.
cp=os.pathsep.join([*asm,str(build.SHARED/'runtime-srg.jar'),str(a.world_rules.resolve()),str(a.calendar.resolve()),str(build.REPO/'server/mods/empirecraft-seasonal-leaves-1.1.0.jar'),str(build.ROOT/'build/mods-srg.jar'),*deps])
out=build.ROOT/'build/mixin-tests';out.mkdir(parents=True,exist_ok=True)
compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
subprocess.run([*compiler,'--release','17','-proc:none','-cp',cp,'-d',str(out),*map(str,(build.ROOT/'tests/guardtest').glob('*.java'))],check=True)
services=out/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
(services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
(services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
subprocess.run(['java','-ea','-cp',str(out)+os.pathsep+cp,'guardtest.RareMixinTest'],check=True)
