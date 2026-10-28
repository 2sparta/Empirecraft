#!/usr/bin/env python3
"""Validate native resources, raw material routes, side parity and input coverage."""
import json,zipfile,hashlib
from pathlib import Path
from build import ROOT,REPO,strings
from policy import blocked,METALS

report=json.loads((ROOT/'audit/build.json').read_text());policy=json.loads((ROOT/'audit/policy.json').read_text());removed=set(policy['removed'])
assert set(METALS)<=removed
assert {'create:andesite_door','create:brass_door','create:copper_door','create:framed_glass_door','create:framed_glass_trapdoor'}<=removed
assert not blocked('naturalist:crimson_froglass') and not blocked('mcwfurnitures:oak_end_table')
assert blocked('fruitsdelight:chorus_jelly') and blocked('createbigcannons:nethersteel_ingot')
assert not (REPO/'server/kubejs/server_scripts/1_steel.js').exists()
for path,row in report.items():
 p=REPO/path;raw=p.read_bytes();assert hashlib.sha256(raw).hexdigest()==row['sha256']
 with zipfile.ZipFile(p) as z:
  assert z.testzip() is None
  for name in z.namelist():
   if '/recipes/' not in name or not name.endswith('.json'):continue
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
print('PASS native resource audit, exact Epic Knights bronze recipe, removed CBC alloy recipe and client/server parity')
