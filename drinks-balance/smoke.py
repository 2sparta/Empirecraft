#!/usr/bin/env python3
"""Three real Forge boots: legacy save -> patched save -> unloaded/offline 30-day aging."""
import argparse,os,shutil,subprocess,zipfile,uuid
from pathlib import Path
from build import ROOT,REPO,INPUTS,pack
def run(args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def main():
 p=argparse.ArgumentParser();p.add_argument('--runtime',type=Path,required=True);p.add_argument('--toolchain',type=Path,required=True);p.add_argument('--upstream',type=Path,required=True);a=p.parse_args()
 rt=a.runtime.resolve();tool=a.toolchain.resolve();build=ROOT/'build';classes=build/'test-classes';shutil.rmtree(classes,ignore_errors=True)
 deps=[tool/'runtime-named.jar',tool/'client-named.jar',*rt.rglob('*.jar')]
 run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',os.pathsep.join(map(str,deps)),'-d',classes,ROOT/'tests/DrinksSmoke.java'])
 pack(build/'fixture-named.jar',{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')})
 cp=[tool/'tiny-remapper.jar',tool/'mapping-io.jar',*rt.glob('libraries/org/ow2/asm/**/*.jar')]
 target=build/'fixture-srg.jar';target.unlink(missing_ok=True)
 run(['java','-cp',os.pathsep.join(map(str,cp)),'dev.architectury.tinyremapper.Main',build/'fixture-named.jar',target,tool/'named-srg.tiny','named','srg',tool/'runtime-named.jar',tool/'client-named.jar','--ignoreConflicts'])
 with zipfile.ZipFile(target) as z:files={n:z.read(n) for n in z.namelist()}
 files['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_drinks_test"\nversion="1.0.0"\n';pack(build/'fixture.jar',files)
 root=build/('smoke-runtime-'+uuid.uuid4().hex[:8]);(root/'mods').mkdir(parents=True)
 shutil.copytree(rt/'libraries',root/'libraries',copy_function=os.symlink)
 # Use genuine Forge classes from the installer, avoiding the server's Mohist universal/loader replacements.
 if (rt/'forge-installer.jar').exists():
  with zipfile.ZipFile(rt/'forge-installer.jar') as installer:
   for name in installer.namelist():
    if name.startswith('maven/') and name.endswith('.jar'):
     target=root/'libraries'/name.removeprefix('maven/');target.parent.mkdir(parents=True,exist_ok=True);target.unlink(missing_ok=True);target.write_bytes(installer.read(name))
 args=(rt/'libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt').read_text()
 # Use the installed Mohist distribution's equivalents if its bundled versions differ.
 import re
 for rel in set(re.findall(r'libraries/[^\s:]+\.jar',args)):
  if (root/rel).exists():continue
  stem=Path(rel).name.rsplit('-',1)[0]
  options=[f for f in (rt/'libraries').rglob(stem+'-*.jar') if re.fullmatch(re.escape(stem)+r'-(?:mohist-)?\d.*\.jar',f.name)]
  if len(options)!=1:raise RuntimeError('Missing runtime input '+rel)
  replacement=options[0].relative_to(rt).as_posix();args=args.replace(rel,replacement).replace(Path(rel).name,options[0].name)
 (root/'launch.args').write_text(args)
 (root/'eula.txt').write_text('eula=true\n');(root/'server.properties').write_text('level-name=drinks-test\nlevel-type=minecraft:flat\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25594\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
 for pattern in ['architectury-*.jar','cloth-config-*.jar','letsdo-API-*.jar','FarmersDelight-*.jar']:
  f=next(a.upstream.glob(pattern));shutil.copyfile(f,root/'mods'/f.name)
 shutil.copyfile(build/'fixture.jar',root/'mods/fixture.jar')
 for phase in [0,1,2]:
  for old,_,new in INPUTS.values():
   for name in [old,new]:(root/'mods'/name).unlink(missing_ok=True)
   source=a.upstream/old if phase==0 else REPO/'mods'/new;shutil.copyfile(source,root/'mods'/source.name)
  for old,_,new in INPUTS.values():
   assert (root/'mods'/(old if phase==0 else new)).exists()
   assert not (root/'mods'/(new if phase==0 else old)).exists()
  log=build/('smoke-'+str(phase)+'.log')
  with log.open('w') as out:
   result=subprocess.run(['java','-XX:ActiveProcessorCount=2','-Xms256M','-Xmx1200M','-Ddrinks.phase='+str(phase),'@launch.args','nogui'],cwd=root,stdout=out,stderr=subprocess.STDOUT,timeout=180)
  txt=log.read_text();resultfile=root/('test-result-'+str(phase)+'.txt')
  if resultfile.exists():txt+='\n'+resultfile.read_text()
  marker='DRINKS_SMOKE_PASS phase='+str(phase)
  if result.returncode or marker not in txt or 'DRINKS_SMOKE_FAIL' in txt:raise SystemExit('FAIL code='+str(result.returncode)+' '+str(log)+'\n'+txt[-10000:])
  print(marker,flush=True)
if __name__=='__main__':main()
