#!/usr/bin/env python3
"""Build menu languages against installed Bukkit/Forge APIs and the existing mapping SDK."""
import argparse,hashlib,json,os,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build';DIST=ROOT/'dist'
BASES={'empirecraft-feudal-1.19.2.jar':'52e09241114a9fa600a276d9e476f9d9e44af4ee45c75026fe8e710e65b3d56a',
       'empirecraft-towny-journeymap-1.11.0.jar':'97e37142e80892a772bce4b0e2b328247fd29bb42c0bbc66d7c438b642e8764d'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def readjar(p):
 with zipfile.ZipFile(p) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def jar(p,files):
 p.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(p,'w') as z:
  for name,raw in sorted(files.items()):
   info=zipfile.ZipInfo(name,(2026,11,13,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
 p.with_suffix('.jar.sha256').write_text(sha(p)+'  '+p.name+'\n')
def resources(p):return {f.relative_to(p).as_posix():f.read_bytes() for f in p.rglob('*') if f.is_file()}
def base(name):
 candidates=[REPO/'server/plugins'/name,REPO/'mods'/name,*sorted((REPO/'disabled-mods').rglob(name))]
 return next(p for p in candidates if p.is_file() and sha(p)==BASES[name])
def main():
 p=argparse.ArgumentParser();p.add_argument('--jdk17',type=Path,default=REPO.parent/'work/tfc/jdk');p.add_argument('--sdk',type=Path,default=REPO.parent/'work/tfc/toolchain');p.add_argument('--java21',default='java');p.add_argument('--resume-client',action='store_true');a=p.parse_args()
 BUILD.mkdir(exist_ok=True);DIST.mkdir(exist_ok=True);logs=BUILD/'logs';logs.mkdir(exist_ok=True)
 def run(command,name):
  result=subprocess.run(list(map(str,command)),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
  (logs/(name+'.log')).write_text(result.stdout)
  if result.returncode:print(result.stdout[-7000:]);raise SystemExit(name+' failed')
  print(name+'_PASS',flush=True);return result.stdout
 libs=sorted((REPO/'server/libraries').rglob('*.jar'));forge=next(p for p in libs if p.name=='forge-1.20.1-47.4.23-universal.jar')
 api=BUILD/'bukkit-api.jar';jar(api,{n:v for n,v in readjar(forge).items() if n.startswith('org/bukkit/') and not n.startswith('org/bukkit/craftbukkit/') and n.endswith('.class')})
 pluginbase=base('empirecraft-feudal-1.19.2.jar');bridgebase=base('empirecraft-towny-journeymap-1.11.0.jar')
 cp=os.pathsep.join(map(str,[api,pluginbase,*sorted((REPO/'server/plugins').glob('*.jar')),*libs]))
 classes=BUILD/'classes';tests=BUILD/'tests'
 suites=('FeudalTests CurrencyTests RealmCommerceTests GoldInventoryTests ItemPlanningTests ProvisionTests PoliticsTests PoliticsDebugTests PoliticalRegressionTests PoliticsProvisionTests ChatTitleTests CurrencyMarketTests CurrencyScreenTests CurrencyExchangeTests CommandLanguageTests PhysicalGoldTests TradeTownyTests KingdomTests ProvisionClaimTests CityMarketTests MarketExtensionTests MarketMailTests MarketNoticeTests MarketFishingTests ContractTests FleetTests ShipRegistrationTests RecruitMarketTests RecruitTownyRuntimeTests RuntimeCompatibilityTests DiscordTests LocalizationTests').split()
 fingerprint={p.relative_to(REPO).as_posix():sha(p) for folder in ['feudal/src/main/java','feudal/src/main/resources','feudal/src/test/java'] for p in sorted((REPO/folder).rglob('*')) if p.is_file()}
 checkpoint=BUILD/'backend-checkpoint.json';plugin=DIST/'empirecraft-feudal-1.19.3.jar'
 if a.resume_client:
  cached=json.loads(checkpoint.read_text());assert cached['fingerprint']==fingerprint and cached['plugin_sha256']==sha(plugin)
  scenarios=cached['scenarios'];assert set(scenarios)==set(suites);print('BACKEND_CHECKPOINT_PASS',flush=True)
 else:
  for d in [classes,tests]:
   if d.exists():shutil.rmtree(d)
   d.mkdir()
  compiler=[a.java21,'com.sun.tools.javac.Main','-source','17','-target','17','--system',a.jdk17,'-proc:none','-encoding','UTF-8']
  run([*compiler,'-cp',cp,'-d',classes,*sorted((REPO/'feudal/src/main/java').rglob('*.java'))],'feudal-compile')
  run([*compiler,'-cp',str(classes)+os.pathsep+cp,'-d',tests,*sorted((REPO/'feudal/src/test/java').rglob('*.java'))],'tests-compile')
  scenarios={}
  for suite in suites:
   out=run([a.java21,'-ea','-cp',os.pathsep.join(map(str,[classes,tests,REPO/'feudal/src/main/resources']))+os.pathsep+cp,'ua.empirecraft.feudal.'+suite],suite)
   scenarios[suite]=sum(line.startswith('✓ ') for line in out.splitlines())
  files={n:v for n,v in readjar(pluginbase).items() if not n.startswith('ua/empirecraft/feudal/') and n!='plugin.yml' and not n.startswith('empirecraft-feudal/')}
  files.update(resources(classes));files.update(resources(REPO/'feudal/src/main/resources'));plugin=DIST/'empirecraft-feudal-1.19.3.jar';jar(plugin,files)
  checkpoint.write_text(json.dumps({'fingerprint':fingerprint,'scenarios':scenarios,'plugin_sha256':sha(plugin)},indent=2)+'\n')
 # Compile only the three changed GUI classes. All other bridge classes stay byte-identical.
 sdk=a.sdk;jdk=a.jdk17;remapper=[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))]
 bridgeNamed=BUILD/'bridge-base-named.jar'
 if not bridgeNamed.exists():run([jdk/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',bridgebase,bridgeNamed,sdk/'named-srg.tiny','srg','named',sdk/'runtime-srg.jar',*libs,'--ignoreConflicts'],'bridge-base-remap')
 gui=BUILD/'gui';gui.mkdir(exist_ok=True);guisrc=REPO/'bridge/src/main/java/ua/empirecraft/townymap/client'
 journey=next((REPO/'mods').glob('journeymap-forge-*.jar'))
 nested=BUILD/'nested';nested.mkdir(exist_ok=True)
 for name,raw in readjar(journey).items():
  if name.endswith('.jar'):(nested/Path(name).name).write_bytes(raw)
 clientcp=os.pathsep.join(map(str,[bridgeNamed,sdk/'runtime-named.jar',sdk/'client-named.jar',sdk/'client-original.jar',journey,*sorted(nested.glob('*.jar')),*libs]))
 run([jdk/'bin/javac','--release','17','-proc:none','-encoding','UTF-8','-cp',clientcp,'-d',gui,*[guisrc/n for n in ['FleetScreen.java','FleetUiHooks.java','ClientText.java']]],'bridge-compile')
 named=BUILD/'gui-named.jar';srg=BUILD/'gui-srg.jar';jar(named,resources(gui));srg.unlink(missing_ok=True)
 run([jdk/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',named,srg,sdk/'named-srg.tiny','named','srg',sdk/'runtime-named.jar',sdk/'client-named.jar',sdk/'client-original.jar',bridgeNamed,journey,*sorted(nested.glob('*.jar')),*libs,'--ignoreConflicts'],'bridge-remap')
 files=readjar(bridgebase);patch=readjar(srg);files.update(patch)
 for name in ['META-INF/mods.toml','assets/empirecraft_townymap/locales/en.json','assets/empirecraft_townymap/locales/ru.json']:files[name]=(REPO/'bridge/src/main/resources'/name).read_bytes()
 bridge=DIST/'empirecraft-towny-journeymap-1.11.1.jar';jar(bridge,files)
 unchanged=[n for n,v in readjar(bridgebase).items() if n.endswith('.class') and n not in patch];assert all(files[n]==readjar(bridgebase)[n] for n in unchanged)
 run([jdk/'bin/javac','--release','17','-proc:none','-cp',str(gui)+os.pathsep+clientcp,'-d',tests,REPO/'naval/tests/FleetClientTests.java',ROOT/'tests/MenuClientTests.java'],'client-tests-compile')
 testcp=os.pathsep.join(map(str,[tests,gui,REPO/'bridge/src/main/resources']))+os.pathsep+clientcp
 client=run([jdk/'bin/java','-ea','-cp',testcp,'FleetClientTests',bridge],'FleetClientTests')
 locale=run([jdk/'bin/java','-ea','-cp',testcp,'MenuClientTests',bridge],'MenuClientTests')
 report={'status':'passed','backend_suites':len(suites),'labeled_backend_scenarios':sum(scenarios.values()),'scenarios':scenarios,'client_markers':[l for l in (client+'\n'+locale).splitlines() if '_PASS' in l],
         'sha256':{p.name:sha(p) for p in [plugin,bridge]},'base_sha256':BASES,'unchanged_bridge_classes':len(unchanged),'graphical_client_tested':False,'production_world_opened':False,
         'bukkit_api':'Unmodified API class subset extracted from installed Forge universal JAR','forge_sha256':sha(forge)}
 (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/verification.json').write_text(json.dumps(report,indent=2)+'\n');subprocess.run([sys.executable,ROOT/'tests/check_packaged.py','--java21',a.java21],check=True);print('MENU_BUILD_PASS',sum(scenarios.values()))
if __name__=='__main__':main()
