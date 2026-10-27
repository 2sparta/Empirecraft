#!/usr/bin/env python3
"""Update native trim JAR identities and preserve unrelated mod records."""
import argparse,copy,hashlib,importlib.util,json,tomllib,zipfile
from build import ROOT,REPO,NAMES,MOD,VERSION,write

def digest(path):
 raw=path.read_bytes()
 return dict(size=len(raw),sha256=hashlib.sha256(raw).hexdigest(),sha512=hashlib.sha512(raw).hexdigest(),git_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest())
def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
 paths=[REPO/'modpack-lock.json',REPO/'seasonal-calendar/installed-mods.json',REPO/'seasonal-calendar/audit/coverage.json',REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json']
 original=[json.loads(p.read_text()) for p in paths];lock,inventory,coverage,adm=copy.deepcopy(original)
 spec=importlib.util.spec_from_file_location('adm_parser',REPO/'medieval-create/update_adm.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
 assert helper.integrity(adm)==adm['integrityHash'],'Existing ADM integrity mismatch'
 changed_mods=set()
 for old,new in NAMES.items():
  aliases={old,new,new.replace('empirecraft.2','empirecraft.1')}
  hashes=digest(REPO/'mods'/new)
  assert (REPO/'mods'/new).read_bytes()==(REPO/'server/mods'/new).read_bytes()
  row=next(r for r in lock['mods'] if r['file'] in aliases);row.update(file=new,**hashes,source='local:content-trim',pin_reason='Native registry/resource trim; all Chipped Nether variants except glowstone removed; upstream mod version retained for the existing exact dependencies. Rebuild and migration tests in content-trim/.')
  record=next(r for r in inventory if r['name'] in aliases);record.update(name=new,path='server/mods/'+new,size=hashes['size'],sha=hashes['git_sha'])
  for alias in aliases:coverage['jar_sha256'].pop(alias,None)
  coverage['jar_sha256'][new]=hashes['sha256']
  with zipfile.ZipFile(REPO/'mods'/new) as z:mods=tomllib.loads(z.read('META-INF/mods.toml').decode())['mods']
  for mod in mods:
   record=next(r for r in adm['modIds'] if r['id']==mod['modId']);assert record['version']==mod['version'];record.update(sha256=hashes['sha256'],filePresent=True);changed_mods.add(mod['modId'])
 hashes=digest(REPO/'server/mods'/MOD)
 row=next((r for r in lock['mods'] if r['id']=='empirecraft_content_trim'),None)
 if row is None:
  row=dict(id='empirecraft_content_trim',old_version=None,added=True,updated=False);lock['mods'].append(row);lock['server_jar_count']+=1
 row.update(file=MOD,version=VERSION,sides=['server'],source='local:content-trim',pin_reason='Blocks the requested mob families and migrates directly removed native IDs; adds water-only plant slime recipe.',**hashes)
 record=next((r for r in inventory if r['name'].startswith('empirecraft-content-trim-')),None)
 if record is None:record={};inventory.append(record)
 record.update(name=MOD,path='server/mods/'+MOD,size=hashes['size'],sha=hashes['git_sha']);inventory.sort(key=lambda r:r['name'])
 for alias in list(coverage['jar_sha256']):
  if alias.startswith('empirecraft-content-trim-'):coverage['jar_sha256'].pop(alias)
 coverage['jar_sha256'][MOD]=hashes['sha256'];coverage['jar_sha256']=dict(sorted(coverage['jar_sha256'].items()));coverage['jar_count']=len(inventory)
 adm['integrityHash']=helper.integrity(adm)
 assert lock['client_jar_count']==original[0]['client_jar_count']
 assert adm['resourcePackIds']==original[3]['resourcePackIds']
 assert all(r in adm['modIds'] for r in original[3]['modIds'] if r['id'] not in changed_mods)
 assert all(r in lock['mods'] for r in original[0]['mods'] if r['file'] not in set(NAMES)|set(NAMES.values())|{n.replace('empirecraft.2','empirecraft.1') for n in NAMES.values()} and r['id']!='empirecraft_content_trim')
 for path,before,after in zip(paths,original,[lock,inventory,coverage,adm]):
  if a.check:assert before==after,'Stale metadata '+str(path)
  else:write(path,after)
 print('PASS lock, server inventory, calendar hashes, and ADM for all six merged Knights mods; unrelated entries unchanged')
if __name__=='__main__':main()

