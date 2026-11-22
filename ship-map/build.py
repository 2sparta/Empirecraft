"""Build the optical ship map on the exact installed bridge, using real Java/Forge/VS/JourneyMap APIs."""
from pathlib import Path
import os,subprocess,zipfile,io,hashlib,json,shutil,textwrap
R=Path(__file__).resolve().parent;REPO=R.parent;B=R/'build';B.mkdir(exist_ok=True)
SDK=Path('/workspace/work/tfc/toolchain');JDK=Path('/workspace/work/tfc/jdk/bin');REF=Path('/workspace/Empirecraft')
BASE='eb0cd1e13';OLD='empirecraft-towny-journeymap-1.14.0.jar';NAME='empirecraft-towny-journeymap-1.14.1.jar'
JAVA=['ShipViewDistance','mixin/ShipClientSettings','ShipIcon','ShipIconAtlas','ShipSight','ShipObservation','SpyglassService','StrategyNetwork','client/ShipMapLayer','client/ShipIconCache','client/ShipReconClient','client/ShipReconKeys','client/StrategyMapUi','client/mixin/ShipSpyglassKeys','client/mixin/ShipSpyglassMouse']
SOURCE=REPO/'bridge/src/main/java/ua/empirecraft/townymap'
libs=sorted((REF/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)]
remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
def run(*args):
 result=subprocess.run(list(map(str,args)))
 if result.returncode:raise SystemExit('Build command failed: '+str(args[0]))
def read(path):
 with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def archive(path,files):
 path.parent.mkdir(parents=True,exist_ok=True);tmp=path.with_name(path.name+'.tmp')
 with zipfile.ZipFile(tmp,'w') as z:
  for n,data in sorted(files.items()):
   info=zipfile.ZipInfo(n,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
 tmp.replace(path);path.with_suffix('.jar.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
def cp():
 old=REPO/'ship-physics/water-fix/build'
 return [SDK/'annotations.jar',SDK/'runtime-named.jar',SDK/'client-named.jar',B/'bridge-named.jar',old/'valkyrienskies-120-2.4.11-empirecraft.1-named.jar',*sorted(B.glob('journeymap-api*.jar')),*sorted((old/'nested').glob('*.jar')),*libs,*sorted((REPO/'mods').glob('*.jar'))]
def main():
 raw=subprocess.check_output(['git','show',BASE+':mods/'+OLD],cwd=REPO);assert hashlib.sha256(raw).hexdigest()=='7b7e92e4c8c313c22416ab361d9f2679b487b582b5fe3f624268ed72332bbe67';(B/'base.jar').write_bytes(raw)
 if not (B/'bridge-named.jar').exists():run(JDK/'java','-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',B/'base.jar',B/'bridge-named.jar',SDK/'named-srg.tiny','srg','named',SDK/'runtime-srg.jar','--ignoreConflicts')
 for n,data in read(REPO/'mods/journeymap-forge-1.20.1-6.0.7.jar').items():
  if n.endswith('.jar') and 'api' in n.lower():(B/Path(n).name).write_bytes(data)
 sources=[SOURCE/(n+'.java') for n in JAVA];classes=B/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir();classpath=cp()
 run(JDK/'javac','-J-Xmx512m','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,classpath)),'-d',classes,*sources)
 archive(B/'named.jar',{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
 srg=B/'srg.jar';srg.unlink(missing_ok=True);run(JDK/'java','-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',B/'named.jar',srg,SDK/'named-srg.tiny','named','srg',*classpath,'--ignoreConflicts')
 files=read(B/'base.jar');prefixes=['ua/empirecraft/townymap/'+n for n in JAVA]
 changed=lambda n:any(n==p+'.class' or n.startswith(p+'$') for p in prefixes)
 files={n:v for n,v in files.items() if not changed(n)};files.update({n:v for n,v in read(srg).items() if n.endswith('.class')})
 resources=REPO/'bridge/src/main/resources';metadata=resources/'META-INF/mods.toml';files['META-INF/mods.toml']=metadata.read_bytes()
 paths=[metadata]
 for folder in ['textures/map','lang']:
  for p in (resources/'assets/empirecraft_townymap'/folder).glob('*'):
   if folder=='textures/map' and not p.name.startswith('ship_'):continue
   n=p.relative_to(resources).as_posix();data=p.read_bytes()
   if folder=='lang' and n in files:
    merged=json.loads(files[n]);merged.update(json.loads(data));data=(json.dumps(merged,ensure_ascii=False,indent=2)+'\n').encode()
   files[n]=data;paths.append(p)
 config='empirecraft-ship-map.mixins.json';files[config]=(json.dumps(dict(required=True,minVersion='0.8',package='ua.empirecraft.townymap.client.mixin',compatibilityLevel='JAVA_17',client=['ShipSpyglassKeys','ShipSpyglassMouse'],injectors={'defaultRequire':1}))+ '\n').encode()
 common='empirecraft-ship-map-common.mixins.json';files[common]=(json.dumps(dict(required=True,minVersion='0.8',package='ua.empirecraft.townymap.mixin',compatibilityLevel='JAVA_17',mixins=['ShipClientSettings'],injectors={'defaultRequire':1}))+'\n').encode()
 manifest=files['META-INF/MANIFEST.MF'].decode().replace('\r\n ','').replace('\n ','');lines=manifest.splitlines();out=[]
 for line in lines:
  if line.startswith('MixinConfigs:'):line=line+','+config+','+common
  if line.startswith('Implementation-Version:'):line='Implementation-Version: 1.14.1'
  if not line:continue
  while len(line.encode())>70:out.append(line[:70]);line=' '+line[70:]
  out.append(line)
 if 'MixinConfigs:' not in manifest:out.append('MixinConfigs: '+config+','+common)
 files['META-INF/MANIFEST.MF']=('\r\n'.join(out)+'\r\n\r\n').encode()
 archive(R/'dist'/NAME,files)
 preserved=[n for n in read(B/'base.jar') if n.endswith('.class') and not changed(n)];assert all(files[n]==read(B/'base.jar')[n] for n in preserved)
 (R/'build.json').write_text(json.dumps(dict(base_commit=subprocess.check_output(['git','rev-parse',BASE],cwd=REPO).decode().strip(),base_sha256=hashlib.sha256(raw).hexdigest(),sha256=hashlib.sha256((R/'dist'/NAME).read_bytes()).hexdigest(),preserved_classes=len(preserved),sources_sha256={p.relative_to(REPO).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources+paths}),indent=2)+'\n')
 print('SHIP_MAP_BUILD_PASS')
if __name__=='__main__':main()
