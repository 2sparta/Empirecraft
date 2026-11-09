#!/usr/bin/env python3
"""Build the fleet update with genuine installed 1.20.1 APIs and pinned previous client bytecode.

JDK 17+, Python 3. No client API substitutes, production server boot or Gradle download.
Outputs remain under naval/dist/fleet-fixes-1.1; installation is a separate operation.
"""
import argparse,hashlib,json,os,shutil,subprocess,sys,urllib.request,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
BUILD=ROOT/'build/update'
DIST=ROOT/'dist/fleet-fixes-1.1'
BASES={
 'bridge/native-inputs/empirecraft-towny-journeymap-1.10.0.jar':'34bac49b917f64baf3156b8191756597dabd821e38325b8c9db876dd324ecb49',
 'engagement/native-inputs/empirecraft-engagement-1.1.0.jar':'a6419f5505027bcff4623675001ceed1947d491fd5414f4904bed040ac3ddb1f',
}
H2_SHA='b9d8f19358ada82a4f6eb5b174c6cfe320a375b5a9cb5a4fe456d623e6e55497'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def jar(path,files):
 path.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(path,'w') as z:
  for name,raw in sorted(files.items()):
   info=zipfile.ZipInfo(name,(2026,11,8,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
def readjar(path):
 with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def resources(path):return {p.relative_to(path).as_posix():p.read_bytes() for p in path.rglob('*') if p.is_file()}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server-home',type=Path,default=REPO/'server');p.add_argument('--java-home',type=Path);p.add_argument('--runtime-java',help='Optional Java 21+ runtime for hybrid API proxy tests');a=p.parse_args();server=a.server_home.resolve()
 BUILD.mkdir(parents=True,exist_ok=True);DIST.mkdir(parents=True,exist_ok=True)
 home=a.java_home or (Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else None)
 java=str(home/'bin/java') if home else shutil.which('java');javac=str(home/'bin/javac') if home else shutil.which('javac');testjava=a.runtime_java or java
 if not java or not javac:raise SystemExit('Use a full JDK 17+ (--java-home or JAVA_HOME).')
 def run(args,name):
  result=subprocess.run(list(map(str,args)),cwd=REPO,capture_output=True,text=True);(BUILD/(name+'.log')).write_text(result.stdout+result.stderr)
  if result.returncode:print(result.stdout+result.stderr);raise SystemExit('Failed: '+name)
  print(name+': '+(result.stdout.strip().splitlines()[-1] if result.stdout.strip() else 'PASS'),flush=True)
  return result.stdout
 def need(pattern,base=server/'libraries'):
  found=sorted(base.rglob(pattern));assert len(found)==1,(pattern,len(found));return found[0]
 def compile(sources,target,cp,name):
  shutil.rmtree(target,ignore_errors=True);target.mkdir(parents=True)
  args=['--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',target,*sources];argfile=BUILD/(name+'.args');argfile.write_text('\n'.join('"'+str(v).replace('\\','\\\\').replace('"','\\"')+'"' for v in args));run([javac,'@'+str(argfile)],name)
 for path,expected in BASES.items():assert sha(REPO/path)==expected,'Changed native input: '+path
 libs=sorted((server/'libraries').rglob('*.jar'));asm=[f for f in libs if '/org/ow2/asm/' in str(f)];asmcp=os.pathsep.join(map(str,asm));tools=BUILD/'tools'
 h2=REPO/'feudal/lib/h2.jar';h2.parent.mkdir(exist_ok=True)
 if not h2.exists():
  with urllib.request.urlopen('https://repo.maven.apache.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar',timeout=60) as response:raw=response.read()
  assert hashlib.sha256(raw).hexdigest()==H2_SHA;h2.write_bytes(raw)
 assert sha(h2)==H2_SHA,'Changed H2 dependency'
 towny=server/'plugins/Towny-0.101.2.0.jar';assert sha(towny)=='c21dd5c8e63401fe5f14be77a33e96afa727b9c8214a7f85c2c4f67b761645a6'
 api=need('forge-1.20.1-47.4.23-server.jar');universal=need('forge-1.20.1-47.4.23-universal.jar');mc=need('server-1.20.1-20230612.114412-srg.jar')
 cp=os.pathsep.join(map(str,[api,h2,towny,*libs]));classes=BUILD/'feudal-classes';tests=BUILD/'feudal-tests'
 compile(sorted((REPO/'feudal/src/main/java').rglob('*.java')),classes,cp,'feudal-compile')
 compile(sorted((REPO/'feudal/src/test/java').rglob('*.java')),tests,str(classes)+os.pathsep+cp,'feudal-test-compile')
 backend=[]
 for suite in ['FleetTests','ShipRegistrationTests','CommandLanguageTests','CityMarketTests','ContractTests','FeudalTests','ProvisionTests','LocalizationTests']:
  output=run([testjava,'-ea','-cp',os.pathsep.join(map(str,[classes,tests,REPO/'feudal/src/main/resources']))+os.pathsep+cp,'ua.empirecraft.feudal.'+suite],suite);backend.append({'suite':suite,'passed':True})
 # Produce the release with the canonical compiler/API and all 32 backend suites.
 if home:os.environ['JAVA_HOME']=str(home)
 os.environ['FEUDAL_RUNTIME_JAVA']=testjava
 run([sys.executable,REPO/'feudal/build.py','--output-dir',DIST],'feudal-full-backend')
 plugin=DIST/'empirecraft-feudal-1.19.1.jar'
 # Server mappings are sufficient: executable GUI widgets come from the pinned
 # native bridge. New selectors use its public form protocol and real widgets.
 sdk=BUILD/'sdk';sdk.mkdir(exist_ok=True);mapping=sdk/'named-srg.srg';named=sdk/'runtime-named.jar'
 compile([REPO/'sails/tools/OfflineMappings.java',ROOT/'FleetClientOverlay.java'],tools,asmcp+os.pathsep+str(need('srgutils-0.5.4.jar')),'tools')
 with zipfile.ZipFile(need('mcp_config-1.20.1-20230612.114412.zip')) as z:(sdk/'joined.tsrg').write_bytes(z.read('config/joined.tsrg'))
 run([java,'-cp',str(tools)+os.pathsep+str(need('srgutils-0.5.4.jar')),'OfflineMappings',need('server-1.20.1-20230612.114412-mappings.txt'),sdk/'joined.tsrg',mapping],'mappings')
 key=sha(mapping)+''.join(sha(f) for f in [mc,api,universal]);stamp=sdk/'runtime.sha256'
 if not named.exists() or not stamp.exists() or stamp.read_text()!=key:
  raw={}
  for f in [mc,api,universal]:raw.update({n:v for n,v in readjar(f).items() if n.endswith('.class')})
  runtime=sdk/'runtime-srg.jar';jar(runtime,raw);named.unlink(missing_ok=True)
  run([java,'-Xmx2G','-cp',os.pathsep.join(map(str,[runtime,*libs])),'net.md_5.specialsource.SpecialSource','-i',runtime,'-o',named,'-m',mapping,'-r','-l','--quiet'],'runtime-names');stamp.write_text(key)
 nested=BUILD/'nested';nested.mkdir(exist_ok=True)
 modfiles=[need('valkyrienskies-120-2.4.11-empirecraft.1.jar',server/'mods'),need('kotlinforforge-4.12.0-all*.jar',server/'mods'),need('architectury-9.2.14-forge.jar',server/'mods'),need('journeymap-forge-1.20.1-6.0.7.jar',server/'mods')]
 for f in modfiles:
  for n,v in readjar(f).items():
   if n.endswith('.jar'):(nested/Path(n).name).write_bytes(v)
 bridgebase=REPO/next(path for path in BASES if path.startswith('bridge/'))
 commoncp=os.pathsep.join(map(str,[named,bridgebase,*modfiles,*nested.glob('*.jar'),*libs]));helpers=BUILD/'bridge-classes';base=REPO/'bridge/src/main/java/ua/empirecraft/townymap'
 compile([base/'FleetObservationNative.java',*[base/'client'/n for n in ['FleetMarkerBook.java','FleetWaypoints.java','FleetUiHooks.java']]],helpers,commoncp,'bridge-helpers')
 patch=BUILD/'bridge-named.jar';jar(patch,resources(helpers));srg=BUILD/'bridge-srg.jar';srg.unlink(missing_ok=True)
 run([java,'-Xmx1G','-cp',str(patch)+os.pathsep+commoncp,'net.md_5.specialsource.SpecialSource','-i',patch,'-o',srg,'-m',mapping,'-l','--quiet'],'bridge-reobf')
 bridge=DIST/'empirecraft-towny-journeymap-1.11.0.jar'
 run([java,'-cp',str(tools)+os.pathsep+asmcp,'FleetClientOverlay',bridgebase,srg,REPO/'bridge/src/main/resources',bridge],'bridge-overlay')
 clienttests=BUILD/'client-tests';compile([ROOT/'tests/FleetClientTests.java'],clienttests,str(helpers)+os.pathsep+commoncp+os.pathsep+asmcp,'client-tests-compile')
 run([java,'-ea','-cp',str(clienttests)+os.pathsep+str(helpers)+os.pathsep+commoncp+os.pathsep+asmcp,'FleetClientTests',bridge],'client-tests')
 policy=BUILD/'policy';compile([REPO/'engagement/src/main/java/ua/empirecraft/engagement/CombatPolicy.java'],policy,'','policy-compile')
 policytests=BUILD/'policy-tests';compile([REPO/'engagement/src/test/java/ua/empirecraft/engagement/CombatPolicyTest.java'],policytests,str(policy),'policy-tests-compile')
 run([java,'-ea','-cp',str(policytests)+os.pathsep+str(policy),'ua.empirecraft.engagement.CombatPolicyTest'],'policy-tests')
 combatbase=REPO/next(path for path in BASES if path.startswith('engagement/'));files=readjar(combatbase);files.update(resources(policy));files['META-INF/mods.toml']=(REPO/'engagement/src/main/resources/META-INF/mods.toml').read_bytes();combat=DIST/'empirecraft-engagement-1.1.1.jar';jar(combat,files)
 artifacts={}
 for artifact in [plugin,bridge,combat]:
  with zipfile.ZipFile(artifact) as z:assert z.testzip() is None and not any(n.startswith(('net/minecraft/','org/bukkit/')) for n in z.namelist())
  artifacts[artifact.name]=sha(artifact);artifact.with_suffix('.jar.sha256').write_text(sha(artifact)+'  '+artifact.name+'\n')
 report={'artifacts':artifacts,'backend':backend,'input_jars':{str(f):sha(f) for f in [mc,api,universal,towny,h2,*modfiles]},'native_bases':BASES,'graphical_client_tested':False,'production_world_tested':False}
 (DIST/'build-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('FLEET_UPDATE_BUILD_PASS',json.dumps(artifacts))
if __name__=='__main__':main()
