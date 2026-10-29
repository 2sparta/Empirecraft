#!/usr/bin/env python3
"""Transform the packaged renewal class with the actual seasonal Mixin and verify its bytecode."""
import os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent
B=R/'ecology-balance/build/integration'
shutil.rmtree(B,ignore_errors=True);B.mkdir(parents=True)
jars=sorted({p.resolve() for root in [R/'server/libraries',R/'server/mods',R/'mods'] for p in root.rglob('*.jar')},key=lambda p:(0 if p.name=='forge-1.20.1-47.4.23-server.jar' else 1 if p.name in {'asm-9.7.jar','asm-tree-9.7.jar','asm-commons-9.7.jar','asm-analysis-9.7.jar','asm-util-9.7.jar','mixin-0.8.5.jar'} else 2,str(p)))
cp=os.pathsep.join(map(str,jars))
sources=[R/'seasonal-calendar/src/test/java/guardtest'/(n+'.java') for n in ['OfflineMixinService','OfflineProperties','CalendarMixinTest','CalendarPolicyTest','CalendarDataTest']]
a=['--release','17','-proc:none','-cp',cp,'-d',str(B),*map(str,sources)]
argfile=B/'javac.args';argfile.write_text('\n'.join('"'+s+'"' for s in a))
subprocess.run(['java','com.sun.tools.javac.Main','@'+str(argfile)],check=True)
services=B/'META-INF/services';services.mkdir(parents=True)
(services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
(services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
cp=str(B)+os.pathsep+cp
for test,args in [('CalendarPolicyTest',[]),('CalendarDataTest',[]),('CalendarMixinTest',['SERVER'])]:
 subprocess.run(['java','-ea','-cp',cp,'guardtest.'+test,*args],check=True)
