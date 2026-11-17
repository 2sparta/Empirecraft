"""Validate final resources against the supplied artifact and the pack's active exclusions."""
import hashlib,json,zipfile,tomllib
from pathlib import Path
from build import ROOT,REPO,NAME,sha
from resources import policy

def main():
 pin=json.loads((ROOT/'inputs.json').read_text());source=ROOT/'upstream'/pin['file'];assert sha(source)==pin['sha256']
 jar=ROOT/'dist'/NAME;digest=sha(jar);p=policy()
 with zipfile.ZipFile(source) as old,zipfile.ZipFile(jar) as new:
  art=[n for n in old.namelist() if not n.endswith('/') and (n.endswith('.png') or '/models/' in n)]
  assert all(new.read(n)==old.read(n) for n in art)
  meta=tomllib.loads(new.read('META-INF/mods.toml').decode());assert meta['mods'][0]['modId']=='chineseweapons'
  assert any(v['modId']=='empirecraft_armory' and v['mandatory'] for v in meta['dependencies']['chineseweapons'])
  with zipfile.ZipFile(next((REPO/'mods').glob('empirecraft-tfc-integration*.jar'))) as z:banned=set(json.loads(z.read('empirecraft-tfc-policy.json'))['forbidden'])
  recipes=[];forged=0
  for n in new.namelist():
   if n.startswith('data/chineseweapons/recipes/') and n.endswith('.json'):
    data=json.loads(new.read(n));raw=json.dumps(data);assert not any('"'+i+'"' in raw for i in banned),(n,'retired ingredient')
    assert data['type']!='chineseweapons:weapon_casting_shaped';recipes.append(n);forged+=data['type']=='overgeared:forging'
    out=data.get('result');out=out.get('item') if isinstance(out,dict) else out
    assert out not in p['aliases'],n
   if n.startswith('data/chineseweapons/'):assert '/worldgen' not in n and '/biome_modifier/' not in n,n
  assert forged==21
  for language in ['uk_ua','en_us']:
   names=[n for n in new.namelist() if n.startswith('assets/empirecraft_guidebook/patchouli_books/handbook/'+language+'/') and 'chinese' in n]
   assert names,(language,'no handbook chapter')
 for folder in ['mods','server/mods']:assert sha(REPO/folder/NAME)==digest
 for audit in ['runtime','build']:
  a=json.loads((ROOT/'audit'/f'{audit}.json').read_text());assert a['status']=='passed' and a.get('mod_sha256',a.get('sha256'))==digest
 report=dict(status='passed',sha256=digest,art_files_retained=len(art),recipes=len(recipes),native_forging_recipes=forged,player_armor=len(p['armor']),horse_armor=len(p['horse']),paired_jars_identical=True,retired_ingredients_absent=True)
 (ROOT/'audit/resources.json').write_text(json.dumps(report,indent=2)+'\n');print('CHINESE_RESOURCES_PASS',json.dumps(report))
if __name__=='__main__':main()
