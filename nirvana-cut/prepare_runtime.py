#!/usr/bin/env python3
import os,shutil,subprocess,zipfile
from pathlib import Path
from build import ROOT,REPO,NAME

runtime=ROOT/'build/runtime';classes=ROOT/'build/probe';classes.mkdir(parents=True,exist_ok=True)
jars=sorted((runtime/'libraries').rglob('*.jar'));assert jars
flags=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(ROOT/'NirvanaAudit.java')]
args=ROOT/'build/javac.args';args.write_text('\n'.join('"'+v.replace('\\','\\\\').replace('"','\\"')+'"' for v in flags))
subprocess.run(['javac','@'+str(args)],check=True)
mods=runtime/'mods';mods.mkdir(exist_ok=True)
with zipfile.ZipFile(mods/'nirvana-audit-test-only.jar','w',zipfile.ZIP_DEFLATED) as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
 z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="nirvana_audit"\nversion="1.0"\n')
 z.writestr('pack.mcmeta','{"pack":{"pack_format":15,"description":"Isolated native Nirvana acceptance probe"}}')
kotlin=list((REPO/'mods').glob('kotlinforforge-*.jar'));assert len(kotlin)==1;shutil.copy2(kotlin[0],mods/kotlin[0].name)
(runtime/'eula.txt').write_text('eula=true\n');(runtime/'server.properties').write_text('server-ip=127.0.0.1\nonline-mode=false\nlevel-type=minecraft:flat\ndifficulty=normal\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\n')
config=runtime/'config';config.mkdir(exist_ok=True);(config/'nirvana-common.toml').write_text('[nirvana.smoking]\nreeferAfterHits = 0\nreeferSpawnChance = 1.0\n')
print('PASS test-only probe and exact Kotlin dependency prepared; Reefer configuration deliberately enabled at maximum')
