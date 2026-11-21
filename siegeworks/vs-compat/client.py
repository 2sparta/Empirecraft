"""Update only the synthetic client test branch; no channel or production save is modified."""
from build import R,REPO,SDK,JDK,REF,b,libs,remapper,run,read,jar,NAME
from pathlib import Path
import os,io,json,zipfile,subprocess,hashlib,shutil
c=b/'client';c.mkdir(exist_ok=True)
cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',b/'siegeworks-named.jar',b/'axiomata-named.jar',REPO/'ship-physics/water-fix/build/valkyrienskies-120-2.4.11-empirecraft.1-named.jar',*sorted((REPO/'ship-physics/water-fix/build/nested').glob('*.jar')),*libs,*sorted((REPO/'mods').glob('*.jar'))]
run(JDK/'javac','-J-Xmx512m','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',c,R/'tests/ClientProbe.java')
jar(b/'client-named.jar',{p.relative_to(c).as_posix():p.read_bytes() for p in c.rglob('*.class')});srg=b/'client-srg.jar';srg.unlink(missing_ok=True);run(JDK/'java','-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',b/'client-named.jar',srg,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts')
public=Path('/workspace/work/siegeworks-client-test');fixture=public/'launcher/audit/siegeworks-test';raw=subprocess.check_output(['git','show','HEAD:launcher/audit/siegeworks-test/fixture.zip'],cwd=public)
with zipfile.ZipFile(io.BytesIO(raw)) as z:files={n:z.read(n) for n in z.namelist()}
probe='mods/empirecraft-kitchen-client-probe-TEST-ONLY.jar'
with zipfile.ZipFile(io.BytesIO(files[probe])) as z:content={n:z.read(n) for n in z.namelist() if not n.startswith(('ua/empirecraft/kitchenclienttest/ClientProbe','ua/empirecraft/siegeclienttest/ClientProbe'))}
content.update({n:v for n,v in read(srg).items() if n.endswith('.class')});jar(b/'client-probe.jar',content);files[probe]=(b/'client-probe.jar').read_bytes()
for n in list(files):
 if n.startswith('mods/siegeworks-'):del files[n]
files['mods/'+NAME]=(R/'dist'/NAME).read_bytes();jar(fixture/'fixture.zip',files);shutil.copy2(R/'tests/ClientProbe.java',fixture/'ClientProbe.java')
p=fixture/'fixture.json';report=json.loads(p.read_text());report.update(mod_sha256=hashlib.sha256((R/'dist'/NAME).read_bytes()).hexdigest(),sha256=hashlib.sha256((fixture/'fixture.zip').read_bytes()).hexdigest(),probe_sha256=hashlib.sha256((fixture/'ClientProbe.java').read_bytes()).hexdigest());p.write_text(json.dumps(report,indent=2)+'\n')
p=public/'.github/workflows/siegeworks-preview.yml';s=p.read_text().replace('KITCHEN_CLIENT_PASS waterSettlements=true','KITCHEN_CLIENT_PASS siegeVs=true').replace('Current guides probe did not pass','Siegeworks VS probe did not pass');p.write_text(s)
print('SIEGE_VS_CLIENT_FIXTURE_READY')
