"""Compile an SRG fixture and boot a disposable full Forge/Mohist world."""
import json,os,shutil,subprocess,zipfile
from pathlib import Path
from build import ROOT,REPO,classpath,compiler

def main():
 work=REPO/'work/gunsmith-native';work.mkdir(parents=True,exist_ok=True)
 classes=work/'classes';classes.mkdir(exist_ok=True)
 jars=classpath([REPO/'server/libraries',REPO/'server/mods',REPO/'mods'])
 subprocess.run([*compiler(),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(ROOT/'native-tests/GunsmithNative.java')],check=True)
 runtime=work/'runtime'
 if runtime.exists():
  assert (runtime/'GUNSMITH-TEST-ONLY').is_file();shutil.rmtree(runtime)
 runtime.mkdir();(runtime/'GUNSMITH-TEST-ONLY').touch();source=REPO/'server'
 (runtime/'libraries').symlink_to(source/'libraries',target_is_directory=True)
 (runtime/'mods').mkdir();(runtime/'home').mkdir();(runtime/'mohist-config').mkdir()
 for p in (source/'mods').glob('*.jar'):shutil.copyfile(p,runtime/'mods'/p.name)
 for name in ['config','defaultconfigs','kubejs']:
  shutil.copytree(source/name,runtime/name)
 (runtime/'eula.txt').write_text('eula=true\n');(runtime/'mohist-config/mohist.yml').write_text('config-version: 1\n')
 (runtime/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
 flat={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'features':False,'lakes':False,'structure_overrides':[]}
 (runtime/'server.properties').write_text('level-name=world\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(flat)+'\nserver-ip=127.0.0.1\nserver-port=25596\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
 with zipfile.ZipFile(runtime/'mods/empirecraft-gunsmith-test.jar','w') as z:
  for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes))
  z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_gunsmith_test"\nversion="1.0.0"\n')
 spec=json.loads((REPO/'sails/smoke-runtime.json').read_text());boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
 launch=['-Xmx6G','-XX:ActiveProcessorCount=2','-Duser.home='+str(runtime/'home'),'--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
 (runtime/'launch.args').write_text('\n'.join('"'+str(a)+'"' for a in launch))
 with (work/'native.log').open('w') as log:result=subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
 text=(work/'native.log').read_text(errors='replace');markers=[l for l in text.splitlines() if 'GUNSMITH_NATIVE_' in l];print('\n'.join(markers))
 assert result.returncode==0 and 'GUNSMITH_NATIVE_PASS' in text and 'GUNSMITH_NATIVE_FAIL' not in text, text[-16000:]
 assert '[ERROR]' not in (runtime/'logs/kubejs/server.log').read_text(errors='replace')
 (REPO/'validation/gunsmith-native.json').write_text(json.dumps({'passed':True,'markers':markers,'production_world_opened':False,'graphical_client_tested':False},indent=2)+'\n')
if __name__=='__main__':main()
