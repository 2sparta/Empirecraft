#!/usr/bin/env python3
import argparse,copy,hashlib,importlib.util,json,tomllib,zipfile
from build import ROOT,REPO,write

def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
 reports=json.loads((ROOT/'audit/build.json').read_text());changed={PathLike.split('/')[-1] for PathLike in reports}
 paths=[REPO/'modpack-lock.json',REPO/'seasonal-calendar/installed-mods.json',REPO/'forging-balance/installed-client-mods.json',REPO/'seasonal-calendar/audit/coverage.json',REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json']
 spec=importlib.util.spec_from_file_location('adm',REPO/'medieval-create/update_adm.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
 before=[json.loads(p.read_text()) for p in paths[:4]]+[helper.parse(paths[4].read_text())];lock,server,client,coverage,adm=copy.deepcopy(before)
 mod_hashes={}
 for name in sorted(changed):
  sides=[p for p in [REPO/'mods'/name,REPO/'server/mods'/name] if p.exists()]
  raw=sides[0].read_bytes();assert all(p.read_bytes()==raw for p in sides)
  sha=hashlib.sha256(raw).hexdigest();git=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
  for row in lock['mods']:
   if row.get('file')==name:row.update(size=len(raw),sha256=sha,sha512=hashlib.sha512(raw).hexdigest(),git_sha=git,source='local:native-content',pin_reason='Native registration exclusions and bronze unification; native-content/ contains pinned inputs and validation.')
  for rows in [server,client]:
   for row in rows:
    if row['name']==name:row.update(size=len(raw),sha=git)
  if name in coverage['jar_sha256']:coverage['jar_sha256'][name]=sha
  with zipfile.ZipFile(sides[0]) as z:
   if 'META-INF/mods.toml' in z.namelist():
    for mod in tomllib.loads(z.read('META-INF/mods.toml').decode())['mods']:mod_hashes[mod['modId']]=sha
 for row in adm['modIds']:
  if row['id'] in mod_hashes and row['filePresent']:row['sha256']=mod_hashes[row['id']]
 adm['integrityHash']=helper.integrity(adm)
 assert adm['resourcePackIds']==before[4]['resourcePackIds']
 for path,old,new in zip(paths,before,[lock,server,client,coverage,adm]):
  if a.check:assert old==new,('Stale native metadata',path)
  else:write(path,new)
 print('PASS native client/server hashes, inventories, lock and ADM integrity')
if __name__=='__main__':main()
