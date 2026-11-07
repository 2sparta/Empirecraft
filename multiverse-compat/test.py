#!/usr/bin/env python3
"""Verify Starcatcher 3 dimension groups and unchanged native fish properties."""
import argparse,copy,json,tempfile,zipfile
from pathlib import Path
import build

def main():
 p=argparse.ArgumentParser();p.add_argument('--starcatcher',type=Path,required=True);p.add_argument('--sails',type=Path,required=True);a=p.parse_args();config=json.loads((build.ROOT/'config.json').read_text())
 for name,path in [('starcatcher',a.starcatcher),('sails',a.sails)]:assert build.digest(path)==config['downloads'][name]['sha256'],name
 name='data/starcatcher/starcatcher/dimension_entries.json'
 with zipfile.ZipFile(a.starcatcher) as star,zipfile.ZipFile(a.sails) as sails,zipfile.ZipFile(build.OUTPUT) as pack:
  original=json.loads(star.read(name));extra=json.loads(pack.read('data/empirecraft/starcatcher/dimension_entries.json'));assert extra==[{'dimension':'minecraft:world_europe','looks_like':'overworld'}];groups=original+extra
  assert [r for r in groups if r['looks_like']=='the_end']==[r for r in original if r['looks_like']=='the_end']
  assert [r for r in groups if r['looks_like']=='the_nether']==[r for r in original if r['looks_like']=='the_nether']
  assert not any('/fish/' in n or '/dimension/' in n or '/dimension_type/' in n for n in pack.namelist()),'Do not override fish definitions or create worlds'
  assert len(pack.namelist())==3
  wind=json.loads(pack.read('data/empirecraft/vs_sails/wind/multiverse_overworld.json'));base=json.loads(sails.read('data/vs_sails/vs_sails/wind/overworld.json'))
  assert wind['wind']==base['wind'];assert wind['targets']=={'dimensions':['minecraft:world_europe'],'dimension_tags':[]};assert wind['priority']>base['priority']
  assert json.loads(pack.read('pack.mcmeta'))['pack']['pack_format']==15
 for bad in [{'minecraft:overworld':['minecraft:the_nether']},{'minecraft:overworld':['minecraft:world_europe','minecraft:world_europe']},{'minecraft:overworld':['World Europe']},{}]:
  try:build.aliases_from({'dimension_aliases':bad})
  except ValueError:pass
  else:raise AssertionError('Invalid alias accepted')
 with tempfile.TemporaryDirectory() as d:
  out=Path(d)/'pack.zip';build.write_pack(out,build.entries_for(a.starcatcher,a.sails,config['dimension_aliases']));assert out.read_bytes()==build.OUTPUT.read_bytes()
 print('MULTIVERSE_STAR3_PASS dimension_groups=4 fish_overrides=0 unchanged_fish_properties=true byte_identical_rebuild=true')
if __name__=='__main__':main()
