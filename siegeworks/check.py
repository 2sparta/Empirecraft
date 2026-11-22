"""Verify paired files, all steel blueprint substitutions and unmodified original artwork."""
import hashlib,json,zipfile
from pathlib import Path
from build import ROOT,REPO,sha

def main():
 installed=list((REPO/'mods').glob('siegeworks-*.jar'))
 assert len(installed)==1,'Exactly one installed Siegeworks'
 NAME=installed[0].name
 checks=0
 def check(ok,message):
  nonlocal checks
  checks+=1
  if not ok:raise AssertionError(message)
 for name,digest in json.loads((ROOT/'inputs.json').read_text()).items():check(sha(ROOT/'upstream'/name)==digest,'Upstream '+name)
 with zipfile.ZipFile(ROOT/'upstream/siegeworks-forge-0.1.0-beta.11-1.20.1.jar') as original,zipfile.ZipFile(REPO/'mods'/NAME) as modified:
  art=[n for n in original.namelist() if n.startswith('assets/') and not n.endswith('/')]
  for n in art:check(original.read(n)==modified.read(n),'Original art retained '+n)
  for id in ['culverin','serpentine','mons_meg','hand_cannon','arquebus']:
   n='data/siegeworks/blueprints/'+id+'.json';old=json.loads(original.read(n));new=json.loads(modified.read(n));check(len(old['stages'])==len(new['stages']),'Stages retained '+id)
   mapping={'minecraft:iron_ingot':'overgeared:steel_ingot','minecraft:iron_nugget':'overgeared:steel_nugget','minecraft:iron_block':'overgeared:steel_block','#siegeworks:iron_ingots':'#forge:ingots/steel','#siegeworks:iron_blocks':'#forge:storage_blocks/steel','#forge:ingots/iron':'#forge:ingots/steel','#forge:storage_blocks/iron':'#forge:storage_blocks/steel','siegeworks:firearm_lock':'empirecraft_casting:matchlock'}
   for before,after in zip(old['stages'],new['stages']):check({mapping.get(k,k):v for k,v in before['materials'].items()}==after['materials'],'Quantities and canonical materials '+id)
  barrel=json.loads(modified.read('data/siegeworks/recipes/barrel.json'));check(barrel['type']=='overgeared:forging','Native barrel forging');check(sum(row.count('#') for row in barrel['pattern'])==8,'Eight ingots per barrel')
 for prefix in ['siegeworks-','axiomata-','recruits-','TownyRecruits']:
  files=list((REPO/'mods').glob(prefix+'*.jar'));check(len(files)==1,'Single client '+prefix);check(files[0].read_bytes()==(REPO/'server/mods'/files[0].name).read_bytes(),'Paired '+prefix)
 for side in ['', 'server/']:
  check(not list((REPO/(side+'mods')).glob('siege-machines-*.jar')),'Siege Machines removed')
 for n in json.loads((ROOT/'audit/extra-files.json').read_text()):check((REPO/'kubejs'/n).read_bytes()==(REPO/'server/kubejs'/n).read_bytes(),'Paired data '+n)
 d=dict(status='passed',checks=checks,original_art_files=len(art),mod_sha256=sha(REPO/'mods'/NAME));(ROOT/'audit/static.json').write_text(json.dumps(d,indent=2)+'\n');print('SIEGE_STATIC_PASS',checks)
if __name__=='__main__':main()
