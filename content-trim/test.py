#!/usr/bin/env python3
"""Check physical resource removal and both-side identity independently of the patcher."""
import argparse,hashlib,json,zipfile
from build import ROOT,REPO,NAMES,MOD,ids_in

def main():
 removed=json.loads((ROOT/'audit/removed.json').read_text());blocked=set(removed['shields']+removed['chipped']+removed['chipped_block_only']+removed['extras'])
 for old,new in NAMES.items():
  client=REPO/'mods'/new;server=REPO/'server/mods'/new
  assert client.read_bytes()==server.read_bytes()
  assert client.with_suffix('.jar.sha256').read_text().split()[0]==hashlib.sha256(client.read_bytes()).hexdigest()
  with zipfile.ZipFile(client) as z:
   assert z.testzip() is None
   for n in z.namelist():
    if n.endswith('.json') and ('/recipes/' in n or '/tags/' in n or '/advancements/' in n):
     try:data=json.loads(z.read(n))
     except ValueError:
      # This malformed, unrelated recipe already exists in the merged upstream JAR.
      assert n=='data/epic_knights__japanese_armory/recipes/sai_2.json'
      assert hashlib.sha256(z.read(n)).hexdigest()=='74931915d97bd4a8e6b58e0e92fb328bd81fddd19f6fe142bf6539b1f4fd8845'
      continue
     assert not (ids_in(data) & blocked),'Removed ID reference: '+n
   for value in blocked:
    ns,path=value.split(':')
    assert 'assets/'+ns+'/models/item/'+path+'.json' not in z.namelist()
   if 'chippedexttras' in new:
    for row in json.loads(z.read('assets/chippedexttras/generated/registry.json')):assert not ids_in(row)&blocked
 # Exact reviewed scope and every original glowstone item must survive resource trimming.
 from policy import FAMILIES, MIXED_BLOCKS
 assert len(FAMILIES)==36 and 'glowstone' not in FAMILIES
 assert len(removed['chipped'])==1144 and len(removed['chipped_block_only'])==3
 assert len(removed['preserved_glowstone'])==20
 with zipfile.ZipFile(REPO/'mods'/NAMES['chipped-forge-1.20.1-3.0.7.jar']) as z:
  for value in removed['preserved_glowstone']:
   assert 'assets/chipped/models/item/'+value.split(':')[1]+'.json' in z.namelist()
  for value in removed['chipped']+removed['chipped_block_only']:
   assert 'assets/chipped/blockstates/'+value.split(':')[1]+'.json' not in z.namelist()
  assert not set(removed['preserved_glowstone'])&blocked
 with zipfile.ZipFile(REPO/'server/mods'/MOD) as z:
  assert not any('trimtest' in n for n in z.namelist())
  recipe=json.loads(z.read('data/empirecraft/recipes/slime_ball_from_plants.json'))
  assert recipe['key']['W']==dict(type='forge:partial_nbt',item='minecraft:potion',nbt={'Potion':'minecraft:water'})
  assert len(recipe['key']['P'])==3 and recipe['result']==dict(item='minecraft:slime_ball',count=1)
 print('PASS physical recipes/tags/models, native Extras registry, both-side checksums, no fixture in deliverable')
if __name__=='__main__':main()

