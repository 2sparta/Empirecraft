#!/usr/bin/env python3
"""Boot only an isolated synthetic world using the complete installed mod set."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
from build import ROOT,REPO,BUILD,NAME,JDK,archive,classpath
def main():
 jdk=JDK;java=jdk/'bin/java';javac=jdk/'bin/javac';runtime=BUILD/'runtime'
 classes=BUILD/'probe-classes';classes.mkdir(parents=True,exist_ok=True)
 addons=[ROOT/'dist'/NAME,ROOT/'dist/empirecraft-armory-1.2.1.jar',ROOT/'dist/chineseweapons-forge-1.20.1-1.2.1.6-empirecraft.2.jar',REPO/'server/mods/empirecraft-locks-compat-1.0.1.jar']
 cp=os.pathsep.join(map(str,[*addons,*classpath()]))
 result=subprocess.run([str(javac),'--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(ROOT/'tests/WorkshopProbe.java')],capture_output=True,text=True);(BUILD/'probe-compile.log').write_text(result.stdout+result.stderr)
 if result.returncode:print(result.stderr[-5000:]);raise SystemExit(result.returncode)
 if runtime.exists():
  assert (runtime/'WORKSHOP-TEST-ONLY').is_file();shutil.rmtree(runtime)
 runtime.mkdir();(runtime/'WORKSHOP-TEST-ONLY').touch();source=REPO/'server';(runtime/'libraries').symlink_to(source/'libraries',target_is_directory=True)
 (runtime/'mods').mkdir();(runtime/'home').mkdir();(runtime/'mohist-config').mkdir()
 replaced=('empirecraft-armory-','chineseweapons-','empirecraft-locks-compat-','empirecraft-workshop-','worldedit-')
 for path in (source/'mods').glob('*.jar'):
  if not path.name.startswith(replaced):shutil.copy2(path,runtime/'mods'/path.name)
 for path in addons:shutil.copy2(path,runtime/'mods'/path.name)
 for name in ['config','defaultconfigs','kubejs']:shutil.copytree(source/name,runtime/name,ignore=shutil.ignore_patterns('logs','*.log','cache'))
 (runtime/'eula.txt').write_text('eula=true\n');(runtime/'mohist-config/mohist.yml').write_text('config-version: 1\n');(runtime/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
 flat={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'features':False,'lakes':False,'structure_overrides':[]}
 (runtime/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(flat)+'\nserver-ip=127.0.0.1\nserver-port=25596\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
 probe={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};probe['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_workshop_test"\nversion="1.0.0"\n'
 archive(runtime/'mods/workshop-test-only.jar',probe)
 spec=json.loads((REPO/'sails/smoke-runtime.json').read_text());boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
 launch=['-Xmx5G','-XX:ActiveProcessorCount=2','-Duser.home='+str(runtime/'home'),'--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
 (runtime/'launch.args').write_text('\n'.join('"'+str(arg)+'"' for arg in launch))
 with (BUILD/'native.log').open('w') as log:result=subprocess.run([str(java),'@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
 text=(BUILD/'native.log').read_text(errors='replace');markers=[line for line in text.splitlines() if 'WORKSHOP_NATIVE_' in line];print('\n'.join(markers))
 assert result.returncode==0 and 'WORKSHOP_NATIVE_PASS' in text and 'WORKSHOP_NATIVE_FAIL' not in text,text[-10000:]
 report=json.loads((runtime/'workshop-probe.json').read_text());report.update(graphical_client_tested=False,production_world_opened=False)
 report['tested_jars']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in addons}
 (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/native.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('WORKSHOP_RUNTIME_PASS')
if __name__=='__main__':main()
