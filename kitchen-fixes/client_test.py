"""Prepare a test-only, offline graphical fixture. Never publishes a production channel."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,default=Path('/workspace/work/kitchen-client'));p.add_argument('--world-dir',type=Path);p.add_argument('--legacy-world',action='store_true');p.add_argument('--runtime',type=Path,default=Path('/workspace/work/kitchen-runtime-1'));a=p.parse_args()
a.work.mkdir(exist_ok=True);sdk=Path('/workspace/work/tfc/toolchain');jdk=Path('/workspace/work/tfc/jdk');ref=Path('/workspace/Empirecraft');libs=list((ref/'server/libraries').rglob('*.jar'));asm=list((ref/'server/libraries/org/ow2/asm').rglob('*.jar'));remapper=[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm]
cp=[ROOT/'build/hooks-named.jar',sdk/'runtime-named.jar',sdk/'client-named.jar',*list((a.runtime/'named').glob('*.jar')),*list((ROOT/'build/named').glob('*.jar')),*libs]
classes=a.work/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
subprocess.run([str(jdk/'bin/javac'),'--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(ROOT/'ClientProbe.java'),*map(str,(ROOT/'client-src').rglob('*.java'))],check=True)
jar=a.work/'probe-named.jar';srg=a.work/'probe-srg.jar';srg.unlink(missing_ok=True)
with zipfile.ZipFile(jar,'w') as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
subprocess.run([str(jdk/'bin/java'),'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(jar),str(srg),str(sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True,stdout=subprocess.DEVNULL)
with zipfile.ZipFile(srg,'a') as z:
 if a.legacy_world:
  assert a.world_dir, 'Legacy checks need a saved previous fixture'
  z.writestr('empirecraft-kitchen-legacy-fixture','TEST ONLY old registry and saved ItemStack migration')
 z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_kitchen_client_test.mixins.json\r\n\r\n')
 z.writestr('empirecraft_kitchen_client_test.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'ua.empirecraft.kitchenclienttest.mixin','compatibilityLevel':'JAVA_17','mixins':['OfflineTownyCommands'],'injectors':{'defaultRequire':1}}))
 z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_kitchen_client_probe"\nversion="1.0.0"\n');z.writestr('pack.mcmeta','{"pack":{"pack_format":15,"description":"TEST ONLY offline kitchen"}}')
print('CLIENT_PROBE_BUILD_PASS',flush=True)
# Build fixture only after the full native probe passed and the server saved its synthetic world.
world=a.world_dir or a.runtime/'runtime/test';assert (a.runtime/'runtime/TFC-INTEGRATION-TEST-ONLY').is_file()
assert world.resolve().is_relative_to(a.runtime.resolve()) and (world/'level.dat').is_file()
if a.world_dir:assert world.name.startswith('previous-test-'), 'Only a saved synthetic fixture is allowed'
else:
 log=(a.runtime/'native.log').read_text(errors='replace');assert 'KITCHEN_NATIVE_PASS' in log and 'KITCHEN_NATIVE_FAIL' not in log
overlay=a.work/'overlay';shutil.rmtree(overlay,ignore_errors=True);overlay.mkdir();(overlay/'mods').mkdir()
for source in (ROOT/'dist').glob('*.jar'):shutil.copy2(source,overlay/'mods'/source.name)
shutil.copy2(srg,overlay/'mods/empirecraft-kitchen-client-probe-TEST-ONLY.jar')
for name in json.loads((ROOT/'audit/data.json').read_text())['files']:
 f=REPO/'kubejs'/name;target=overlay/'kubejs'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,target)
# Only synthetic test-world state, no accounts, players, networks, hosting data or server configs.
allowed={'level.dat','region','data','entities','poi'}
for source in world.rglob('*'):
 rel=source.relative_to(world)
 if not source.is_file() or rel.parts[0] not in allowed or source.is_symlink():continue
 target=overlay/'saves/KITCHEN-TEST'/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
target=a.work/'fixture.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
 for f in sorted(overlay.rglob('*')):
  if f.is_file():z.write(f,f.relative_to(overlay))
print('CLIENT_FIXTURE_READY',target,target.stat().st_size,hashlib.sha256(target.read_bytes()).hexdigest())
