#!/usr/bin/env python3
"""Real Forge restarts: original registries/world -> trimmed registries/world + mob guard."""
import argparse,json,os,shutil,subprocess,tempfile,zipfile
from pathlib import Path
from build import ROOT,REPO,NAMES,MOD,archive,ENV

def run(command, **kw):subprocess.run(list(map(str,command)),check=True,env=ENV,**kw)
def main():
 p=argparse.ArgumentParser();p.add_argument('--runtime',type=Path,required=True);p.add_argument('--toolchain',type=Path,required=True);p.add_argument('--upstream',type=Path,required=True);a=p.parse_args()
 runtime=a.runtime.resolve();tool=a.toolchain.resolve();build=ROOT/'build';classes=build/'test-classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 deps=[tool/'runtime-named.jar',tool/'client-named.jar',*runtime.glob('libraries/**/*.jar')]
 run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join(map(str,deps)),'-d',classes,ROOT/'tests/TrimSmoke.java'])
 archive(build/'fixture-named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
 remapper=os.pathsep.join(map(str,[tool/'tiny-remapper.jar',tool/'mapping-io.jar',*runtime.glob('libraries/org/ow2/asm/**/*.jar')]))
 srg=build/'fixture-srg.jar';srg.unlink(missing_ok=True)
 run(['java','-cp',remapper,'dev.architectury.tinyremapper.Main',build/'fixture-named.jar',srg,tool/'named-srg.tiny','named','srg',tool/'runtime-named.jar',tool/'client-named.jar','--ignoreConflicts'])
 with zipfile.ZipFile(srg) as z:files={n:z.read(n) for n in z.namelist()}
 files['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_trim_test"\nversion="1.0.0"\n'
 files['expected.json']=(ROOT/'audit/removed.json').read_bytes();archive(build/'fixture.jar',files)
 with tempfile.TemporaryDirectory(prefix='empirecraft-trim-') as tmp:
  root=Path(tmp);(root/'libraries').symlink_to(runtime/'libraries',target_is_directory=True);(root/'mods').mkdir()
  for prefix in ['architectury-','cloth-config-','resourcefullib-']:
   jar=next(a.upstream.glob(prefix+'*.jar'));shutil.copyfile(jar,root/'mods'/jar.name)
  shutil.copyfile(build/'fixture.jar',root/'mods/fixture.jar');(root/'eula.txt').write_text('eula=true\n')
  (root/'server.properties').write_text('level-name=trim-test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":{}}\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25592\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
  for baseline in [True,False]:
   for old,new in NAMES.items():
    (root/'mods'/old).unlink(missing_ok=True)
    shutil.copyfile(a.upstream/old if baseline else REPO/'mods'/new,root/'mods'/(old if baseline else new))
   if not baseline:shutil.copyfile(REPO/'server/mods'/MOD,root/'mods'/MOD)
   log=build/('smoke-original.log' if baseline else 'smoke-trimmed.log')
   with log.open('w') as out:
    result=subprocess.run(['java','-XX:ActiveProcessorCount=2','-Xms512M','-Xmx4G','-Dtrim.baseline='+str(baseline).lower(),'@libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt','nogui'],cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=420)
   text=log.read_text();marker='TRIM_SMOKE_PASS baseline='+str(baseline).lower()
   if result.returncode or marker not in text or 'TRIM_SMOKE_FAIL' in text:raise SystemExit('FAIL '+str(log)+'\n'+text[-10000:])
   print(marker)
if __name__=='__main__':main()
