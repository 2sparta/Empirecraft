#!/usr/bin/env python3
"""Run genuine Starcatcher catch completion and persistent civic rewards across two disposable Mohist boots."""
import argparse,hashlib,json,os,shutil,subprocess,sys,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;SDK=REPO/'sails/build'
sys.path.insert(0,str(REPO/'sails'));from build import jar

def compile_fixture():
 folder=ROOT/'build/fixture';folder.mkdir(parents=True,exist_ok=True);classes=folder/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';libs=sorted((REPO/'server/libraries').rglob('*.jar'));cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',*libs]
 args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((ROOT/'tests').glob('*.java'))]
 (folder/'javac.args').write_text('\n'.join('"'+str(a)+'"' for a in args));subprocess.run([java,'com.sun.tools.javac.Main','@'+str(folder/'javac.args')],check=True)
 named=folder/'named.jar';jar(named,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});srg=folder/'srg.jar';srg.unlink(missing_ok=True)
 asm=[p for p in libs if '/org/ow2/asm/' in str(p)];remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
 subprocess.run(list(map(str,[java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',named,srg,SDK/'named-srg.tiny','named','srg',SDK/'runtime-named.jar',SDK/'client-named.jar','--ignoreConflicts'])),check=True)
 with zipfile.ZipFile(srg) as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
 entries['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="All Rights Reserved"\n[[mods]]\nmodId="empirecraft_civic_smoke"\nversion="1.0.0"\ndisplayName="Disposable civic fixture"\n'
 output=folder/'empirecraft-civic-smoke.jar';jar(output,entries);return output

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java',default='/usr/bin/java');p.add_argument('--plugin',type=Path,required=True);p.add_argument('--fixture',type=Path);p.add_argument('--full-modpack',action='store_true');args=p.parse_args();fixture=args.fixture or compile_fixture()
 source=REPO/'server';spec=json.loads((REPO/'sails/smoke-runtime.json').read_text());logs=ROOT/'build/native';logs.mkdir(parents=True,exist_ok=True);results=[]
 patterns=['starcatcher-*','selling_bin-*','fancytabsections-*','libtooltips-*','empirecraft-civic-events-*','empirecraft-no-end-*','empirecraft-towny-journeymap-*','journeymap-forge-*','Contact-forge-*','OELib-*']
 selected=[]
 for pattern in patterns:
  matches=list((source/'mods').glob(pattern+'.jar'));assert len(matches)==1,(pattern,matches);selected.extend(matches)
 if args.full_modpack:
  import importlib.util
  module_spec=importlib.util.spec_from_file_location('civic_full_dependencies',REPO/'engagement/smoke.py');combat=importlib.util.module_from_spec(module_spec);module_spec.loader.exec_module(combat)
  selected=sorted(set(selected)|set(combat.dependencies(source,source/'mods/empirecraft-engagement-1.1.0.jar')))
 hashes={path.name:hashlib.sha256(path.read_bytes()).hexdigest() for path in selected};(REPO/'work').mkdir(exist_ok=True)
 with tempfile.TemporaryDirectory(prefix='civic-',dir=REPO/'work') as temp:
  root=Path(temp);(root/'libraries').symlink_to(source/'libraries',target_is_directory=True);(root/'mods').mkdir();(root/'plugins/Towny/settings').mkdir(parents=True);(root/'mohist-config').mkdir()
  (root/'mohist-config/mohist.yml').write_text('config-version: 1\n');(root/'plugins/Towny/settings/config.yml').write_text("economy:\n  using_economy: 'false'\n");shutil.copyfile(source/'plugins/Towny-0.101.2.0.jar',root/'plugins/Towny-0.101.2.0.jar');shutil.copyfile(args.plugin,root/'plugins'/args.plugin.name);plugin_sha=hashlib.sha256((root/'plugins'/args.plugin.name).read_bytes()).hexdigest()
  for path in [*selected,fixture]:shutil.copyfile(path,root/'mods'/path.name)
  (root/'config/contact').mkdir(parents=True);shutil.copyfile(source/'config/contact/contact_common.json',root/'config/contact/contact_common.json');(root/'eula.txt').write_text('eula=true\n')
  layers=dict(biome='minecraft:plains',layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=62),dict(block='minecraft:grass_block',height=1)],features=False,lakes=False,structure_overrides=[])
  (root/'server.properties').write_text('level-name=test\nlevel-type=minecraft:flat\ngenerator-settings='+json.dumps(layers)+'\nserver-ip=127.0.0.1\nserver-port=25597\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n')
  packs=root/'test/datapacks';packs.mkdir(parents=True)
  compat=source/'world/datapacks/empirecraft-multiverse.zip';shutil.copyfile(compat,packs/compat.name);datapack_sha=hashlib.sha256((packs/compat.name).read_bytes()).hexdigest()
  dimension=packs/'civic-test-dimension';(dimension/'data/minecraft/dimension').mkdir(parents=True)
  (dimension/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Disposable world_europe test dimension'}}))
  (dimension/'data/minecraft/dimension/world_europe.json').write_text(json.dumps({'type':'minecraft:overworld','generator':{'type':'minecraft:flat','settings':layers}}))
  boot=[source/p for p in spec['module_path']];legacy=[source/p for p in spec['legacy_classpath']]
  launch=['-Xmx3G','-XX:ActiveProcessorCount=2','--module-path',os.pathsep.join(map(str,boot)),'--add-modules','ALL-MODULE-PATH','--add-opens','java.base/java.util.jar=cpw.mods.securejarhandler','--add-opens','java.base/java.lang.invoke=cpw.mods.securejarhandler','--add-exports','java.base/sun.security.util=cpw.mods.securejarhandler','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming','-DignoreList='+','.join(p.name for p in boot),'-DlibraryDirectory=libraries','-DlegacyClassPath='+os.pathsep.join(map(str,legacy)),'cpw.mods.bootstraplauncher.BootstrapLauncher','--launchTarget','forgeserver','--fml.forgeVersion','47.4.23','--fml.mcVersion','1.20.1','--fml.forgeGroup','net.minecraftforge','--fml.mcpVersion','20230612.114412','nogui']
  (root/'launch.args').write_text('\n'.join('"'+str(a)+'"' for a in launch))
  for phase in [1,2]:
   log=logs/('phase-'+str(phase)+'.log')
   with log.open('w') as out:r=subprocess.run([args.java,'@launch.args'],cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=360)
   text=log.read_text();lines=[s for s in text.splitlines() if 'CIVIC_NATIVE_' in s];print('\n'.join(lines),flush=True)
   if r.returncode or 'CIVIC_NATIVE_FAIL' in text or 'CIVIC_NATIVE_PASS phase='+str(phase) not in text:print(text[-18000:]);raise SystemExit('Civic smoke failed: '+str(log))
   results.extend(lines)
 report=dict(plugin_sha256=plugin_sha,dimension_pack_sha256=datapack_sha,full_modpack_boot=args.full_modpack,mods_sha256=hashes,results=results,actual_server_restarts=1,graphical_client_tested=False,production_world_tested=False)
 (logs/'result.json').write_text(json.dumps(report,indent=2)+'\n');print('CIVIC_NATIVE_RESTART_PASS')
if __name__=='__main__':main()
