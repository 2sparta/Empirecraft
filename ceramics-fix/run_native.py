#!/usr/bin/env python3
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def main():
 p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--fix',action='store_true');p.add_argument('--reference-repo',type=Path,default=ROOT.parent);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--fixture',type=Path,required=True);a=p.parse_args();REPO=a.reference_repo.resolve();SDK=a.sdk.resolve();JDK=a.java_home.resolve();work=a.work.resolve();work.mkdir(parents=True,exist_ok=True);runtime=work/'runtime';assert not runtime.exists()
 fixture=a.fixture.resolve();assert (fixture/'TFC-INTEGRATION-TEST-ONLY').is_file()
 shutil.copytree(fixture,runtime,symlinks=True)
 # Historical scratch fixtures link versioned repository JARs; restore archived inputs if retired.
 for path in runtime.rglob('*'):
  if path.is_symlink() and not path.exists():
   archived=next((REPO/'disabled-mods').rglob(path.name),None)
   if archived is None:raise RuntimeError('Missing fixture input: '+path.name)
   path.unlink();path.symlink_to(archived.resolve())
 for old in (runtime/'mods').glob('*.jar'):
  if 'probe' in old.name or old.name=='empirecraft-tfc-test.jar':old.unlink()
 for old in (runtime/'mods').glob('empirecraft-handbook-*.jar'):old.unlink()
 shutil.copyfile(next((REPO/'mods').glob('empirecraft-handbook-*.jar')),runtime/'mods'/next((REPO/'mods').glob('empirecraft-handbook-*.jar')).name)
 for old in (runtime/'mods').glob('empirecraft-layered-armor-*.jar'):old.unlink()
 armor=next((REPO/'mods').glob('empirecraft-layered-armor-*.jar'));shutil.copyfile(armor,runtime/'mods'/armor.name)
 for rel in ['zzzzzzzzz_empirecraft_paper_books.js']:
  shutil.copyfile(REPO/'server/kubejs/server_scripts'/rel,runtime/'kubejs/server_scripts'/rel)
 if a.fix:
  shutil.copyfile(ROOT.parent/'server/kubejs/server_scripts/zzzzzzzzzz_empirecraft_sandpaper.js',runtime/'kubejs/server_scripts/zzzzzzzzzz_empirecraft_sandpaper.js')
 libs=sorted((REPO/'server/libraries').rglob('*.jar'));cp=[REPO/'tfc-integration/build/compile-access.jar',SDK/'runtime-named.jar',*sorted((REPO/'tfc-integration/build/named').glob('*.jar')),*libs,*sorted((REPO/'mods').glob('*.jar')),REPO/'tfc-integration/build/nested/Ponder-Forge-1.20.1-1.0.91.jar'];classes=work/'classes';classes.mkdir(exist_ok=True)
 compile=subprocess.run([str(JDK/'bin/javac'),'--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(ROOT/'tests/CeramicsProbe.java')],capture_output=True,text=True)
 if compile.returncode:print(compile.stderr[-5000:]);raise SystemExit(1)
 named=work/'probe-named.jar';srg=work/'probe-srg.jar'
 with zipfile.ZipFile(named,'w') as z:
  for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 remapper=[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))]
 subprocess.run([str(JDK/'bin/java'),'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(named),str(srg),str(SDK/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True,stdout=subprocess.DEVNULL)
 with zipfile.ZipFile(srg,'a') as z:z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_ceramics_probe"\nversion="1.0.0"\n')
 shutil.copyfile(srg,runtime/'mods/empirecraft-ceramics-probe-TEST-ONLY.jar')
 with (work/'native.log').open('w') as log:r=subprocess.run(['/usr/bin/java',*(['-Dempirecraft.ceramics.fix=true'] if a.fix else []),'@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
 raw=(work/'native.log').read_text(errors='replace');markers=[l for l in raw.splitlines() if 'CERAMICS_' in l];print('\n'.join(markers))
 if 'CERAMICS_NATIVE_FAIL' in raw or not any('CERAMICS_NATIVE_PASS' in l for l in markers):print(raw[-7000:]);raise SystemExit(1)
 (ROOT/'audit'/('native-fixed.json' if a.fix else 'native-before.json')).write_text(json.dumps({'status':'passed','markers':markers,'live_world_opened':False,'native':'Mohist/Forge 47.4.23 Java 21','script_sha256':hashlib.sha256((ROOT.parent/'server/kubejs/server_scripts/zzzzzzzzzz_empirecraft_sandpaper.js').read_bytes()).hexdigest() if a.fix else None},indent=2)+'\n')
if __name__=='__main__':main()
