#!/usr/bin/env python3
"""Widen only metadata for the API-compatible Sails 19 patch; preserve every code/resource byte."""
import hashlib,json,subprocess,zipfile
from pathlib import Path
from build import archive,sha,REPO,ROOT
BEFORE={
 'pack-fixes':('empirecraft-pack-fixes-1.0.0.jar','1698576b8576babb3eab0502b0082097fe749ba78905a3053113bbcf2520bca4')}
def main():
 report=[]
 for module,(name,pinned) in BEFORE.items():
  source=REPO/module/'src/main/resources/META-INF/mods.toml'
  text=source.read_text().replace('[0.3.2-empirecraft.18]','[0.3.2-empirecraft.18,0.3.2-empirecraft.20)');source.write_text(text)
  installed=REPO/'mods'/name
  with zipfile.ZipFile(installed) as z:entries={n:z.read(n) for n in z.namelist()}
  baseline=ROOT/'build'/('before-'+name)
  if not baseline.exists():
   raw=installed.read_bytes()
   if hashlib.sha256(raw).hexdigest()!=pinned:
    raw=subprocess.run(['git','show','9a539e9b:mods/'+name],cwd=REPO,check=True,stdout=subprocess.PIPE).stdout
   assert hashlib.sha256(raw).hexdigest()==pinned
   baseline.parent.mkdir(parents=True,exist_ok=True);baseline.write_bytes(raw)
  assert sha(baseline)==pinned
  with zipfile.ZipFile(baseline) as z:before={n:z.read(n) for n in z.namelist()}
  entries['META-INF/mods.toml']=text.encode()
  assert set(before)==set(entries) and all(before[n]==entries[n] for n in entries if n!='META-INF/mods.toml')
  target=REPO/module/'dist'/name;archive(target,entries)
  for path in [target,REPO/'mods'/name,REPO/'server/mods'/name]:
   if path!=target:path.write_bytes(target.read_bytes())
   path.with_suffix('.jar.sha256').write_text(sha(target)+'  '+name+'\n')
  report.append(dict(module=module,file=name,before_sha256=pinned,sha256=sha(target),changed_entries=['META-INF/mods.toml'],all_other_entries_identical=True))
 (ROOT/'build/compat-dependencies.json').write_text(json.dumps(report,indent=2)+'\n')
 print('SAILS_DEPENDENCY_METADATA_PASS',len(report),'JARs; all code and other resources byte-identical')
if __name__=='__main__':main()
