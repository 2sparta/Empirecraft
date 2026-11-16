"""Build small, pinned overlays against the actual Forge/MC SDK; preserve previous forks."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
OUT={
 'handson_delight-forge-1.20.1-1.5.0.jar':'handson_delight-forge-1.20.1-1.5.0-empirecraft.1.jar',
 'farmersrespite-1.20.1-2.1.2-empirecraft-recipes.1.jar':'farmersrespite-1.20.1-2.1.2-empirecraft-recipes.2.jar',
 'legendarysurvivaloverhaul-1.20.1-2.4.7-empirecraft.1.jar':'legendarysurvivaloverhaul-1.20.1-2.4.7-empirecraft.2.jar',
 'TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.1.jar':'TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.2.jar',
 'diegesis-forge-1.20.1-1.0.0-beta.11.jar':'diegesis-forge-1.20.1-1.0.0-beta.11-empirecraft.1.jar'}

def digest(body):return hashlib.sha256(body).hexdigest()
def writejar(path,files):
 path.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(path,'w') as z:
  for name,body in sorted(files.items()):
   info=zipfile.ZipInfo(name,(2026,11,16,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,body)
def unpack(path):
 with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def main():
 p=argparse.ArgumentParser();p.add_argument('--reference-repo',type=Path,default=Path('/workspace/Empirecraft'));p.add_argument('--sdk',type=Path,default=Path('/workspace/work/tfc/toolchain'));p.add_argument('--jdk',type=Path,default=Path('/workspace/work/tfc/jdk'));p.add_argument('--install',action='store_true');a=p.parse_args()
 pin=json.loads((ROOT/'inputs.json').read_text());b=ROOT/'build';b.mkdir(exist_ok=True)
 jars={}
 for name,sha in pin['jars'].items():
  raw=subprocess.check_output(['git','-C',str(REPO),'show',pin['source_ref']+':mods/'+name]);assert digest(raw)==sha
  f=b/name;f.write_bytes(raw);jars[name]=f
 diege='diegesis-forge-1.20.1-1.0.0-beta.11.jar';jars[diege]=REPO/'tea-integration/upstream'/diege;assert digest(jars[diege].read_bytes())==pin['diegesis_sha256']
 libs=list((a.reference_repo/'server/libraries').rglob('*.jar'));asm=list((a.reference_repo/'server/libraries/org/ow2/asm').rglob('*.jar'))
 java=str(a.jdk/'bin/java');javac=str(a.jdk/'bin/javac');remapper=[a.sdk/'tiny-remapper.jar',a.sdk/'mapping-io.jar',*asm]
 named=b/'named';named.mkdir(exist_ok=True)
 cp=[a.sdk/'runtime-named.jar',a.sdk/'client-named.jar',a.sdk/'client-original.jar',a.sdk/'annotations.jar',*libs]
 # Only the imports needed by the changed Java classes, never placeholder APIs.
 deps=[jars[n] for n in jars if not n.startswith('TerraFirmaCraft')]+[next((REPO/'mods').glob('FarmersDelight-*.jar'))]
 for f in deps:
  target=named/f.name;sha=digest(f.read_bytes());marker=target.with_suffix('.source.sha256')
  if not target.exists() or not marker.exists() or marker.read_text()!=sha:
   target.unlink(missing_ok=True)
   subprocess.run([java,'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(f),str(target),str(a.sdk/'named-srg.tiny'),'srg','named',str(a.sdk/'runtime-srg.jar'),*map(str,libs),'--ignoreConflicts'],check=True,stdout=subprocess.DEVNULL)
   marker.write_text(sha)
  cp.append(target)
 classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 subprocess.run([javac,'--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,(ROOT/'src').rglob('*.java'))],check=True)
 writejar(b/'hooks-named.jar',{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')})
 srg=b/'hooks-srg.jar';srg.unlink(missing_ok=True)
 subprocess.run([java,'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(b/'hooks-named.jar'),str(srg),str(a.sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True,stdout=subprocess.DEVNULL)
 hooks=unpack(srg);tools=b/'tools';tools.mkdir(exist_ok=True)
 subprocess.run([javac,'-cp',os.pathsep.join(map(str,asm)),'-d',str(tools),str(ROOT/'tools/Patch.java')],check=True)
 targets={
  'farmersrespite':['umpaz/farmersrespite/common/block/entity/KettleBlockEntity.class','umpaz/farmersrespite/common/block/entity/KettleBlockEntity$2.class'],
  'legendarysurvivaloverhaul':['sfiomn/legendarysurvivaloverhaul/common/temperature/BlockModifier.class','sfiomn/legendarysurvivaloverhaul/util/internal/TemperatureUtilInternal.class'],
  'TerraFirmaCraft':['net/dries007/tfc/common/items/TFCItems.class']}
 report={}
 for name,f in jars.items():
  files=unpack(f);changed=[]
  for prefix,paths in targets.items():
   if name.startswith(prefix):
    for path in paths:
     temp=b/'patch-input.class';out=b/'patch-output.class';temp.write_bytes(files[path])
     r=subprocess.run([java,'-cp',os.pathsep.join(map(str,[tools,*asm])),'Patch',str(temp),str(out)],check=True,capture_output=True,text=True);print(r.stdout.strip(),flush=True)
     files[path]=out.read_bytes();changed.append(path)
  namespace='com/pockyl/handson_delight/' if name.startswith('handson') else 'com/pockyl/diegesis/' if name.startswith('diegesis') else 'ua/empirecraft/kitchen/heat/HeatRules' if name.startswith('legendary') else 'ua/empirecraft/kitchen/egg/EggHooks' if name.startswith('Terra') else None
  if namespace:
   for path,body in hooks.items():
    if path.startswith(namespace) and path.endswith('.class'):files[path]=body;changed.append(path)
  if name.startswith('Terra'):
   for path,body in list(files.items()):
    if path.endswith('.json') and b'tfc:food/cooked_egg' in body:files[path]=body.replace(b'tfc:food/cooked_egg',b'naturalist:cooked_egg')
   files['META-INF/mods.toml']+=b'\n[[dependencies.tfc]]\nmodId="naturalist"\nmandatory=true\nversionRange="[0,)"\nordering="AFTER"\nside="BOTH"\n'
  metadata=files['META-INF/mods.toml'].decode()
  if name.startswith('handson'):metadata=metadata.replace('version="1.5.0"','version="1.5.0-empirecraft.1"')
  if name.startswith('diegesis'):metadata=metadata.replace('version="1.0.0-beta.11"','version="1.0.0-beta.11-empirecraft.1"')
  if name.startswith('legendary'):metadata=metadata.replace('1.20.1-2.4.7-empirecraft.1','1.20.1-2.4.7-empirecraft.2')
  files['META-INF/mods.toml']=metadata.encode();files['EMPIRECRAFT-KITCHEN.txt']=b'Empirecraft kitchen fixes. Exact inputs, source overlays, tests and licenses: kitchen-fixes/ in https://github.com/2sparta/Empirecraft\n'
  output=ROOT/'dist'/OUT[name];writejar(output,files);sha=digest(output.read_bytes());output.with_suffix('.jar.sha256').write_text(sha+'  '+output.name+'\n')
  report[output.name]={'source':name,'sha256':sha,'changed_classes':sorted(set(changed)),'size':output.stat().st_size}
  if a.install:
   prefix=next(k for k in ['handson_delight-','farmersrespite-','legendarysurvivaloverhaul-','TerraFirmaCraft-','diegesis-'] if name.startswith(k))
   for side in ['mods','server/mods']:
    folder=REPO/side;backup=REPO/'disabled-mods/kitchen-2026-11-16'/side;backup.mkdir(parents=True,exist_ok=True)
    for old in folder.glob(prefix+'*.jar'):
     if old.name==output.name:continue
     shutil.move(old,backup/old.name)
     check=old.with_suffix('.jar.sha256')
     if check.exists():shutil.move(check,backup/check.name)
    shutil.copy2(output,folder/output.name);shutil.copy2(output.with_suffix('.jar.sha256'),folder/(output.name+'.sha256'))
 (ROOT/'audit/build.json').write_text(json.dumps(report,indent=2)+'\n');print('KITCHEN_BUILD_PASS',len(report),flush=True)
if __name__=='__main__':main()
