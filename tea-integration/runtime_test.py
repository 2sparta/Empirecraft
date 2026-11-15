"""Boot the complete server mod fixture in a fresh, marked, disposable flat world."""
import argparse, hashlib, json, os, shutil, subprocess, time, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent; REPO=ROOT.parent
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--reuse',action='store_true')
p.add_argument('--reference-repo',type=Path,default=Path('/workspace/Empirecraft'));p.add_argument('--sdk',type=Path,default=Path('/workspace/work/tfc/toolchain'));p.add_argument('--jdk',type=Path,default=Path('/workspace/work/tfc/jdk'));p.add_argument('--named-mods',type=Path,default=Path('/workspace/work/flux-continuous/casting/build/named'));p.add_argument('--fixture',type=Path,default=Path('/workspace/work/tyw-runtime-1/runtime'));p.add_argument('--java',default='/usr/bin/java');a=p.parse_args()
ref=a.reference_repo;sdk=a.sdk;jdk=a.jdk;cache=a.named_mods
fixture=a.fixture;assert (fixture/'TFC-INTEGRATION-TEST-ONLY').is_file()
a.work.mkdir(exist_ok=True);runtime=a.work/'runtime'
if runtime.exists(): assert a.reuse and (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file()
else: shutil.copytree(fixture,runtime,symlinks=True,ignore=shutil.ignore_patterns('test','logs','crash-reports'))
assert 'level-name=test' in (runtime/'server.properties').read_text()
if (runtime/'test').exists(): (runtime/'test').rename(a.work/('previous-test-'+str(time.time_ns())))
for old in runtime.rglob('*'):
    if old.is_symlink() and not old.exists():
        archived=next((ref/'disabled-mods').rglob(old.name),None);assert archived,old;old.unlink();old.symlink_to(archived)
for old in (runtime/'mods').glob('*.jar'):
    if 'probe' in old.name or old.name=='empirecraft-tfc-test.jar' or old.name.startswith(('FarmersDelight-','diegesis-','handson_delight-','empirecraft-casting-')): old.unlink()
for prefix in ['FarmersDelight-','diegesis-','handson_delight-','empirecraft-casting-']:
    source=next((REPO/'server/mods').glob(prefix+'*.jar'));shutil.copy2(source,runtime/'mods'/source.name)
# Copy files, unlinking fixture symlinks before each write to protect reference files.
for source in (REPO/'server/kubejs').rglob('*'):
    if source.is_file():
        target=runtime/'kubejs'/source.relative_to(REPO/'server/kubejs')
        if target.is_symlink(): target.unlink()
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
for name in ['handson_delight.properties','farmersrespite-common.toml']:
    target=runtime/'config'/name
    if target.is_symlink():target.unlink()
    shutil.copy2(REPO/'server/config'/name,target)
libs=list((ref/'server/libraries').rglob('*.jar'));asm=list((ref/'server/libraries/org/ow2/asm').rglob('*.jar'));remapper=[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm]
named=a.work/'named';named.mkdir(exist_ok=True)
for prefix in ['FarmersDelight-','diegesis-','handson_delight-','farmersrespite-','SereneSeasons-']:
    source=next((REPO/'mods').glob(prefix+'*.jar'));target=named/source.name
    source_hash=hashlib.sha256(source.read_bytes()).hexdigest();pin=target.with_suffix('.source.sha256')
    if not target.exists() or not pin.exists() or pin.read_text()!=source_hash:
        target.unlink(missing_ok=True)
        subprocess.run([str(jdk/'bin/java'),'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(source),str(target),str(sdk/'named-srg.tiny'),'srg','named',str(sdk/'runtime-srg.jar'),*map(str,libs),'--ignoreConflicts'],check=True,stdout=subprocess.DEVNULL)
        pin.write_text(source_hash)
cp=[sdk/'runtime-named.jar',sdk/'client-named.jar',*named.glob('*.jar'),*cache.glob('*.jar'),*libs,*list((REPO/'mods').glob('*.jar'))]
classes=a.work/'classes';classes.mkdir(exist_ok=True)
r=subprocess.run([str(jdk/'bin/javac'),'--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(ROOT/'Probe.java')],capture_output=True,text=True)
if r.returncode:print(r.stderr);raise SystemExit(r.returncode)
jar=a.work/'probe-named.jar';srg=a.work/'probe-srg.jar';srg.unlink(missing_ok=True)
with zipfile.ZipFile(jar,'w') as z:
    for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
subprocess.run([str(jdk/'bin/java'),'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(jar),str(srg),str(sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True,stdout=subprocess.DEVNULL)
with zipfile.ZipFile(srg,'a') as z:
    z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_tea_probe"\nversion="1.0.0"\n');z.writestr('pack.mcmeta','{"pack":{"pack_format":15,"description":"TEST ONLY"}}')
shutil.copy2(srg,runtime/'mods/empirecraft-tea-probe-TEST-ONLY.jar')
with (a.work/'native.log').open('w') as log:subprocess.run([a.java,'@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
subprocess.run([os.sys.executable,str(ROOT/'verify_runtime.py'),'--work',str(a.work)],check=True)
