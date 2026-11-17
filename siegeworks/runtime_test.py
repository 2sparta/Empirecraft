"""Run the full installed modpack on a fresh marked Mohist fixture, never a user world."""
import hashlib,json,os,shutil,subprocess,time,zipfile
from pathlib import Path
from build import ROOT,REPO,SDK,JDK,REF,run,archive,sha
work=Path('/workspace/work/siegeworks-native-1');runtime=work/'runtime';fixture=Path('/workspace/work/tyw-runtime-1/runtime')
work.mkdir(exist_ok=True);assert (fixture/'TFC-INTEGRATION-TEST-ONLY').is_file()
if not runtime.exists():shutil.copytree(fixture,runtime,symlinks=True,ignore=shutil.ignore_patterns('test','logs','crash-reports'))
assert (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file() and 'level-name=test' in (runtime/'server.properties').read_text()
if (runtime/'test').exists():(runtime/'test').rename(work/('previous-test-'+str(time.time_ns())))
for f in runtime.rglob('*'):
 if f.is_symlink() and not f.exists():
  if f.parent==runtime/'mods':f.unlink();continue
  replacement=next((REF/'disabled-mods').rglob(f.name),None);assert replacement,f;f.unlink();f.symlink_to(replacement)
for f in (runtime/'mods').glob('*.jar'):f.unlink()
for f in (REPO/'server/mods').glob('*.jar'):(runtime/'mods'/f.name).symlink_to(f)
for root in ['server/kubejs','server/config']:
 for f in (REPO/root).rglob('*'):
  if f.is_file():
   target=runtime/Path(root).name/f.relative_to(REPO/root)
   if target.is_symlink():target.unlink()
   target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,target)
libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'));remapper=[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]
cp=[ROOT/'build/named.jar',Path('/workspace/work/flux-continuous/casting/build/compile-access.jar'),SDK/'runtime-named.jar',SDK/'client-named.jar',*list((ROOT/'build/named').glob('*.jar')),*libs,*list((REPO/'mods').glob('*.jar'))]
classes=work/'classes';classes.mkdir(exist_ok=True)
run([JDK/'bin/javac','--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',classes,ROOT/'Probe.java'],'probe-compile')
archive(work/'named.jar',{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')});srg=work/'srg.jar';srg.unlink(missing_ok=True)
run([JDK/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',work/'named.jar',srg,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts'],'probe-reobf')
with zipfile.ZipFile(srg,'a') as z:z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_siege_probe"\nversion="1.0.0"\n');z.writestr('pack.mcmeta','{"pack":{"pack_format":15,"description":"TEST ONLY"}}')
shutil.copy2(srg,runtime/'mods/siege-probe-TEST-ONLY.jar')
inputs={f.name:sha(f) for prefix in ['siegeworks-','axiomata-','recruits-','empirecraft-claim-guard-','TownyRecruits','blockandboard-'] for f in (REPO/'server/mods').glob(prefix+'*.jar')};probe_sha=sha(ROOT/'Probe.java')
with (work/'native.log').open('w') as log:result=subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
assert result.returncode==0,'Server exited with code '+str(result.returncode)
log=(work/'native.log').read_text(errors='replace');assert 'SIEGE_NATIVE_PASS' in log and 'SIEGE_NATIVE_FAIL' not in log and 'Encountered an unexpected exception' not in log and 'Exception in server tick loop' not in log,'See '+str(work/'native.log')
marker=next(line for line in log.splitlines() if 'SIEGE_NATIVE_PASS' in line);assert all(sha(REPO/'server/mods'/name)==digest for name,digest in inputs.items());assert sha(ROOT/'Probe.java')==probe_sha
(ROOT/'audit/runtime.json').write_text(json.dumps(dict(status='passed',marker=marker,mod_sha256=sha(next((REPO/'mods').glob('siegeworks-*.jar'))),probe_sha256=probe_sha,dependency_sha256=inputs),indent=2)+'\n');print(marker)
