import sys,os,subprocess,shutil,zipfile,json,hashlib
from pathlib import Path
R=Path(__file__).resolve().parent;REPO=R.parent;sys.path.insert(0,str(REPO/'siegeworks'))
from build import SDK,JDK,REF,archive
w=Path('/workspace/work/knockedback-native');runtime=w/'runtime';fixture=Path('/workspace/work/tyw-runtime-1/runtime')
assert (fixture/'TFC-INTEGRATION-TEST-ONLY').is_file()
if not runtime.exists():shutil.copytree(fixture,runtime,symlinks=True,ignore=shutil.ignore_patterns('test','logs','crash-reports','session.lock'))
assert (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file() and 'level-name=test' in (runtime/'server.properties').read_text()
for p in (runtime/'mods').glob('*.jar'):p.unlink()
for p in (REPO/'server/mods').glob('*.jar'):(runtime/'mods'/p.name).symlink_to(p)
for root in ['server/kubejs','server/config']:
 for f in (REPO/root).rglob('*'):
  if f.is_file():
   t=runtime/Path(root).name/f.relative_to(REPO/root)
   if t.is_symlink():t.unlink()
   t.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,t)
libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'))
cp=[Path('/workspace/work/flux-continuous/casting/build/compile-access.jar'),SDK/'runtime-named.jar',SDK/'client-named.jar',R/'build/base-named.jar',R/'build/helpers-named.jar',*libs]
classes=w/'classes';classes.mkdir(exist_ok=True)
subprocess.run([str(JDK/'bin/javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(R/'tests/Probe.java')],check=True)
archive(w/'named.jar',{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')});out=w/'probe.jar';out.unlink(missing_ok=True)
subprocess.run([str(JDK/'bin/java'),'-cp',os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm])),'dev.architectury.tinyremapper.Main',str(w/'named.jar'),str(out),str(SDK/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
with zipfile.ZipFile(out,'a') as z:z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0"\n[[mods]]\nmodId="empirecraft_knocked_probe"\nversion="1.0.0"\n')
shutil.copy2(out,runtime/'mods/knocked-probe-TEST-ONLY.jar')
with (w/'native.log').open('w') as log:result=subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
text=(w/'native.log').read_text(errors='replace');assert result.returncode==0 and 'KNOCKED_NATIVE_PASS' in text and 'KNOCKED_NATIVE_FAIL' not in text,str(w/'native.log')
marker=next(line for line in text.splitlines() if 'KNOCKED_NATIVE_PASS' in line)
(R/'audit/runtime.json').write_text(json.dumps(dict(status='passed',marker=marker,jar_sha256=hashlib.sha256((R/'dist/knockedback-1.6.2-empirecraft.1.jar').read_bytes()).hexdigest()),indent=2)+'\n');print(marker)
