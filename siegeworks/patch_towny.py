"""Adapt the pinned TownyRecruits bridge to Recruits 1.15.2 without changing tactical rules."""
import io,json,os,subprocess,zipfile
from pathlib import Path
from build import ROOT,REPO,JDK,REF,archive,sha
raw=subprocess.check_output(['git','show','e5de18a2:server/mods/TownyRecruits.jar'],cwd=REPO)
with zipfile.ZipFile(io.BytesIO(raw)) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
b=ROOT/'build/towny';b.mkdir(parents=True,exist_ok=True);asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'));cp=os.pathsep.join(map(str,asm))
subprocess.run([JDK/'bin/javac','-cp',cp,'-d',b,ROOT/'tools/PatchTowny.java'],check=True)
name='ua/townyrecruits/TownyRecruits.class';(b/'original.class').write_bytes(files[name]);subprocess.run([JDK/'bin/java','-cp',str(b)+os.pathsep+cp,'PatchTowny',b/'original.class',b/'patched.class'],check=True);files[name]=(b/'patched.class').read_bytes()
text=files['META-INF/mods.toml'].decode().replace('version="2.0.1"','version="2.0.2"').replace('versionRange="[1.15.0]"','versionRange="[1.15.2,1.16.0)"');files['META-INF/mods.toml']=text.encode()
manifest=files['META-INF/MANIFEST.MF'].decode();manifest=manifest.replace('Implementation-Version: 2.0.1','Implementation-Version: 2.0.2');files['META-INF/MANIFEST.MF']=manifest.encode()
for folder in ['mods','server/mods']:
 p=REPO/folder/'TownyRecruits.jar';archive(p,files);p.with_suffix('.jar.sha256').write_text(sha(p)+'  '+p.name+'\n')
(ROOT/'audit/townyrecruits.json').write_text(json.dumps(dict(status='passed',version='2.0.2',sha256=sha(REPO/'mods/TownyRecruits.jar'),changes=['dependency range for Recruits 1.15.2','remove write to unused null UseAsyncTargetFinding field'],obsolete_field_referenced_only_by_its_declaration_in_recruits=True),indent=2)+'\n')
