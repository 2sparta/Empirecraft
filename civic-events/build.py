#!/usr/bin/env python3
import hashlib,os,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=ROOT/'build';SDK=REPO/'sails/build';VERSION='1.0.0'
sys.path.insert(0,str(REPO/'sails'));from build import jar

def run(*args):subprocess.run(list(map(str,args)),check=True)
def main():
 java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';BUILD.mkdir(exist_ok=True)
 libs=sorted((REPO/'server/libraries').rglob('*.jar'));cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',SDK/'annotations.jar',*libs,*sorted((SDK/'nested').glob('*.jar'))]
 classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 args=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((ROOT/'src/main/java').rglob('*.java'))]
 (BUILD/'javac.args').write_text('\n'.join('"'+str(a)+'"' for a in args));run(java,'com.sun.tools.javac.Main','@'+str(BUILD/'javac.args'))
 named=BUILD/'named.jar';jar(named,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
 asm=[p for p in libs if '/org/ow2/asm/' in str(p)];remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]));srg=BUILD/'srg.jar';srg.unlink(missing_ok=True)
 run(java,'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',named,srg,SDK/'named-srg.tiny','named','srg',SDK/'runtime-named.jar',SDK/'client-named.jar','--ignoreConflicts')
 with zipfile.ZipFile(srg) as z:entries={n:z.read(n) for n in z.namelist() if n.endswith('.class')}
 entries.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in (ROOT/'src/main/resources').rglob('*') if p.is_file()})
 entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft-civic.mixins.json\r\n\r\n'
 out=BUILD/('empirecraft-civic-events-'+VERSION+'.jar');jar(out,entries)
 target=REPO/'server/mods'/out.name;shutil.copyfile(out,target);target.with_suffix('.jar.sha256').write_text(hashlib.sha256(out.read_bytes()).hexdigest()+'  '+out.name+'\n');print('CIVIC_BUILD_PASS',target.name)
if __name__=='__main__':main()
