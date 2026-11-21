"""Full installed Forge/Mohist/Towny pack, disposable sentinel-protected save only."""
from pathlib import Path
import os,subprocess,zipfile,json,shutil
from build_water import jar
R=Path(__file__).resolve().parent;REPO=R.parent;REF=Path('/workspace/Empirecraft');b=R/'build/population-native';b.mkdir(parents=True,exist_ok=True)
libs=sorted((REF/'server/libraries').rglob('*.jar'));libs.sort(key=lambda p:0 if p.name.startswith('forge-1.20.1-') and p.name.endswith('-server.jar') else 1)
cp=[REPO/'town-menu-art/build/refresh/bukkit-api.jar',R/'dist/TownyRecruits.jar',*libs,*sorted((REPO/'mods').glob('*.jar'))]
c=b/'classes';c.mkdir(exist_ok=True)
subprocess.run(list(map(str,['java','com.sun.tools.javac.Main','-source','17','-target','17','--system','/workspace/work/tfc/jdk','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',c,R/'tests/native/PopulationProbe.java'])),check=True)
f={p.relative_to(c).as_posix():p.read_bytes() for p in c.rglob('*.class')};f['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_population_probe"\nversion="1"\n';probe=b/'population-probe-TEST-ONLY.jar';jar(probe,f)
runtime=Path('/workspace/work/knockedback-native/runtime');assert (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file();assert 'level-name=test' in (runtime/'server.properties').read_text();assert not (runtime/'world').exists()
for p in (runtime/'mods').glob('*.jar'):
 if p.is_symlink() or 'probe' in p.name.lower():p.unlink()
for p in (REPO/'server/mods').glob('*.jar'):
 if p.name.startswith(('vs_sails-','empirecraft-handbook-','TownyRecruits','TerraFirmaCraft')):continue
 (runtime/'mods'/p.name).symlink_to(p)
with zipfile.ZipFile(R/'dist/TownyRecruits.jar') as z:combined={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
combined.update({n:v for n,v in f.items() if n.endswith('.class')});combined['META-INF/mods.toml']+=b'\n[[mods]]\nmodId="empirecraft_population_probe"\nversion="1"\n';probe=b/'TownyRecruits-probe-TEST-ONLY.jar';jar(probe,combined)
for p in [probe,R/'dist/vs_sails-1.20.1-0.3.2-empirecraft.19.4-forge.jar',R/'dist/empirecraft-handbook-1.0.6.jar',R/'dist/TerraFirmaCraft-Forge-1.20.1-3.2.30-empirecraft.4.jar']:
 if p.parent==R/'dist':
  target=b/p.name;shutil.copy2(p,target);p=target
 (runtime/'mods'/p.name).symlink_to(p)
plugin=runtime/'plugins/empirecraft-frontier-1.0.0.jar'
if plugin.is_symlink():plugin.unlink()
snapshot=b/plugin.name;shutil.copy2(R/'dist/empirecraft-frontier-1.0.0.jar',snapshot);plugin.symlink_to(snapshot)
with (b/'native.log').open('w') as log:result=subprocess.run(['/usr/bin/java','@launch.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=420)
text=(b/'native.log').read_text(errors='replace');markers=[s for s in text.splitlines() if 'POPULATION_NATIVE_' in s];print('\n'.join(markers));assert result.returncode==0 and 'POPULATION_NATIVE_PASS' in text and 'POPULATION_NATIVE_FAIL' not in text,b/'native.log'
(R/'population-native.json').write_text(json.dumps(dict(status='passed',markers=markers,production_world_opened=False),indent=2)+'\n')
