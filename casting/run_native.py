#!/usr/bin/env python3
"""Run the real casting adapter only in an explicitly marked, generated test world."""
import argparse,hashlib,json,os,re,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def main():
 p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--reference-repo',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);p.add_argument('--fixture',type=Path,required=True);a=p.parse_args();ref=a.reference_repo.resolve();sdk=a.sdk.resolve();jdk=a.jdk.resolve();fixture=a.fixture.resolve();assert (fixture/'TFC-INTEGRATION-TEST-ONLY').is_file();work=a.work.resolve();work.mkdir(exist_ok=True);runtime=work/'runtime';assert not runtime.exists();shutil.copytree(fixture,runtime,symlinks=True)
 for path in runtime.rglob('*'):
  if path.is_symlink() and not path.exists():
   archived=next((ref/'disabled-mods').rglob(path.name),None)
   if archived is None:raise RuntimeError('Missing fixture input: '+path.name)
   path.unlink();path.symlink_to(archived.resolve())
 for old in (runtime/'mods').glob('*.jar'):
  if 'probe' in old.name or old.name=='empirecraft-tfc-test.jar' or old.name.startswith(('empirecraft-casting-','sulfur_gunpowder-')):old.unlink()

 for old in (runtime/'mods').glob('empirecraft-ore-retrogen-*.jar'):old.unlink()
 shutil.copyfile(ROOT/'dist/empirecraft-ore-retrogen-1.1.1.jar',runtime/'mods/empirecraft-ore-retrogen-1.1.1.jar')
 artifact=ROOT/'dist/empirecraft-casting-1.3.0.jar';artifact_sha=hashlib.sha256(artifact.read_bytes()).hexdigest();probe_sha=hashlib.sha256((ROOT/'tests/CastingProbe.java').read_bytes()).hexdigest();metallurgy_probe_sha=hashlib.sha256((ROOT/'tests/MetallurgyProbe.java').read_bytes()).hexdigest();crucible_probe_sha=hashlib.sha256((ROOT/'tests/CrucibleProbe.java').read_bytes()).hexdigest();food_probe_sha=hashlib.sha256((ROOT/'tests/FoodProbe.java').read_bytes()).hexdigest();thermal_probe_sha=hashlib.sha256((ROOT/'tests/ThermalProbe.java').read_bytes()).hexdigest();shutil.copyfile(artifact,runtime/'mods'/artifact.name)
 script='kubejs/server_scripts/zzzz_empirecraft_smithing.js';target=runtime/script
 if target.exists() or target.is_symlink():target.unlink()
 shutil.copyfile(ROOT.parent/'server'/script,target)
 food_script='kubejs/server_scripts/zzzzzzzzzzz_empirecraft_food.js';food_target=runtime/food_script
 if food_target.exists() or food_target.is_symlink():food_target.unlink()
 shutil.copyfile(ROOT.parent/food_script,food_target)
 libs=sorted((ref/'server/libraries').rglob('*.jar'));asm=sorted((ref/'server/libraries/org/ow2/asm').rglob('*.jar'));cp=[ROOT/'build/classes',ROOT/'build/compile-access.jar',sdk/'runtime-named.jar',sdk/'client-named.jar',*sorted((ROOT/'build/named').glob('*.jar')),*libs,*sorted((ref/'mods').glob('*.jar'))];classes=work/'classes';classes.mkdir()
 r=subprocess.run([str(jdk/'bin/javac'),'--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,sorted((ROOT/'tests').glob('*Probe.java')))],capture_output=True,text=True)
 if r.returncode:print(r.stderr[-6000:]);raise SystemExit(1)
 named=work/'probe-named.jar';srg=work/'probe-srg.jar'
 with zipfile.ZipFile(named,'w') as z:
  for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 remapper=[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm]
 subprocess.run([str(jdk/'bin/java'),'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(named),str(srg),str(sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True,stdout=subprocess.DEVNULL)
 with zipfile.ZipFile(srg,'a') as z:z.writestr('pack.mcmeta','{"pack":{"pack_format":15,"description":"Casting probe, test only"}}');z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0-1.0"\n[[mods]]\nmodId="empirecraft_casting_probe"\nversion="1.0.0"\n')
 shutil.copyfile(srg,runtime/'mods/empirecraft-casting-probe-TEST-ONLY.jar')
 with (work/'native.log').open('w') as log:subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=600)
 raw=(work/'native.log').read_text(errors='replace');markers=['CASTING_NATIVE_'+re.sub(r'\x1b\[[0-9;]*m','',l).split('CASTING_NATIVE_',1)[1].strip() for l in raw.splitlines() if 'CASTING_NATIVE_' in l];print('\n'.join(markers))
 if 'CASTING_NATIVE_FAIL' in raw or not any('CASTING_NATIVE_PASS' in l for l in markers):
  errors=[l for l in raw.splitlines() if 'AssertionError' in l or 'Exception' in l];print('\n'.join(errors[-12:]));raise SystemExit(1)
 (ROOT/'audit/native.json').write_text(json.dumps(dict(status='passed',sha256=artifact_sha,probe_sha256=probe_sha,metallurgy_probe_sha256=metallurgy_probe_sha,thermal_probe_sha256=thermal_probe_sha,food_probe_sha256=food_probe_sha,crucible_probe_sha256=crucible_probe_sha,ore_retrogen_sha256=hashlib.sha256((runtime/'mods/empirecraft-ore-retrogen-1.1.1.jar').read_bytes()).hexdigest(),food_script_sha256=hashlib.sha256((runtime/food_script).read_bytes()).hexdigest(),smithing_script_sha256=hashlib.sha256((runtime/script).read_bytes()).hexdigest(),markers=markers,production_world_opened=False,graphical_client_tested=False),indent=2)+'\n')
if __name__=='__main__':main()
