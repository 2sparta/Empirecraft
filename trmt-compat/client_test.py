"""Create a synthetic real-client visual test fixture on the isolated CI test branch."""
import hashlib,io,json,os,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent;REPO=R.parent;SDK=Path('/workspace/work/tfc/toolchain');JDK=Path('/workspace/work/tfc/jdk');REF=Path('/workspace/Empirecraft')
b=R/'build/client';b.mkdir(parents=True,exist_ok=True)
libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'))
cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',R/'build/remembered/named.jar',*libs]
classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
subprocess.run([str(JDK/'bin/javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(R/'tests/client/ClientProbe.java')],check=True)
from build_remembered import VERSION, jar
jar(b/'named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});out=b/'srg.jar';out.unlink(missing_ok=True)
subprocess.run([str(JDK/'bin/java'),'-cp',os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm])),'dev.architectury.tinyremapper.Main',str(b/'named.jar'),str(out),str(SDK/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
public=Path('/workspace/work/siegeworks-client-test');fixture=public/'launcher/audit/siegeworks-test'
with zipfile.ZipFile(fixture/'fixture.zip') as z:files={n:z.read(n) for n in z.namelist()}
probe='mods/empirecraft-kitchen-client-probe-TEST-ONLY.jar'
with zipfile.ZipFile(io.BytesIO(files[probe])) as z:files_probe={n:z.read(n) for n in z.namelist() if not n.startswith(('ua/empirecraft/kitchenclienttest/ClientProbe','ua/empirecraft/siegeclienttest/ClientProbe'))}
with zipfile.ZipFile(out) as z:files_probe.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
jar(b/'probe.jar',files_probe);files[probe]=(b/'probe.jar').read_bytes()
for name in list(files):
 if name.startswith(('mods/trmt-','mods/empirecraft-menu-art-','mods/empirecraft-tfc-integration-','mods/vs_sails-','mods/empirecraft-ship-physics-','mods/empirecraft-engagement-')):del files[name]
for p in [R/'dist'/f'trmt-{VERSION}.jar',*list((REPO/'mods').glob('empirecraft-tfc-integration-*.jar')),*list((REPO/'ship-physics/water-fix/dist').glob('*.jar'))]:files['mods/'+p.name]=p.read_bytes()
jar(fixture/'fixture.zip',files);shutil.copy2(R/'tests/client/ClientProbe.java',fixture/'ClientProbe.java')
report=json.loads((fixture/'fixture.json').read_text());report.update(sha256=hashlib.sha256((fixture/'fixture.zip').read_bytes()).hexdigest(),probe_sha256=hashlib.sha256((fixture/'ClientProbe.java').read_bytes()).hexdigest());(fixture/'fixture.json').write_text(json.dumps(report,indent=2)+'\n')
p=public/'.github/workflows/siegeworks-preview.yml';s=p.read_text().replace('--replace-mod-prefix vs_sails-', '--replace-mod-prefix empirecraft-engagement- --replace-mod-prefix vs_sails-').replace('--replace-mod-prefix trmt-', '--replace-mod-prefix vs_sails- --replace-mod-prefix empirecraft-ship-physics- --replace-mod-prefix trmt-').replace('--replace-mod-prefix knockedback-','--replace-mod-prefix trmt- --replace-mod-prefix knockedback-').replace('KITCHEN_CLIENT_PASS menuEquipment=true','KITCHEN_CLIENT_PASS soilMemory=true').replace('Menu equipment probe did not pass','Soil memory probe did not pass');p.write_text(s)
print('SOIL_CLIENT_FIXTURE_READY')
