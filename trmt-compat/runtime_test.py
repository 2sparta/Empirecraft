"""Run the actual Mohist pack twice in a sentinel-only disposable world."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent;REPO=R.parent
runtime=Path('/workspace/work/knockedback-native/runtime')
assert (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file() and 'level-name=test' in (runtime/'server.properties').read_text()
for p in (runtime/'mods').glob('*.jar'):p.unlink()
for p in (REPO/'server/mods').glob('*.jar'):
 if not p.name.startswith('trmt-'):(runtime/'mods'/p.name).symlink_to(p)
jar=R/'dist/trmt-0.5.1b-1.20.1-forge-empirecraft.2.jar'
(runtime/'mods'/jar.name).symlink_to(jar)
probe=R/'build/remembered/empirecraft-trmt-test.jar';shutil.copy2(probe,runtime/'mods/soil-probe-TEST-ONLY.jar')
for p in (runtime/'plugins').glob('*feudal*.jar'):p.unlink()
plugin=REPO/'town-menu-art/dist/empirecraft-feudal-1.20.2.jar';shutil.copy2(plugin,runtime/'plugins'/plugin.name)
for root in ['server/kubejs','server/config']:
 for p in (REPO/root).rglob('*'):
  if p.is_file():
   target=runtime/Path(root).name/p.relative_to(REPO/root)
   if target.is_symlink():target.unlink()
   target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
(runtime/'soil-memory-probe.nbt').unlink(missing_ok=True)
markers=[]
for phase in (1,2):
 log=R/f'build/remembered/native-{phase}.log'
 with log.open('w') as out:result=subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=out,stderr=subprocess.STDOUT,timeout=600)
 text=log.read_text(errors='replace')
 good=f'SOIL_MEMORY_PHASE{phase}_PASS'
 if result.returncode or good not in text or 'SOIL_MEMORY_FAIL' in text:
  print('\n'.join(text.splitlines()[-85:]));raise SystemExit(f'Native phase {phase} failed: {log}')
 markers.extend(line for line in text.splitlines() if 'SOIL_MEMORY_' in line or 'MENU_MOD_ICONS_PASS' in line)
 print(markers[-1],flush=True)
(R/'audit').mkdir(exist_ok=True)
(R/'audit/remembered-runtime.json').write_text(json.dumps(dict(status='passed',markers=markers,jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),plugin_sha256=hashlib.sha256(plugin.read_bytes()).hexdigest(),production_world_opened=False),indent=2)+'\n')
