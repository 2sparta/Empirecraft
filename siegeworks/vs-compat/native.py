"""Real Forge/VS/Siegeworks/Mohist/Towny; only the existing sentinel-protected test fixture."""
from pathlib import Path
import os,subprocess,shutil,zipfile,json,hashlib,re
from build import R,REPO,SDK,JDK,REF,b,libs,remapper,run,read,jar,NAME
old=REPO/'ship-physics/water-fix/build'
cp=[b/'named.jar',SDK/'runtime-named.jar',SDK/'client-named.jar',b/'siegeworks-named.jar',b/'axiomata-named.jar',old/'valkyrienskies-120-2.4.11-empirecraft.1-named.jar',*sorted((old/'nested').glob('*.jar')),*libs,*sorted((REPO/'mods').glob('*.jar'))]
c=b/'probe';c.mkdir(exist_ok=True);run(JDK/'javac','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',c,R/'tests/NativeProbe.java')
jar(b/'probe-named.jar',{p.relative_to(c).as_posix():p.read_bytes() for p in c.rglob('*.class')});out=b/'probe-srg.jar';out.unlink(missing_ok=True);run(JDK/'java','-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',b/'probe-named.jar',out,SDK/'named-srg.tiny','named','srg',*cp,'--ignoreConflicts')
f=read(out);f['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0"\n[[mods]]\nmodId="empirecraft_siege_vs_probe"\nversion="1"\n';probe=b/'siege-vs-probe-TEST-ONLY.jar';jar(probe,f)
runtime=Path('/workspace/work/knockedback-native/runtime');assert (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file();assert 'level-name=test' in (runtime/'server.properties').read_text();assert not (runtime/'world').exists()
if (runtime/'test').exists():shutil.rmtree(runtime/'test')
for p in (runtime/'mods').glob('*.jar'):p.unlink()
for p in (REPO/'server/mods').glob('*.jar'):
 if not p.name.startswith('siegeworks-'):(runtime/'mods'/p.name).symlink_to(p)
snapshot=b/NAME;shutil.copy2(R/'dist'/NAME,snapshot)
for p in [snapshot,probe]:(runtime/'mods'/p.name).symlink_to(p)
for p in (runtime/'plugins').glob('empirecraft-feudal-*.jar'):p.unlink()
current=next((REPO/'server/plugins').glob('empirecraft-feudal-*.jar'));(runtime/'plugins'/current.name).symlink_to(current)
(runtime/'logs').mkdir(exist_ok=True)
with (b/'native.log').open('w') as log:result=subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
t=re.sub(r'\x1b\[[0-9;]*m','',(b/'native.log').read_text(errors='replace'));markers=[l for l in t.splitlines() if 'SIEGE_VS_NATIVE_' in l or 'SIEGE_VS_SWEEPS' in l];print('\n'.join(markers));assert result.returncode==0 and 'SIEGE_VS_NATIVE_PASS' in t and 'SIEGE_VS_NATIVE_FAIL' not in t,b/'native.log'
(R/'native.json').write_text(json.dumps(dict(status='passed',markers=markers,artifact_sha256=hashlib.sha256(snapshot.read_bytes()).hexdigest(),production_world_opened=False),indent=2)+'\n')
