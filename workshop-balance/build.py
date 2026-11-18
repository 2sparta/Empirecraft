#!/usr/bin/env python3
"""Compile against the installed SRG Forge APIs; no downloads or API stand-ins."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
import resources
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent;BUILD=REPO/'work/workshop-balance';NAME='empirecraft-workshop-1.0.0.jar'
LOCAL_JDK=REPO.parent/'.empirecraft/jdk-17'
JDK=Path(os.environ.get('JAVA_HOME',str(LOCAL_JDK if LOCAL_JDK.exists() else Path(shutil.which('java')).resolve().parent.parent)))
def archive(path,entries):
 path.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(path,'w') as z:
  for name,raw in sorted(entries.items()):
   info=zipfile.ZipInfo(name,(2026,11,18,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
 path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
def classpath():
 jars=list((REPO/'server/libraries').rglob('*.jar'))+list((REPO/'mods').glob('*.jar'))
 return sorted(jars,key=lambda p:(0 if p.name=='forge-1.20.1-47.4.23-server.jar' else 1 if '/org/ow2/asm/' in str(p) else 2,str(p)))
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--java-home',type=Path,default=JDK);args=parser.parse_args()
 classes=BUILD/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir(parents=True)
 cp=os.pathsep.join(map(str,classpath()));sources=sorted((ROOT/'src/main/java').rglob('*.java'))
 command=[str(args.java_home/'bin/javac'),'--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,sources)]
 result=subprocess.run(command,capture_output=True,text=True);(BUILD/'compile.log').write_text(result.stdout+result.stderr)
 if result.returncode:print(result.stdout+result.stderr);raise SystemExit(result.returncode)
 entries={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
 entries.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in (ROOT/'src/main/resources').rglob('*') if p.is_file()})
 entries.update(resources.generate())
 entries['META-INF/LICENSE.txt']=(ROOT/'LICENSE').read_bytes()
 entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_workshop.mixins.json\r\n\r\n'
 archive(ROOT/'dist'/NAME,entries);print('WORKSHOP_BUILD_PASS')
if __name__=='__main__':main()
