"""Overlay ship-frame compatibility on the exact installed Siegeworks fork; Java 17 + real APIs."""
from pathlib import Path
import os,subprocess,zipfile,json,hashlib,shutil
R=Path(__file__).resolve().parent;REPO=R.parents[1];SDK=Path('/workspace/work/tfc/toolchain');JDK=Path('/workspace/work/tfc/jdk/bin');REF=Path('/workspace/Empirecraft');b=R/'build';b.mkdir(exist_ok=True)
BASE='9661ede246d7f59d1713722bd3913ea8ade2072f';NAME='siegeworks-forge-0.1.0-beta.11-1.20.1-empirecraft.2.jar'
libs=sorted((REF/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
def run(*args):
 result=subprocess.run(list(map(str,args)))
 if result.returncode:raise SystemExit("Build command failed: "+str(args[0]))
def jar(path,files):
 path.parent.mkdir(parents=True,exist_ok=True);temp=path.with_name(path.name+'.tmp')
 with zipfile.ZipFile(temp,'w') as z:
  for n,raw in sorted(files.items()):
   info=zipfile.ZipInfo(n,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
 temp.replace(path);path.with_suffix('.jar.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
def read(path):
 with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def main():
 base=b/'base.jar';raw=subprocess.check_output(['git','show',BASE+':mods/siegeworks-forge-0.1.0-beta.11-1.20.1-empirecraft.1.jar'],cwd=REPO);base.write_bytes(raw)
 mapped=[]
 for name,source in [('siegeworks',base),('axiomata',next((REPO/'mods').glob('axiomata-*.jar')))]:
  target=b/(name+'-named.jar')
  if not target.exists():run(JDK/'java','-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',source,target,SDK/'named-srg.tiny','srg','named',SDK/'runtime-srg.jar','--ignoreConflicts')
  mapped.append(target)
 with zipfile.ZipFile(next((REPO/'mods').glob('valkyrienskies-*.jar'))) as z:
  nested=next(n for n in z.namelist() if n.endswith('mixinextras-forge-0.4.1.jar'))
  import io
  with zipfile.ZipFile(io.BytesIO(z.read(nested))) as api:(b/'mixinextras-api.jar').write_bytes(api.read('META-INF/jars/MixinExtras-0.4.1.jar'))
 old=REPO/'ship-physics/water-fix/build'
 cp=[b/'mixinextras-api.jar',SDK/'annotations.jar',SDK/'runtime-named.jar',SDK/'client-named.jar',*mapped,old/'valkyrienskies-120-2.4.11-empirecraft.1-named.jar',*sorted((old/'nested').glob('*.jar')),*libs,*sorted((REPO/'mods').glob('*.jar'))]
 classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 run(JDK/'javac','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((R/'src').rglob('*.java')))
 jar(b/'named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});out=b/'srg.jar';out.unlink(missing_ok=True)
 run(JDK/'java','-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',b/'named.jar',out,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts')
 files=read(base);patch={n:v for n,v in read(out).items() if n.endswith('.class')};files.update(patch)
 files['empirecraft_siegeworks_vs.mixins.json']=(json.dumps(dict(required=True,minVersion='0.8',package='ua.empirecraft.siegeworks.vs.mixin',compatibilityLevel='JAVA_17',mixins=['CarrierYaw','DeckTick','DeckMovement','ImpactFrames','FragmentFrames','ShipClip','ProjectileClip','LoadedImpactReads','BlastFrames'],injectors={'defaultRequire':1}))+ '\n').encode()
 files['META-INF/MANIFEST.MF']=files['META-INF/MANIFEST.MF'].replace(b'MixinConfigs: ',b'MixinConfigs: empirecraft_siegeworks_vs.mixins.json,')
 files['META-INF/mods.toml']+=b'\n[[dependencies.siegeworks]]\nmodId="valkyrienskies"\nmandatory=true\nversionRange="[2.4.11,)"\nordering="AFTER"\nside="BOTH"\n'
 jar(R/'dist'/NAME,files)
 (R/'build.json').write_text(json.dumps({'base_commit':BASE,'base_sha256':hashlib.sha256(raw).hexdigest(),'sha256':hashlib.sha256((R/'dist'/NAME).read_bytes()).hexdigest(),'classes':sorted(patch),'sources_sha256':{p.relative_to(R).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((R/'src').rglob('*.java'))},'native_protocol_unchanged':True},indent=2)+'\n')
 print('SIEGEWORKS_VS_BUILD_PASS')
if __name__=='__main__':main()
