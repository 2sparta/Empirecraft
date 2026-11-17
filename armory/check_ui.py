"""Validate the packaged full-cube assets, translations and paired production artifact."""
import io,json,zipfile
from PIL import Image
from build import ROOT,REPO,NAME,sha

def main():
 jar=ROOT/'dist'/NAME
 with zipfile.ZipFile(jar) as z:
  model=json.loads(z.read('assets/empirecraft_armory/models/block/armorer_table.json'));assert model['parent']=='minecraft:block/cube'
  assert set(model['textures'])=={'particle','up','down','north','south','east','west'}
  for kind in ['top','front','side','bottom']:
   image=Image.open(io.BytesIO(z.read('assets/empirecraft_armory/textures/block/workbench_'+kind+'.png')));assert image.size==(32,32);assert image.convert('RGBA').getchannel('A').getextrema()==(255,255)
  for language in ['uk_ua','en_us']:
   labels=json.loads(z.read('assets/empirecraft_armory/lang/'+language+'.json'));assert all(labels.get('armory.'+key) for key in ['required_count','stock_count','ready_count','treatment_hint','supply_hint','recipe_click','tab.0','tab.1','tab.2','help_body'])
  recipes=[n for n in z.namelist() if n.startswith('data/empirecraft_armory/recipes/parts/')];assert len(recipes)==140
  assert b'version="1.1.0"' in z.read('META-INF/mods.toml')
 for folder in ['mods','server/mods']:
  assert sha(REPO/folder/NAME)==sha(jar)
  assert len(list((REPO/folder).glob('empirecraft-armory-*.jar')))==1
 asset='assets/empirecraft_forging/models/item/lockwork.json';assert (REPO/'kubejs'/asset).read_bytes()==(REPO/'server/kubejs'/asset).read_bytes();assert json.loads((REPO/'kubejs'/asset).read_text())['textures']['layer0']=='minecraft:item/clock_00'
 report=dict(lockwork_icon_override=True,status='passed',sha256=sha(jar),original_opaque_32px_textures=4,full_cube_parent=True,translations=['uk_ua','en_us'],part_recipes=140,paired_identical_jars=True)
 (ROOT/'audit/ui-resources.json').write_text(json.dumps(report,indent=2)+'\n');print('WORKBENCH_RESOURCE_PASS',json.dumps(report))
if __name__=='__main__':main()
