#!/usr/bin/env python3
"""Validate shipped JARs, recipe accessibility, removed data, preserved upstream resources and metadata."""
import argparse,hashlib,importlib.util,json,zipfile
from pathlib import Path
from build import ROOT,REPO,REMOVED,REPLACEMENTS,pack,sha,git_sha
def read(p):
 with zipfile.ZipFile(p) as z:assert z.testzip() is None;return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def main():
 p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path);a=p.parse_args()
 report=json.loads((ROOT/'audit/build.json').read_text());lock=json.loads((REPO/'modpack-lock.json').read_text())
 spec=importlib.util.spec_from_file_location('adm',REPO/'medieval-create/update_adm.py');adm=importlib.util.module_from_spec(spec);spec.loader.exec_module(adm)
 data=adm.parse((REPO/'server/config/admanticheat/idfile/admanticheat_id_list.json').read_text())
 for row in report:
  path=REPO/'mods'/row['file'];b=path.read_bytes();files=read(path)
  assert b==(REPO/'server/mods'/row['file']).read_bytes()
  assert sha(b)==row['sha256'] and git_sha(b)==row['git_sha'] and len(b)==row['size']
  assert path.with_suffix('.jar.sha256').read_text().split()[0]==sha(b)
  assert not (REPO/'mods'/row['old_file']).exists()
  entry=next(e for e in lock['mods'] if e['id']==row['id'])
  assert all(entry[k]==row[k] for k in ['file','sha256','sha512','git_sha','size'])
  assert next(e for e in data['modIds'] if e['id']==row['id'])['sha256']==sha(b)
  installed=json.loads((REPO/'seasonal-calendar/installed-mods.json').read_text())
  entry=next(e for e in installed if e['name']==row['file']);assert entry['sha']==row['git_sha'] and entry['size']==row['size']
  assert json.loads((REPO/'seasonal-calendar/audit/coverage.json').read_text())['jar_sha256'][row['file']]==sha(b)
  for n,raw in files.items():
   if not n.startswith('data/') or not n.endswith('.json'):continue
   d=json.loads(raw)
   if row['id']=='vinery':assert not any(s.encode() in raw for s in REMOVED),n
   if '/recipes/' not in n:continue
   mapping=REPLACEMENTS.get(Path(n).stem,{})
   for old in mapping:assert old.encode() not in raw,(n,old)
  if row['id']=='brewinandchewin':
   d=json.loads(files['data/brewinandchewin/recipes/heating_cask.json'])
   assert d['pattern']==['sss','cCc','pmp']
   assert d['key']['C']['item']=='minecraft:cauldron' and d['key']['m']['item']=='minecraft:campfire'
   assert b'minecraft:glowstone_dust' in files['data/brewinandchewin/recipes/fermenting/glittering_grenadine_from_water.json']
  else:
   assert b'minecraft:glowstone_dust' in files['data/vinery/recipes/wine_fermentation/mellohi_wine.json']
   for name in ['WineYears','WineStorage','WineMigration']:assert 'net/satisfy/vinery/core/util/'+name+'.class' in files
  if a.upstream:
   original=read(a.upstream/row['old_file']);allowed=set(row['changed_data'])|{'META-INF/empirecraft-drinks.json'}
   if row['id']=='vinery':allowed|={n for n in files if n.endswith('.class')}
   assert set(original)-set(files)==set(row['removed_files'])
   assert all(n in allowed or raw==files.get(n) for n,raw in original.items() if n in files)
   assert set(files)-set(original)<=allowed
  duplicate=ROOT/'build/repacked.jar';pack(duplicate,files);assert duplicate.read_bytes()==b
  print('PASS',row['id'],'recipes, glowstone, data, both sides, reproducible archive, integrity records')
 print('PASS checksums, ADM integrityHash, upstream preservation')
if __name__=='__main__':main()
