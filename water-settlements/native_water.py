"""Run actual VS/Mohist engines in a minimal, disposable world; never open a production save."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
from build_water import jar
def read(path):
 with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith("/")}
R=Path(__file__).resolve().parent;REPO=R.parent;REF=Path('/workspace/Empirecraft');SDK=Path('/workspace/work/tfc/toolchain');JDK=Path('/workspace/work/tfc/jdk/bin');b=R/'build/native'
libs=sorted((REF/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)];remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
def run(*args):subprocess.run(list(map(str,args)),check=True)
mods=[next((REPO/'mods').glob(p)) for p in ['valkyrienskies-*.jar','kotlinforforge-*.jar','create-1*.jar','copycats-*.jar','architectury-*.jar','empirecraft-no-end-*.jar']]
mods+=[R/'dist/vs_sails-1.20.1-0.3.2-empirecraft.19.4-forge.jar',next((REPO/'mods').glob('empirecraft-ship-physics-*.jar'))]
nested=b/'nested';nested.mkdir(parents=True,exist_ok=True)
for p in mods:
 with zipfile.ZipFile(p) as z:
  for name in z.namelist():
   if name.endswith('.jar') and not (nested/Path(name).name).exists():(nested/Path(name).name).write_bytes(z.read(name))
# Remap only the three mod APIs used by the fixture. No bundled Minecraft classes.
mapped=[]
for p in mods:
 if not p.name.startswith(('vs_sails','empirecraft-ship-physics','valkyrienskies')):continue
 target=b/(p.stem+'-named.jar');
 if not target.exists():run(JDK/'java','-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',p,target,SDK/'named-srg.tiny','srg','named',SDK/'runtime-srg.jar','--ignoreConflicts');mapped.append(target)
cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',*mapped,*mods,*sorted(nested.glob('*.jar')),*libs]
c=b/'test-classes';shutil.rmtree(c,ignore_errors=True);c.mkdir()
run(JDK/'javac','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',c,R/'tests/native/WaterProbe.java')
jar(b/'probe-named.jar',{p.relative_to(c).as_posix():p.read_bytes() for p in c.rglob('*.class')})
out=b/'probe-srg.jar';out.unlink(missing_ok=True);run(JDK/'java','-cp',remapper,'dev.architectury.tinyremapper.Main',b/'probe-named.jar',out,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts')
files=read(out);files['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_water_probe"\nversion="1"\n';probe=b/'water-probe-TEST-ONLY.jar';jar(probe,files)
runtime=Path('/workspace/work/ship-water-native');runtime.mkdir(exist_ok=True)
mark=runtime/'SHIP-WATER-TEST-ONLY'
if not mark.exists():
 assert not any(runtime.iterdir());mark.write_text('Synthetic world only\n')
assert mark.is_file()
if (runtime/'test').exists() and not os.environ.get('REUSE_EMPTY_NATIVE_WORLD'):shutil.rmtree(runtime/'test')
(runtime/'libraries').symlink_to(REF/'server/libraries',target_is_directory=True) if not (runtime/'libraries').exists() else None
(runtime/'mods').mkdir(exist_ok=True)
for p in (runtime/'mods').glob('*.jar'):p.unlink()
testbase=next(p for p in mods if p.name.startswith('vs_sails-'));files=read(testbase);files.update({n:v for n,v in read(probe).items() if n.endswith('.class')});files['META-INF/mods.toml']+=b'\n[[mods]]\nmodId="empirecraft_water_probe"\nversion="1"\n';combined=b/'vs-sails-water-probe-TEST-ONLY.jar';jar(combined,files)
for p in [*[p for p in mods if p!=testbase],combined]:(runtime/'mods'/p.name).symlink_to(p.resolve())
(runtime/'mohist-config').mkdir(exist_ok=True);(runtime/'mohist-config/mohist.yml').write_text('config-version: 1\n')
(runtime/'eula.txt').write_text('eula=true\n');(runtime/'config/vs_sails').mkdir(parents=True,exist_ok=True);(runtime/'config/vs_sails/config.cfg').write_text('enable-aerodynamic-wind=false\nwave-strength=0\n')
settings=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=2),dict(block='minecraft:water',height=132)],features=False,lakes=False,structure_overrides=[])
(runtime/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(settings)+'\nserver-ip=127.0.0.1\nserver-port=25596\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nallow-nether=false\nmax-tick-time=120000\n')
spec=json.loads((REPO/'sails/smoke-runtime.json').read_text());boot=[REF/'server'/p for p in spec['module_path']];legacy=[REF/'server'/p for p in spec['legacy_classpath']]
args=['-Xmx2G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
(runtime/'launch.args').write_text('\n'.join('"'+str(x)+'"' for x in args))
log=b/'native.log'
with log.open('w') as output:result=subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=output,stderr=subprocess.STDOUT,timeout=420)
text=log.read_text(errors='replace');markers=[line for line in text.splitlines() if 'WATER_NATIVE_' in line or 'WATER_TOPOLOGY_PASS' in line]
print('\n'.join(markers))
assert result.returncode==0 and 'WATER_NATIVE_PASS' in text and 'WATER_NATIVE_FAIL' not in text,log
(R/'native.json').write_text(json.dumps(dict(status='passed',markers=markers,production_world_opened=False,artifacts={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in mods if p.parent==R/'dist'}),indent=2)+'\n')
