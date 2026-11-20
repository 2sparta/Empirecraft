import sys,os,subprocess,hashlib,json,zipfile,shutil
from pathlib import Path
R=Path(__file__).resolve().parent;REPO=R.parents[1];sys.path.insert(0,str(REPO/'siegeworks'))
from build import SDK,JDK,REF,archive
b=R/'build/client';b.mkdir(parents=True,exist_ok=True)
libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'))
cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',R/'build/named.jar',R/'build/deps/quickskin-named.jar',REPO/'mods/player-animation-lib-forge-1.0.2-rc1+1.20.jar',*libs]
classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
subprocess.run([str(JDK/'bin/javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(R/'tests/menu-equipment/ClientProbe.java')],check=True)
archive(b/'named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});out=b/'srg.jar';out.unlink(missing_ok=True)
subprocess.run([str(JDK/'bin/java'),'-Xmx2G','-cp',os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm])),'dev.architectury.tinyremapper.Main',str(b/'named.jar'),str(out),str(SDK/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
public=Path('/workspace/work/siegeworks-client-test');fixture=public/'launcher/audit/siegeworks-test'
with zipfile.ZipFile(fixture/'fixture.zip') as z:files={n:z.read(n) for n in z.namelist()}
probe='mods/empirecraft-kitchen-client-probe-TEST-ONLY.jar'
import io
with zipfile.ZipFile(io.BytesIO(files[probe])) as z:jar={n:z.read(n) for n in z.namelist() if not n.startswith(('ua/empirecraft/kitchenclienttest/ClientProbe','ua/empirecraft/siegeclienttest/ClientProbe'))}
with zipfile.ZipFile(out) as z:jar.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
archive(b/'probe.jar',jar);files[probe]=(b/'probe.jar').read_bytes()
files['mods/empirecraft-launcher-client-0.3.0.jar']=(R/'dist/empirecraft-launcher-client-0.3.0.jar').read_bytes()
archive(fixture/'fixture.zip',files);shutil.copy2(R/'tests/menu-equipment/ClientProbe.java',fixture/'ClientProbe.java')
report=json.loads((fixture/'fixture.json').read_text());report.update(sha256=hashlib.sha256((fixture/'fixture.zip').read_bytes()).hexdigest(),probe_sha256=hashlib.sha256((fixture/'ClientProbe.java').read_bytes()).hexdigest(),native_sha256=hashlib.sha256(files['mods/empirecraft-launcher-client-0.3.0.jar']).hexdigest());(fixture/'fixture.json').write_text(json.dumps(report,indent=2)+'\n')
workflow=public/'.github/workflows/siegeworks-preview.yml';s=workflow.read_text();s=s.replace('--replace-mod-prefix TerraFirmaCraft-','--replace-mod-prefix empirecraft-launcher-client- --replace-mod-prefix TerraFirmaCraft-') if '--replace-mod-prefix empirecraft-launcher-client-' not in s else s;s=s.replace('          exit "$result"', '\n'.join(['          python - <<\'PYCHECK\'', '          from pathlib import Path', '          text=Path("launcher/kitchen-captures/logs/latest.log").read_text(errors="replace")', '          assert "KITCHEN_CLIENT_PASS knocked=true" in text, "KnockedBack-specific probe did not pass"', '          PYCHECK', '          exit "$result"'])) if 'PYCHECK' not in s else s
s=s.replace('KITCHEN_CLIENT_PASS knocked=true','KITCHEN_CLIENT_PASS menuEquipment=true').replace('KnockedBack-specific probe did not pass','Menu equipment probe did not pass')
workflow.write_text(s)
print('CLIENT_FIXTURE_READY')
