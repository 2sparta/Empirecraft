#!/usr/bin/env python3
"""Compile the fleet GUI against the exact installed bridge and genuine Minecraft client APIs."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
VERSION='1.11.2';BASE_SHA='85a1513ce93bd6074b68e5b45edcfde36f02171cf866a4bf924ea3ac804e8743'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def readjar(p):
 with zipfile.ZipFile(p) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def jar(p,files):
 p.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(p,'w') as z:
  for name,raw in sorted(files.items()):
   i=zipfile.ZipInfo(name,(2026,11,14,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,raw)
 p.with_suffix(p.suffix+'.sha256').write_text(sha(p)+'  '+p.name+'\n')
def main():
 p=argparse.ArgumentParser();p.add_argument('--reference-repo',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);a=p.parse_args();ref=a.reference_repo.resolve();sdk=a.sdk.resolve();jdk=a.jdk.resolve();build=ROOT/'build';build.mkdir(exist_ok=True);logs=build/'logs';logs.mkdir(exist_ok=True)
 def run(command,name):
  r=subprocess.run(list(map(str,command)),capture_output=True,text=True);(logs/(name+'.log')).write_text(r.stdout+r.stderr)
  if r.returncode:print((r.stdout+r.stderr)[-6000:]);raise SystemExit(name+' failed')
  print(name+'_PASS',flush=True);return r.stdout
 base=ref/'mods/empirecraft-towny-journeymap-1.11.1.jar';assert sha(base)==BASE_SHA,(base,sha(base));libs=sorted((ref/'server/libraries').rglob('*.jar'));remapper=[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*sorted((ref/'server/libraries/org/ow2/asm').rglob('*.jar'))]
 named=build/'bridge-base-named.jar'
 if not named.exists():run([jdk/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',base,named,sdk/'named-srg.tiny','srg','named',sdk/'runtime-srg.jar',*libs,'--ignoreConflicts'],'base-remap')
 journey=next((ref/'mods').glob('journeymap-forge-*.jar'));nested=build/'nested';nested.mkdir(exist_ok=True)
 for name,raw in readjar(journey).items():
  if name.endswith('.jar'):(nested/Path(name).name).write_bytes(raw)
 cp=[named,sdk/'runtime-named.jar',sdk/'client-named.jar',sdk/'client-original.jar',journey,*sorted(nested.glob('*.jar')),*libs];classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir();src=REPO/'bridge/src/main/java/ua/empirecraft/townymap/client'
 sources=[src/(n+'.java') for n in ['FleetScreen','FleetUiHooks','FleetStyle','FleetLayout']]
 run([jdk/'bin/javac','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sources],'gui-compile')
 gui=build/'gui-named.jar';srg=build/'gui-srg.jar';jar(gui,{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')});srg.unlink(missing_ok=True)
 run([jdk/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',gui,srg,sdk/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts'],'gui-remap')
 files=readjar(base);patch=readjar(srg)
 # Remove only inner classes belonging to the replaced GUI classes before overlaying.
 changed=['ua/empirecraft/townymap/client/'+n for n in ['FleetScreen','FleetUiHooks','FleetStyle','FleetLayout']]
 files={n:raw for n,raw in files.items() if not any(n==c+'.class' or n.startswith(c+'$') for c in changed)};files.update(patch)
 for path in ['META-INF/mods.toml','assets/empirecraft_townymap/locales/en.json','assets/empirecraft_townymap/locales/ru.json']:files[path]=(REPO/'bridge/src/main/resources'/path).read_bytes()
 output=ROOT/'dist'/('empirecraft-towny-journeymap-'+VERSION+'.jar');jar(output,files)
 unchanged=[n for n in readjar(base) if n.endswith('.class') and not any(n==c+'.class' or n.startswith(c+'$') for c in changed)];assert all(files[n]==readjar(base)[n] for n in unchanged)
 tests=build/'tests';tests.mkdir(exist_ok=True);testcp=os.pathsep.join(map(str,[classes,*cp]));testsrc=[REPO/'naval/tests/FleetClientTests.java',REPO/'menu-update/tests/MenuClientTests.java',ROOT/'tests/FleetLayoutTest.java',ROOT/'tests/FleetPackagedTest.java']
 run([jdk/'bin/javac','--release','17','-proc:none','-encoding','UTF-8','-cp',testcp,'-d',tests,*testsrc],'tests-compile')
 markers=[]
 for suite in ['FleetClientTests','MenuClientTests','FleetLayoutTest','FleetPackagedTest']:
  out=run([jdk/'bin/java','-ea','-cp',os.pathsep.join(map(str,[tests,classes,REPO/'bridge/src/main/resources',*cp])),suite,output,(REPO if suite=='FleetPackagedTest' else ROOT/'audit/layout.json')],suite);markers.extend(l for l in out.splitlines() if '_PASS' in l)
 report=dict(status='passed',version=VERSION,sha256=sha(output),base_sha256=BASE_SHA,unchanged_classes=len(unchanged),markers=markers,graphical_client_tested=False,production_world_opened=False)
 (ROOT/'audit/build.json').write_text(json.dumps(report,indent=2)+'\n');(ROOT/'audit/packaged.json').write_text(json.dumps(dict(status='passed',sha256=sha(output),markers=markers),indent=2)+'\n');print('FLEET_MENU_BUILD_PASS',sha(output))
if __name__=='__main__':main()
