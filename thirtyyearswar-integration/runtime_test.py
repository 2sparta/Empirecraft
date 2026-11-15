"""Boot only an explicitly marked disposable Mohist fixture; never production worlds."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--fixture',type=Path,required=True);p.add_argument('--reuse',action='store_true');p.add_argument('--reference-repo',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);p.add_argument('--casting-sdk',type=Path,required=True);a=p.parse_args();assert (a.fixture/'TFC-INTEGRATION-TEST-ONLY').is_file();a.work.mkdir(exist_ok=True);runtime=a.work/'runtime';
if runtime.exists():assert a.reuse and (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file()
else:shutil.copytree(a.fixture,runtime,symlinks=True)
ref=a.reference_repo;sdk=a.sdk;jdk=a.jdk
for old in (runtime/'mods').glob('*.jar'):
 if 'probe' in old.name or old.name=='empirecraft-tfc-test.jar':old.unlink()
for old in runtime.rglob('*'):
 if old.is_symlink() and not old.exists():
  archived=next((ref/'disabled-mods').rglob(old.name),None);assert archived,old;old.unlink();old.symlink_to(archived)
# All generated files are copied, including the last recipe pass and TFC item heats.
generated=json.loads((ROOT/'audit/generated.json').read_text())
for name in generated['files']:
 dest=runtime/'kubejs'/name
 if dest.is_symlink():dest.unlink()
 dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(REPO/'kubejs'/name,dest)
shutil.copy2(next((REPO/'mods').glob('epic-knights-thirty*jar')),runtime/'mods')
classes=a.work/'classes';classes.mkdir(exist_ok=True);libs=list((ref/'server/libraries').rglob('*.jar'));asm=list((ref/'server/libraries/org/ow2/asm').rglob('*.jar'));native=a.casting_sdk
cp=[native/'compile-access.jar',sdk/'runtime-named.jar',sdk/'client-named.jar',*list((native/'named').glob('*.jar')),*libs,*list((REPO/'mods').glob('*.jar'))]
r=subprocess.run([str(jdk/'bin/javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(ROOT/'Probe.java')],text=True,capture_output=True)
if r.returncode:print(r.stderr);raise SystemExit(r.returncode)
named=a.work/'probe-named.jar';srg=a.work/'probe-srg.jar';srg.unlink(missing_ok=True);recipe_bytes=(ROOT/'recipes.json').read_bytes()
with zipfile.ZipFile(named,'w') as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
subprocess.run([str(jdk/'bin/java'),'-cp',os.pathsep.join(map(str,[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm])),'dev.architectury.tinyremapper.Main',str(named),str(srg),str(sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True,stdout=subprocess.DEVNULL)
with zipfile.ZipFile(srg,'a') as z:
 z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_tyw_probe"\nversion="1.0.0"\n');z.writestr('pack.mcmeta','{"pack":{"pack_format":15,"description":"TYW test only"}}');z.writestr('tyw-recipes.json',recipe_bytes)
shutil.copy2(srg,runtime/'mods/empirecraft-tyw-probe-TEST-ONLY.jar')
with (a.work/'native.log').open('w') as log:subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
text=(a.work/'native.log').read_text(errors='replace');lines=[l for l in text.splitlines() if 'TYW_NATIVE_' in l];print('\n'.join(lines));passed='TYW_NATIVE_PASS' in text and 'TYW_NATIVE_FAIL' not in text
if not passed:
 print('\n'.join(l for l in text.splitlines() if any(w in l for w in ['Exception','Error','ERROR','missing','required','Loading'] ))[-10000:]);raise SystemExit(1)
(ROOT/'audit/runtime.json').write_text(json.dumps(dict(status='passed',probe_sha256=hashlib.sha256((ROOT/'Probe.java').read_bytes()).hexdigest(),generated_sha256=hashlib.sha256(recipe_bytes).hexdigest(),markers=lines,production_world_opened=False,graphical_client_tested=False),indent=2)+'\n')
