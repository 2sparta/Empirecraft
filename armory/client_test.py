"""Prepare the offline graphical fixture from the saved synthetic server world."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
from build import ROOT,REPO,SDK,JDK,REF,run,archive,sha
work=Path('/workspace/work/armory-client');work.mkdir(exist_ok=True);runtime=Path('/workspace/work/armory-native-1/runtime');assert (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file()
parser=argparse.ArgumentParser();parser.add_argument('--world',type=Path);args=parser.parse_args()
libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'));remapper=[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]
cp=[ROOT/'build/named.jar',Path('/workspace/work/flux-continuous/casting/build/compile-access.jar'),SDK/'runtime-named.jar',SDK/'client-named.jar',*list((ROOT/'build/named').glob('*.jar')),*libs,*list((REPO/'mods').glob('*.jar'))]
classes=work/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
guard=REPO/'kitchen-fixes/client-src/ua/empirecraft/kitchenclienttest/mixin/OfflineTownyCommands.java'
run([JDK/'bin/javac','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,ROOT/'ClientProbe.java',guard],'client-compile')
archive(work/'named.jar',{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')});srg=work/'srg.jar';srg.unlink(missing_ok=True)
run([JDK/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',work/'named.jar',srg,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts'],'client-reobf')
with zipfile.ZipFile(srg,'a') as z:
 z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_kitchen_client_test.mixins.json\r\n\r\n');z.writestr('empirecraft_kitchen_client_test.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'ua.empirecraft.kitchenclienttest.mixin','compatibilityLevel':'JAVA_17','mixins':['OfflineTownyCommands'],'injectors':{'defaultRequire':1}}));z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_kitchen_client_probe"\nversion="1.0.0"\n');z.writestr('pack.mcmeta','{"pack":{"pack_format":15,"description":"TEST ONLY armorer workbench"}}')
log=Path('/workspace/work/armory-native-1/native.log').read_text(errors='replace')
world=args.world or runtime/'test'
if args.world:assert world.resolve().is_relative_to(runtime.parent.resolve()) and world.name.startswith('previous-test-'),'Only an archived synthetic fixture is allowed'
else:assert 'ARMORY_NATIVE_PASS' in log and 'ARMORY_NATIVE_FAIL' not in log
assert (world/'level.dat').is_file();files={}
for prefix in ['TerraFirmaCraft-','legendarysurvivaloverhaul-','handson_delight-','diegesis-','farmersrespite-','empirecraft-armory-']:
 f=next((ROOT/'dist' if prefix=='empirecraft-armory-' else REPO/'mods').glob(prefix+'*.jar'));files['mods/'+f.name]=f.read_bytes()
files['mods/empirecraft-kitchen-client-probe-TEST-ONLY.jar']=srg.read_bytes();files['config/overgeared-common.toml']=(REPO/'config/overgeared-common.toml').read_bytes()
for name in json.loads((REPO/'kitchen-fixes/audit/data.json').read_text())['files']:files['kubejs/'+name]=(REPO/'kubejs'/name).read_bytes()
for f in world.rglob('*'):
 rel=f.relative_to(world)
 if f.is_file() and not f.is_symlink() and rel.parts[0] in {'level.dat','region','data','entities','poi'}:files['saves/KITCHEN-TEST/'+rel.as_posix()]=f.read_bytes()
target=work/'fixture.zip';archive(target,files)
record=dict(sha256=sha(target),probe_sha256=sha(ROOT/'ClientProbe.java'),guard_sha256=sha(guard),mod_sha256=sha(ROOT/'dist/empirecraft-armory-1.0.0.jar'))
(work/'fixture.json').write_text(json.dumps(record,indent=2)+'\n');print('ARMORY_CLIENT_FIXTURE_PASS',sha(target))
