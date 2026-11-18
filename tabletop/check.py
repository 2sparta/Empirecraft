"""Validate replacement parity, recipe accessibility data, names and handbook pages."""
import hashlib,json,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
sys.path.insert(0,str(REPO/'handbook'));from layout import lines,MAX_LINES

def main():
 d=json.loads((ROOT/'audit/data.json').read_text());checks=0
 def check(ok,msg):
  nonlocal checks
  checks+=1
  if not ok:raise AssertionError(msg)
 source=ROOT/'upstream'/d['file'];check(hashlib.sha256(source.read_bytes()).hexdigest()==d['upstream_sha256'],'Pinned upstream')
 with zipfile.ZipFile(source) as z:
  recipes=[n for n in z.namelist() if n.startswith('data/mctabletop/recipes/') and n.endswith('.json')];check(len(recipes)==36,'All 36 recipes retained')
  original=json.loads(z.read('data/mctabletop/recipes/go_fish_board.json'));expected=json.loads(json.dumps(original));expected['key']['A']={'tag':'minecraft:fishes'}
 for side in ['', 'server/']:
  base=REPO/side;check((base/'mods'/d['file']).read_bytes()==source.read_bytes(),'Unmodified shared JAR');check(not (base/'mods'/d['retired']).exists(),'Old game mod retired')
  for name in d['files']:check((base/name).read_bytes()==(REPO/name).read_bytes(),'Paired resource '+name)
  check(json.loads((base/'kubejs/data/mctabletop/recipes/go_fish_board.json').read_text())==expected,'Only Go Fish fish ingredient unified')
  wood=json.loads((base/'kubejs/data/empirecraft_mass/tags/blocks/wood.json').read_text())['values'];ids=[v if isinstance(v,str) else v['id'] for v in wood];check(not any(n.startswith('table_top_craft:') for n in ids),'Old mass IDs retired');check(all(ids.count('mctabletop:'+n)==1 for n in ['small_game_table','large_game_table','game_chair']),'Exactly one mass assignment per furniture block')
  path=base/'kubejs/assets/empirecraft_guidebook/patchouli_books/handbook/uk_ua/entries/leisure/tabletop.json';entry=json.loads(path.read_text());check(entry['category']=='empirecraft_guidebook:leisure','Existing handbook category');check(all(lines(p['text'])<=MAX_LINES for p in entry['pages']),'All handbook text fits')
 report=dict(status='passed',checks=checks,boards=33,recipes=36,jar_sha256=d['upstream_sha256']);(ROOT/'audit/static.json').write_text(json.dumps(report,indent=2)+'\n');print('BOARD_STATIC_PASS',checks)
if __name__=='__main__':main()
