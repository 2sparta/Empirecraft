"""Full Mohist/Forge/VS; refuse every runtime except the sentinel-protected synthetic fixture."""
from build import *
classpath=[B/'named.jar',*cp()];C=B/'native-probe';shutil.rmtree(C,ignore_errors=True);C.mkdir()
run(JDK/'javac','--release','17','-proc:none','-cp',os.pathsep.join(map(str,classpath)),'-d',C,R/'tests/NativeProbe.java')
archive(B/'native-named.jar',{p.relative_to(C).as_posix():p.read_bytes() for p in C.rglob('*.class')});srg=B/'native-srg.jar';srg.unlink(missing_ok=True)
run(JDK/'java','-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',B/'native-named.jar',srg,SDK/'named-srg.tiny','named','srg',*classpath,'--ignoreConflicts')
files=read(srg);files['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0"\n[[mods]]\nmodId="empirecraft_ship_map_probe"\nversion="1"\n';probe=B/'ship-map-probe-TEST-ONLY.jar';archive(probe,files)
runtime=Path('/workspace/work/knockedback-native/runtime');assert (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file();assert 'level-name=test' in (runtime/'server.properties').read_text();assert not (runtime/'world').exists()
if (runtime/'test').exists():shutil.rmtree(runtime/'test')
for p in (runtime/'mods').glob('*.jar'):p.unlink()
for p in (REPO/'server/mods').glob('*.jar'):
 if not p.name.startswith('empirecraft-towny-journeymap-'):(runtime/'mods'/p.name).symlink_to(p)
snapshot=B/NAME;shutil.copy2(R/'dist'/NAME,snapshot)
for p in [snapshot,probe]:(runtime/'mods'/p.name).symlink_to(p)
(runtime/'logs').mkdir(exist_ok=True)
with (B/'native.log').open('w') as log:result=subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
import re
text=re.sub(r'\x1b\[[0-9;]*m','',(B/'native.log').read_text(errors='replace'));markers=[line for line in text.splitlines() if 'SHIP_MAP_NATIVE_' in line];print('\n'.join(markers));assert result.returncode==0 and 'SHIP_MAP_NATIVE_PASS' in text and 'SHIP_MAP_NATIVE_FAIL' not in text
(R/'native.json').write_text(json.dumps(dict(status='passed',artifact_sha256=hashlib.sha256(snapshot.read_bytes()).hexdigest(),markers=markers,production_world_opened=False),indent=2)+'\n')
