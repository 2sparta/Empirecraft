#!/usr/bin/env python3
import hashlib,io,json,zipfile
from build import ROOT,REPO,NAME
from patch import VERSION,archive

def main():
 baseline=json.loads((ROOT/'build/runtime/nirvana-runtime-baseline.json').read_text());patched=json.loads((ROOT/'build/runtime/nirvana-runtime-patched.json').read_text());assert baseline['passed'] and patched['passed']
 raw=(REPO/'mods'/NAME).read_bytes();assert raw==(REPO/'server/mods'/NAME).read_bytes()
 entries={folder+'/'+NAME:raw for folder in ['mods','server/mods']}
 for folder in ['mods','server/mods']:entries[folder+'/'+NAME+'.sha256']=(REPO/folder/(NAME+'.sha256')).read_bytes()
 for name in ['modpack-lock.json','server/config/admanticheat/idfile/admanticheat_id_list.json']:entries[name]=(REPO/name).read_bytes()
 pack=REPO/'translations/dist/Empirecraft-Translations-uk-ru-1.1.0.zip';entries['resourcepacks/'+pack.name]=pack.read_bytes()
 entries['README-UA.md']=(ROOT/'README.md').read_bytes()
 entries['verification/baseline.json']=(ROOT/'build/runtime/nirvana-runtime-baseline.json').read_bytes();entries['verification/patched.json']=(ROOT/'build/runtime/nirvana-runtime-patched.json').read_bytes()
 source={}
 for path in ROOT.rglob('*'):
  if path.is_file() and not any(part in ['build','dist','__pycache__'] for part in path.relative_to(ROOT).parts):source[path.relative_to(REPO).as_posix()]=path.read_bytes()
 entries['nirvana-cut-sources.zip']=archive(source)
 entries['SHA256SUMS.txt']=''.join(hashlib.sha256(value).hexdigest()+'  '+name+'\n' for name,value in sorted(entries.items())).encode()
 target=ROOT/'dist/Empirecraft-Nirvana-No-Creepers-1.0.0.zip';target.parent.mkdir(exist_ok=True);target.write_bytes(archive(entries));digest=hashlib.sha256(target.read_bytes()).hexdigest();target.with_suffix('.zip.sha256').write_text(digest+'  '+target.name+'\n')
 standalone=target.parent/('nirvana-forge-'+VERSION+'.jar');standalone.write_bytes(raw);standalone.with_suffix('.jar.sha256').write_text(hashlib.sha256(raw).hexdigest()+'  '+standalone.name+'\n')
 print('PASS native no-creepers release; JAR SHA-256:',hashlib.sha256(raw).hexdigest());print('Installer ZIP SHA-256:',digest)
if __name__=='__main__':main()
