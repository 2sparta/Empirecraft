"""Prepare the existing isolated full-client CI fixture. No production world or launcher channel changes."""
from build import *
C=B/'client';shutil.rmtree(C,ignore_errors=True);C.mkdir()
classpath=[B/'named.jar',*cp()]
run(JDK/'javac','-J-Xmx512m','--release','17','-proc:none','-cp',os.pathsep.join(map(str,classpath)),'-d',C,R/'tests/ClientProbe.java')
archive(B/'client-named.jar',{p.relative_to(C).as_posix():p.read_bytes() for p in C.rglob('*.class')});srg=B/'client-srg.jar';srg.unlink(missing_ok=True)
run(JDK/'java','-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',B/'client-named.jar',srg,SDK/'named-srg.tiny','named','srg',*classpath,'--ignoreConflicts')
public=Path('/workspace/work/siegeworks-client-test');fixture=public/'launcher/audit/siegeworks-test';raw=subprocess.check_output(['git','show','HEAD:launcher/audit/siegeworks-test/fixture.zip'],cwd=public)
with zipfile.ZipFile(io.BytesIO(raw)) as z:files={n:z.read(n) for n in z.namelist() if not n.startswith(('saves/KITCHEN-TEST/empirecraft/ship-observations/','saves/KITCHEN-TEST/empirecraft/ship-icons/'))}
probe='mods/empirecraft-kitchen-client-probe-TEST-ONLY.jar'
with zipfile.ZipFile(io.BytesIO(files[probe])) as z:content={n:z.read(n) for n in z.namelist() if not n.startswith(('ua/empirecraft/kitchenclienttest/ClientProbe','ua/empirecraft/siegeclienttest/ClientProbe'))}
content.update({n:v for n,v in read(srg).items() if n.endswith('.class')});archive(B/'client-probe.jar',content);files[probe]=(B/'client-probe.jar').read_bytes()
for n in list(files):
 if n.startswith(('mods/empirecraft-towny-journeymap-','mods/empirecraft-handbook-')):del files[n]
files['mods/empirecraft-earth-0.1.1.jar']=(REPO/'server/mods/empirecraft-earth-0.1.1.jar').read_bytes()
files['mods/empirecraft-handbook-1.0.7.jar']=(R/'dist/empirecraft-handbook-1.0.7.jar').read_bytes()
files['mods/'+NAME]=(R/'dist'/NAME).read_bytes();archive(fixture/'fixture.zip',files);shutil.copy2(R/'tests/ClientProbe.java',fixture/'ClientProbe.java')
p=fixture/'fixture.json';report=json.loads(p.read_text());report.update(sha256=hashlib.sha256((fixture/'fixture.zip').read_bytes()).hexdigest(),probe_sha256=hashlib.sha256((fixture/'ClientProbe.java').read_bytes()).hexdigest(),bridge_sha256=hashlib.sha256((R/'dist'/NAME).read_bytes()).hexdigest());p.write_text(json.dumps(report,indent=2)+'\n')
p=public/'.github/workflows/siegeworks-preview.yml';s=p.read_text().replace('KITCHEN_CLIENT_PASS siegeVs=true','KITCHEN_CLIENT_PASS shipMap=true').replace('Siegeworks VS probe did not pass','Ship map spyglass probe did not pass').replace('name: Siegeworks aboard Valkyrien Skies client','name: Ship portraits and spyglass recon client');p.write_text(s)
print('SHIP_MAP_CLIENT_FIXTURE_READY')
