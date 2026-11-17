"""Exercise real Forge/Mohist FML startup with a deliberately broken file-version view."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
from build import ROOT,REPO,REF,JDK,INPUT,NAME,archive,sha
work=Path('/workspace/work/tfc-protocol-native');runtime=work/'runtime';fixture=Path('/workspace/work/tyw-runtime-1/runtime');assert (fixture/'TFC-INTEGRATION-TEST-ONLY').is_file();work.mkdir(exist_ok=True)
if not runtime.exists():shutil.copytree(fixture,runtime,symlinks=True,ignore=shutil.ignore_patterns('test','logs','crash-reports'))
assert (runtime/'TFC-INTEGRATION-TEST-ONLY').is_file()
libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'));classes=work/'classes';classes.mkdir(exist_ok=True)
subprocess.run([str(JDK/'bin/javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,libs)),'-d',str(classes),str(ROOT/'Probe.java')],check=True)
probe={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};probe['META-INF/mods.toml']=b'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="CC0"\n[[mods]]\nmodId="empirecraft_tfc_protocol_probe"\nversion="1"\n';archive(work/'probe.jar',probe)
loader=next((REF/'server/libraries/net/minecraftforge/fmlloader').rglob('*.jar'));key='net/minecraftforge/fml/loading/moddiscovery/ModFileInfo.class'
with zipfile.ZipFile(loader) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
(work/'in.class').write_bytes(files[key]);subprocess.run([str(JDK/'bin/java'),'-cp',os.pathsep.join(map(str,[ROOT/'build',*asm])),'Patch','simulate',str(work/'in.class'),str(work/'out.class')],check=True);files[key]=(work/'out.class').read_bytes();simulated=work/loader.name;archive(simulated,files)
args=(fixture/'launch.args').read_text().replace(str(loader),str(simulated)).replace('-Xmx6G','-Xmx4G');assert str(simulated) in args
source=subprocess.check_output(['git','show','63c57975:server/mods/'+INPUT],cwd=REPO);(work/INPUT).write_bytes(source)
reports=[]
for mode,target in [('control',work/INPUT),('patched',ROOT/'dist'/NAME)]:
 for f in (runtime/'mods').glob('*.jar'):f.unlink()
 for f in (REPO/'server/mods').glob('*.jar'):
  if not f.name.startswith('TerraFirmaCraft-'):(runtime/'mods'/f.name).symlink_to(f)
 (runtime/'mods'/target.name).symlink_to(target);shutil.copy2(work/'probe.jar',runtime/'mods/tfc-protocol-probe-TEST-ONLY.jar')
 (runtime/'protocol.args').write_text('"-Dtfc.protocol.test.mode='+mode+'"\n'+args)
 with (work/(mode+'.log')).open('w') as log:r=subprocess.run(['/usr/bin/java','@protocol.args'],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,timeout=300)
 text=(work/(mode+'.log')).read_text(errors='replace');assert r.returncode==0 and 'TFC_PROTOCOL_REGRESSION_PASS' in text and 'TFC_PROTOCOL_REGRESSION_FAIL' not in text,'See '+str(work/(mode+'.log'))
 marker=next(l for l in text.splitlines() if 'TFC_PROTOCOL_REGRESSION_PASS' in l);print(marker,flush=True);reports.append(dict(mode=mode,marker=marker))
(ROOT/'audit/runtime.json').write_text(json.dumps(dict(status='passed',sha256=sha((ROOT/'dist'/NAME).read_bytes()),probe_sha256=sha((ROOT/'Probe.java').read_bytes()),test_only_loader_fault='ModFileInfo.versionString returns 0.0NONE; actual ModInfo still declares 3.2.30',real_fml_common_setup=True,no_world_loaded=True,strict_versions_verified_both_directions=True,cases=reports),indent=2)+'\n')
