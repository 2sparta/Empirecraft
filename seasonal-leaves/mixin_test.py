#!/usr/bin/env python3
"""Apply the actual Calendar/World Rules/plant-growth Mixins and verify transformed bytecode."""
import os,shutil,subprocess,json,argparse
from pathlib import Path
import build
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--server-home',type=Path,default=build.REPO/'server')
p.add_argument('--world-rules',type=Path)
p.add_argument('--calendar',type=Path)
a=p.parse_args()
a.world_rules=a.world_rules or a.server_home/'mods/empirecraft-world-rules-1.0.2.jar'
a.calendar=a.calendar or next((a.server_home/'mods').glob('empirecraft-calendar-*.jar'))
for name,row in json.loads((build.ROOT/'compat-inputs.json').read_text()).items():
 path=getattr(a,name);assert build.sha(path)==row['sha256'] and path.stat().st_size==row['size'],'Changed compatibility input: '+name
libs=a.server_home/'libraries';deps=sorted(libs.rglob('*.jar'));asm=list(libs.glob('org/ow2/asm/*/9.9.1/*.jar'))
jar=build.ROOT/'dist'/build.VERSION/('empirecraft-seasonal-leaves-'+build.VERSION+'.jar')
cp=os.pathsep.join(map(str,[*asm,jar,build.BUILD/'runtime-srg.jar',a.world_rules,a.calendar,*deps]))
out=build.BUILD/'mixin-tests';out.mkdir(parents=True,exist_ok=True)
sources=[build.ROOT/'tests/guardtest'/name for name in ['OfflineMixinService.java','OfflineProperties.java','IntegrationTest.java']]
subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',str(out),*map(str,sources)],check=True)
services=out/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
(services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
(services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
subprocess.run(['java','-ea','-cp',str(out)+os.pathsep+cp,'guardtest.IntegrationTest'],check=True)
