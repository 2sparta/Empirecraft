#!/usr/bin/env python3
"""Compile civic reader/layout changes against the pinned previously shipped Bridge and native client API."""
import hashlib,os,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build/overlay';SDK=REPO/'sails/build'
BASE=ROOT/'native-inputs/empirecraft-towny-journeymap-1.9.0.jar';BASE_SHA='51eb39ea21840e415d0e03a434371b465deee2d498801237ea23c8b042aa80c7'
sys.path.insert(0,str(REPO/'sails'));from build import jar

def run(*args):subprocess.run(list(map(str,args)),check=True)
def main():
 assert hashlib.sha256(BASE.read_bytes()).hexdigest()==BASE_SHA
 java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';BUILD.mkdir(parents=True,exist_ok=True);libs=sorted((REPO/'server/libraries').rglob('*.jar'));asm=[p for p in libs if '/org/ow2/asm/' in str(p)];remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]))
 named=BUILD/'base-named.jar';named.unlink(missing_ok=True);run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',BASE,named,SDK/'named-srg.tiny','srg','named',SDK/'runtime-srg.jar','--ignoreConflicts')
 classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir();cp=[named,REPO/'mods/chatplus-forge-2.8.1-empirecraft.2.jar',SDK/'runtime-named.jar',SDK/'client-named.jar',SDK/'annotations.jar',*libs]
 sources=[ROOT/'src/main/java/ua/empirecraft/townymap/client'/n for n in ['MarketScreen.java','CivicReadState.java','mixin/InteractiveTabMixin.java']]
 args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sources];(BUILD/'javac.args').write_text('\n'.join('"'+str(a)+'"' for a in args));run(java,'com.sun.tools.javac.Main','@'+str(BUILD/'javac.args'))
 patch=BUILD/'patch-named.jar';jar(patch,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')});srg=BUILD/'patch-srg.jar';srg.unlink(missing_ok=True)
 run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',patch,srg,SDK/'named-srg.tiny','named','srg',SDK/'runtime-named.jar',SDK/'client-named.jar',named,'--ignoreConflicts')
 with zipfile.ZipFile(BASE) as z:entries={n:z.read(n) for n in z.namelist()}
 with zipfile.ZipFile(srg) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
 for p in (ROOT/'src/main/resources').rglob('*'):
  if p.is_file():entries[p.relative_to(ROOT/'src/main/resources').as_posix()]=p.read_bytes()
 output=ROOT/'build/libs/empirecraft-towny-journeymap-1.10.0.jar';jar(output,entries)
 for side in ['mods','server/mods']:
  folder=REPO/side;target=folder/output.name;shutil.copyfile(output,target);target.with_suffix('.jar.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
  for old in folder.glob('empirecraft-towny-journeymap-1.9.0.jar*'):old.unlink()
 print('BRIDGE_CIVIC_BUILD_PASS',hashlib.sha256(output.read_bytes()).hexdigest())
if __name__=='__main__':main()
