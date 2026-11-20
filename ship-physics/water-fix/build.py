"""Pinned overlays: preserve native voxel types, remove duplicate water-cell mass and fluid scan restarts."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent;REPO=R.parents[1];SDK=Path('/workspace/work/tfc/toolchain');JDK=Path('/workspace/work/tfc/jdk/bin');REF=Path('/workspace/Empirecraft')
PIN={'empirecraft-ship-physics-1.2.2.jar':'6ffb4825e245a581f61f5248cdd80580343161df441622cea63fe45b28e312c6'}
def read(p):
 with zipfile.ZipFile(p) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def jar(p,files):
 p.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(p,'w') as z:
  for n,v in sorted(files.items()):
   i=zipfile.ZipInfo(n,(2026,11,20,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,v)
 p.with_suffix('.jar.sha256').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
def main():
 b=R/'build';b.mkdir(parents=True,exist_ok=True)
 pin=json.loads((R/'inputs.json').read_text());bases={}
 for n,h in pin.items():
  p=R/'inputs'/n;assert hashlib.sha256(p.read_bytes()).hexdigest()==h,n;bases[n]=p
 libs=sorted((REF/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)];cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',*libs]
 def run(*cmd):subprocess.run(list(map(str,cmd)),check=True)
 classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 run(JDK/'javac','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((R/'src').rglob('*.java')))
 jar(b/'named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
 out=b/'srg.jar';out.unlink(missing_ok=True)
 run(JDK/'java','-cp',os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm])),'dev.architectury.tinyremapper.Main',b/'named.jar',out,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts')
 tools=b/'tools';tools.mkdir(exist_ok=True);run(JDK/'javac','--release','17','-cp',os.pathsep.join(map(str,asm)),'-d',tools,R/'PatchWater.java')
 helpers=read(out);report={}
 for name,base in bases.items():
  files=read(base);before=dict(files);physics=name.startswith('empirecraft-ship-physics')
  stems=['ua/empirecraft/shipphysics/BlockProperties','ua/empirecraft/shipphysics/MassSync'] if physics else ['com/quintonc/vs_sails/compat/RiggingEvents']
  for stem in stems:
   path=stem+'.class';(b/'in.class').write_bytes(files[path]);run(JDK/'java','-cp',os.pathsep.join(map(str,[tools,*asm])),'PatchWater',b/'in.class',b/'out.class');files[path]=(b/'out.class').read_bytes()
  for n,v in helpers.items():
   if n.endswith('.class') and (n.startswith('ua/') if physics else n.startswith('com/')):files[n]=v
  old,new=('1.2.2','1.2.3') if physics else ('0.3.2-empirecraft.19.2','0.3.2-empirecraft.19.3')
  assert old.encode() in files['META-INF/mods.toml'];files['META-INF/mods.toml']=files['META-INF/mods.toml'].replace(old.encode(),new.encode())
  if physics:files['ua/empirecraft/shipphysics/ShipPhysics.class']=files['ua/empirecraft/shipphysics/ShipPhysics.class'].replace(b'1.2.2',b'1.2.3')
  target=R/'dist'/name.replace(old,new);jar(target,files)
  report[target.name]=dict(sha256=hashlib.sha256(target.read_bytes()).hexdigest(),base_sha256=pin[name],changed_entries=[n for n in before if before[n]!=files.get(n)],unchanged_entries=sum(files.get(n)==v for n,v in before.items()))
 (R/'build.json').write_text(json.dumps(report,indent=2)+'\n');print('SHIP_WATER_PATCH_BUILD_PASS',json.dumps(report))
if __name__=='__main__':main()
