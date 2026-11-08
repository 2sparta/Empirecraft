#!/usr/bin/env python3
"""Verify empty-place visibility using real VS ships and a disposable Mohist world."""
import argparse,hashlib,json,os,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
sys.path.insert(0,str(ROOT));from build_update import jar,sha
def main():
 p=argparse.ArgumentParser();p.add_argument('--server-home',type=Path,default=REPO/'server');p.add_argument('--java-home',type=Path);p.add_argument('--runtime-java',default='java');p.add_argument('--reset',action='store_true');a=p.parse_args();server=a.server_home.resolve();build=ROOT/'build/native-observation';build.mkdir(parents=True,exist_ok=True)
 java=str(a.java_home/'bin/java') if a.java_home else 'java';javac=str(a.java_home/'bin/javac') if a.java_home else 'javac';sdk=ROOT/'build/update';flags=(sdk/'bridge-helpers.args').read_text();import shlex
 words=shlex.split(flags);cp=words[words.index('-cp')+1]+os.pathsep+str(sdk/'bridge-classes');classes=build/'classes';classes.mkdir(exist_ok=True)
 args=['--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(ROOT/'tests/FleetObservationSmoke.java')];argfile=build/'compile.args';argfile.write_text('\n'.join('"'+str(v)+'"' for v in args));subprocess.run([javac,'@'+str(argfile)],check=True)
 named=build/'fixture-named.jar';jar(named,{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')});srg=build/'fixture-srg.jar';srg.unlink(missing_ok=True)
 subprocess.run([java,'-Xmx1G','-cp',str(named)+os.pathsep+cp,'net.md_5.specialsource.SpecialSource','-i',str(named),'-o',str(srg),'-m',str(sdk/'sdk/named-srg.srg'),'-l','--quiet'],check=True)
 with zipfile.ZipFile(srg) as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
 entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_observation_test"\nversion="1.0.0"\n'
 fixture=build/'observation-test.jar';jar(fixture,entries);world=build/'world'
 if a.reset:shutil.rmtree(world,ignore_errors=True)
 (world/'mods').mkdir(parents=True,exist_ok=True);(world/'plugins/Towny/settings').mkdir(parents=True,exist_ok=True);(world/'mohist-config').mkdir(exist_ok=True)
 if not (world/'libraries').exists():(world/'libraries').symlink_to(server/'libraries',target_is_directory=True)
 deps=[]
 for pattern in ['valkyrienskies-120-2.4.11-empirecraft.1.jar','kotlinforforge-4.12.0-all*.jar','architectury-9.2.14-forge.jar','journeymap-forge-1.20.1-6.0.7.jar','empirecraft-no-end-1.0.0.jar']:
  source,=list((server/'mods').glob(pattern));shutil.copyfile(source,world/'mods'/source.name);deps.append(source)
 bridge=ROOT/'dist/fleet-fixes-1.0/empirecraft-towny-journeymap-1.11.0.jar';shutil.copyfile(bridge,world/'mods'/bridge.name);shutil.copyfile(fixture,world/'mods'/fixture.name);shutil.copyfile(server/'plugins/Towny-0.101.2.0.jar',world/'plugins/Towny-0.101.2.0.jar')
 (world/'plugins/Towny/settings/config.yml').write_text("economy:\n  using_economy: 'false'\n");(world/'mohist-config/mohist.yml').write_text('config-version: 1\n');(world/'eula.txt').write_text('eula=true\n')
 (world/'server.properties').write_text('level-name=observation-test\nlevel-type=minecraft:flat\ngenerator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":62},{"block":"minecraft:water","height":16}],"features":false,"lakes":false,"structure_overrides":[]}\nserver-ip=127.0.0.1\nserver-port=25596\nonline-mode=false\nallow-nether=false\nview-distance=4\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
 spec=json.loads((REPO/'sails/smoke-runtime.json').read_text());boot=[server/v for v in spec['module_path']];legacy=[server/v for v in spec['legacy_classpath']]
 launch=['-Xmx3G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(f.name for f in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui'];(world/'launch.args').write_text('\n'.join('"'+v+'"' for v in launch))
 log=build/'server.log'
 with log.open('w') as output:r=subprocess.run([a.runtime_java,'@launch.args'],cwd=world,stdout=output,stderr=subprocess.STDOUT,timeout=240)
 text=log.read_text();lines=[v for v in text.splitlines() if 'OBSERVATION_CHECK' in v or 'FLEET_OBSERVATION_NATIVE_' in v];print('\n'.join(lines))
 if r.returncode or 'FLEET_OBSERVATION_NATIVE_PASS' not in text:print(text[-18000:]);raise SystemExit('Native observation test failed')
 (build/'result.json').write_text(json.dumps({'bridge_sha256':sha(world/'mods'/bridge.name),'dependencies':{f.name:sha(f) for f in deps},'results':lines,'graphical_client_tested':False,'production_world_tested':False},indent=2)+'\n')
if __name__=='__main__':main()
