#!/usr/bin/env python3
"""Validate native resources, raw material routes, side parity and input coverage."""
import io,json,zipfile,hashlib,re
from pathlib import Path
from build import ROOT,REPO,strings
from policy import blocked,METALS

report=json.loads((ROOT/'audit/build.json').read_text());policy=json.loads((ROOT/'audit/policy.json').read_text());removed=set(policy['removed'])
pins=json.loads((ROOT/'inputs.json').read_text())
retired=json.loads((ROOT/'retired-policy.json').read_text())
assert set(retired['aliases'])<=removed and set(retired['blocks'])<=set(policy['blocks'])
assert all(key.split(':')[0] in retired['namespaces'] and blocked(key) and policy['aliases'][key]==value for key,value in retired['aliases'].items())
assert set(METALS)<=removed
assert {'create:andesite_door','create:brass_door','create:copper_door','create:framed_glass_door','create:framed_glass_trapdoor'}<=removed
assert not blocked('naturalist:crimson_froglass') and not blocked('mcwfurnitures:oak_end_table')
assert blocked('fruitsdelight:chorus_jelly') and blocked('createbigcannons:nethersteel_ingot')
assert not (REPO/'server/kubejs/server_scripts/1_steel.js').exists()
checked_embedded=set()
def check_helper_links(z,label):
 names=set(z.namelist())
 for name in names:
  if name.endswith('.class'):
   for owner in set(re.findall(rb'ua/empirecraft/nativecut/j[0-9a-f]+/PolicyHooks',z.read(name))):
    assert owner.decode()+'.class' in names,('Missing native helper',label,name,owner.decode())
  elif name.endswith('.jar'):
   raw=z.read(name);digest=hashlib.sha256(raw).hexdigest()
   if digest not in checked_embedded:
    checked_embedded.add(digest)
    with zipfile.ZipFile(io.BytesIO(raw)) as embedded:check_helper_links(embedded,label+'!/'+name)
for path,row in report.items():
 p=REPO/path;raw=p.read_bytes();assert hashlib.sha256(raw).hexdigest()==row['sha256']
 with zipfile.ZipFile(p) as z:
  assert z.testzip() is None
  check_helper_links(z,path)
  assert json.loads(z.read('META-INF/empirecraft-native-content.json'))['input_sha256']==pins[path]
  if 'META-INF/MANIFEST.MF' in z.namelist():assert z.namelist()[0]=='META-INF/MANIFEST.MF',('Manifest must be first',path)
  for name in z.namelist():
   if '/recipes/' not in name or not name.endswith('.json'):continue
   quoted=set(re.findall(r'"([a-z0-9_]+:[a-z0-9_./-]+)"',z.read(name).decode('utf-8-sig')))
   assert not quoted&removed,(path,name,quoted&removed)
   try:data=json.loads(z.read(name).decode('utf-8-sig'))
   except (ValueError,UnicodeDecodeError):continue
   assert not strings(data)&removed,(path,name,strings(data)&removed)
  if row['boundaries']:assert any(n.endswith('/PolicyHooks.class') for n in z.namelist())
for side in ['mods','server/mods']:
 with zipfile.ZipFile(REPO/side/'epic-knights-all-in-one-1_20_1-forge-empirecraft.1.jar') as z:
  mixture=json.loads(z.read('data/antiquelegacy/recipes/bronze_mixture.json').decode('utf-8-sig'))
  smelt=json.loads(z.read('data/antiquelegacy/recipes/furnace/bronze_ingot_smelting.json'))
  assert mixture['pattern']==['bbb','bab','bbb'] and mixture['key']['a']['tag']=='forge:nuggets/tin' and mixture['key']['b']['tag']=='forge:nuggets/copper'
  assert smelt['ingredient']=={'item':'antiquelegacy:bronze_mixture'} and smelt['result']=='createbigcannons:bronze_ingot' and smelt['cookingtime']==200 and smelt['experience']==0.7
 with zipfile.ZipFile(REPO/side/'createbigcannons-5.11.4-mc.1.20.1-forge-medieval.2.jar') as z:
  assert 'data/createbigcannons/recipes/medieval/bronze.json' not in z.namelist()
  assert 'data/createbigcannons/recipes/bronze_ingot_from_nuggets.json' in z.namelist()
for name in {Path(p).name for p in report}:
 a=REPO/'mods'/name;b=REPO/'server/mods'/name
 if a.exists() and b.exists():assert a.read_bytes()==b.read_bytes(),name
preserved=json.loads((ROOT/'retained-code.json').read_text())
for name,files in preserved.items():
 for side in ['mods','server/mods']:
  path=REPO/side/name
  if not path.exists():continue
  with zipfile.ZipFile(path) as z:
   for entry,digest in files.items():assert hashlib.sha256(z.read(entry)).hexdigest()==digest,('Prior feature changed',side,name,entry)
print('PASS native resource audit, exact Epic Knights bronze recipe, removed CBC alloy recipe and client/server parity')
print('PASS unchanged ship physics, wine aging, Spawn migration, prior Chipped exclusions and progression guards')
