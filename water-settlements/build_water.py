import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent;REPO=R.parent;SDK=Path('/workspace/work/tfc/toolchain');JDK=Path('/workspace/work/tfc/jdk/bin');REF=Path('/workspace/Empirecraft')
b=R/'build/water';b.mkdir(parents=True,exist_ok=True)
from inputs import materialize
base=materialize('mods/vs_sails-1.20.1-0.3.2-empirecraft.19.3-forge.jar','611a8a438bac6a56cf9771e230a1250f6ca19e95f368f7f8f22395081cbc43a2',b/'base.jar')
assert hashlib.sha256(base.read_bytes()).hexdigest()=='611a8a438bac6a56cf9771e230a1250f6ca19e95f368f7f8f22395081cbc43a2'
libs=sorted((REF/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
old=REPO/'ship-physics/water-fix/build'
cp=[SDK/'annotations.jar',SDK/'runtime-named.jar',SDK/'client-named.jar',old/'vs_sails-1.20.1-0.3.2-empirecraft.19.3-forge-named.jar',old/'valkyrienskies-120-2.4.11-empirecraft.1-named.jar',*sorted((old/'nested').glob('*.jar')),*libs,*sorted((REPO/'mods').glob('*.jar'))]
def run(*cmd):subprocess.run(list(map(str,cmd)),check=True)
def jar(path,files):
 path.parent.mkdir(parents=True,exist_ok=True)
 temporary=path.with_name(path.name+'.tmp')
 with zipfile.ZipFile(temporary,'w') as z:
  for n,v in sorted(files.items()):
   i=zipfile.ZipInfo(n,(2026,11,21,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,v)
 temporary.replace(path)
 path.with_suffix('.jar.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
def main():
 classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 names=['FloodModel','FloodTopology','FloodQueryScope','WaterContact','ShipBlockScan','SailsShipControl','ShipFlooding']
 src=REPO/'sails/src/main/java/com/quintonc/vs_sails/ship'
 run(JDK/'javac','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*[src/(n+'.java') for n in names])
 jar(b/'named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});out=b/'srg.jar';out.unlink(missing_ok=True)
 run(JDK/'java','-cp',os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm])),'dev.architectury.tinyremapper.Main',b/'named.jar',out,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts')
 with zipfile.ZipFile(base) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 with zipfile.ZipFile(out) as z:patch={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
 files.update(patch);files['META-INF/mods.toml']=files['META-INF/mods.toml'].replace(b'0.3.2-empirecraft.19.3',b'0.3.2-empirecraft.19.4')
 jar(R/'dist/vs_sails-1.20.1-0.3.2-empirecraft.19.4-forge.jar',files)
 test=b/'tests';test.mkdir(exist_ok=True)
 run(JDK/'javac','--release','17','-proc:none','-cp',os.pathsep.join(map(str,[classes,*cp])),'-d',test,REPO/'sails/tests/FloodModelTest.java',REPO/'sails/tests/WaterSupportTest.java')
 for name in ['FloodModelTest','WaterSupportTest']:run(JDK/'java','-ea','-cp',os.pathsep.join(map(str,[test,classes,*cp])),name)
 (R/'water-build.json').write_text(json.dumps({'overlaid_classes':sorted(patch),'base_sha256':hashlib.sha256(base.read_bytes()).hexdigest()},indent=2)+'\n')
if __name__=='__main__':main()
