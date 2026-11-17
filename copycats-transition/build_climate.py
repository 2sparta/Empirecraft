"""Overlay only the climate adapter on the pinned existing integration; retain every other class."""
import hashlib,os,shutil,subprocess,zipfile,json
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
sysroot=REPO/'siegeworks';import sys;sys.path.insert(0,str(sysroot));from build import SDK,JDK,REF,archive
b=ROOT/'build/climate';b.mkdir(parents=True,exist_ok=True)
base=REPO/'mods/empirecraft-tfc-integration-1.0.3.jar';old=ROOT/'upstream'/base.name;old.parent.mkdir(exist_ok=True)
if not old.exists():shutil.copy2(base,old)
assert hashlib.sha256(old.read_bytes()).hexdigest()==json.loads((ROOT/'inputs.json').read_text())[old.name]
with zipfile.ZipFile(old) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'));cp=[Path('/workspace/work/flux-continuous/casting/build/compile-access.jar'),SDK/'runtime-named.jar',SDK/'client-named.jar',*list((REPO/'siegeworks/build/named').glob('*.jar')),*libs,*list((REPO/'mods').glob('*.jar')),old]
classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
subprocess.run([str(JDK/'bin/javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(REPO/'tfc-integration/src/main/java/ua/empirecraft/tfc/Agriculture.java')],check=True)
archive(b/'named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});target=b/'srg.jar';target.unlink(missing_ok=True)
subprocess.run([str(JDK/'bin/java'),'-cp',os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm])),'dev.architectury.tinyremapper.Main',str(b/'named.jar'),str(target),str(SDK/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
with zipfile.ZipFile(target) as z:
 changed={n:z.read(n) for n in z.namelist() if n.endswith('.class')};assert all(n.startswith('ua/empirecraft/tfc/Agriculture') for n in changed)
files.update(changed);files['META-INF/mods.toml']=files['META-INF/mods.toml'].replace(b'version="1.0.3"',b'version="1.0.4"')
files['META-INF/MANIFEST.MF']=files.get('META-INF/MANIFEST.MF',b'Manifest-Version: 1.0\r\n\r\n').replace(b'Implementation-Version: 1.0.3',b'Implementation-Version: 1.0.4')
for side in ['mods','server/mods']:
 for p in (REPO/side).glob('empirecraft-tfc-integration-1.0.3.jar*'):
  dest=REPO/'disabled-mods/replaced-2026-11-01'/side/p.name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest);p.unlink()
 p=REPO/side/'empirecraft-tfc-integration-1.0.4.jar';archive(p,files);p.with_suffix('.jar.sha256').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
(ROOT/'audit/climate-build.json').write_text(json.dumps(dict(status='passed',base_sha256=hashlib.sha256(old.read_bytes()).hexdigest(),changed_classes=list(changed),retained_unrelated_classes=True),indent=2)+'\n');print('CLIMATE_BUILD_PASS')
