"""Generate matching cooking data for both sides, preserving unrelated recipes."""
from pathlib import Path
from zipfile import ZipFile
import hashlib,json
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def main():
 files={}
 def put(path,value):files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
 eggs={'minecraft:egg','minecraft:turtle_egg','minecraft:sniffer_egg'}
 for path in (REPO/'mods').glob('*.jar'):
  with ZipFile(path) as z:
   for name in z.namelist():
    for namespace in ['dragnlivestock','naturalist']:
     prefix='assets/'+namespace+'/models/item/'
     if name.startswith(prefix) and name.endswith('.json'):
      item=name[len(prefix):-5]
      if (item=='egg' or item.endswith('_egg') or item=='snail_eggs') and item!='cooked_egg' and 'spawn' not in item:eggs.add(namespace+':'+item)
 put('data/empirecraft_kitchen/tags/items/raw_eggs.json',{'replace':True,'values':[{'id':'#forge:eggs','required':False},{'id':'#c:eggs','required':False},*[{"id":egg,"required":False} for egg in sorted(eggs)]]})
 put('data/forge/tags/items/cooked_eggs.json',{'replace':True,'values':['naturalist:cooked_egg','tfc:food/boiled_egg','farmersdelight:fried_egg']})
 put('data/empirecraft_casting/tags/items/food/compat/farmersdelight_fried_egg.json',{'replace':True,'values':['naturalist:cooked_egg','farmersdelight:fried_egg']})
 for kind,suffix,time in [('smelting','',200),('smoking','_from_smoking',100),('campfire_cooking','_from_campfire_cooking',600)]:
  put('data/naturalist/recipes/cooked_egg'+suffix+'.json',{'type':'minecraft:'+kind,'ingredient':{'tag':'empirecraft_kitchen:raw_eggs'},'result':'naturalist:cooked_egg','experience':.35,'cookingtime':time})
 put('data/empirecraft_kitchen/tfc/item_heats/raw_eggs.json',{'ingredient':{'tag':'empirecraft_kitchen:raw_eggs'},'heat_capacity':1.0})
 put('data/tfc/recipes/heating/cooked_egg.json',{'type':'tfc:heating','ingredient':{'tag':'empirecraft_kitchen:raw_eggs'},'result_item':{'stack':{'item':'naturalist:cooked_egg'}},'temperature':200})
 script='''// priority: -22000
// Runs after the shared food, metallurgy and kitchen compatibility passes.
ServerEvents.recipes(function (event) {
  var edits = {};
  function rewrite(value) {
    if (typeof value === 'string') return value === 'tfc:food/cooked_egg' || value === 'farmersdelight:fried_egg' ? 'naturalist:cooked_egg' : value;
    if (Array.isArray(value)) return value.map(rewrite);
    if (value && typeof value === 'object') { var result = {}; Object.keys(value).forEach(function (key) { result[key] = rewrite(value[key]); }); return result; }
    return value;
  }
  function normalize(recipe) {
    if (recipe.removed) return;
    recipe.serialize();
    var text = recipe.json.toString();
    if (text.indexOf('tfc:food/cooked_egg') >= 0 || text.indexOf('farmersdelight:fried_egg') >= 0)
      edits[String(recipe.getId())] = rewrite(JSON.parse(text));
  }
  event.forEachRecipe({}, normalize);
  event.addedRecipes.forEach(normalize);
  Object.keys(edits).forEach(function (id) {
    event.addedRecipes.removeIf(function (recipe) { return String(recipe.getId()) === id; });
    event.remove({id:id});
    event.custom(edits[id]).id(id);
  });
});
'''
 files['server_scripts/zzzzzzzzzzzzzz_empirecraft_kitchen_fixes.js']=script.encode()
 files['client_scripts/empirecraft_kitchen_fixes.js']=b"JEIEvents.hideItems(function (event) { event.hide('farmersdelight:fried_egg'); });\n"
 for folder in [REPO/'kubejs',REPO/'server/kubejs']:
  for name,body in files.items():
   p=folder/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(body)
 (ROOT/'audit/data.json').write_text(json.dumps({'raw_eggs':sorted(eggs),'files':{name:hashlib.sha256(body).hexdigest() for name,body in files.items()}},indent=2)+'\n')
 print('KITCHEN_DATA_PASS raw_egg_ids='+str(len(eggs)))
if __name__=='__main__':main()
