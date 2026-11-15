"""Integrate the exact uploaded Thirty Years War 1.3 without modifying its JAR."""
import copy,json,zipfile,hashlib,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
JAR='epic-knights-thirty-years-war-1.20.1-forge-1.3-empirecraft.1.jar'
NS='thirtyyearswar';CAST='empirecraft_casting:quality_shaped'
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def generate():
 with zipfile.ZipFile(REPO/'mods'/JAR) as z:
  original={n.split('/recipes/')[1][:-5]:json.loads(z.read(n)) for n in z.namelist() if n.startswith('data/'+NS+'/recipes/') and n.endswith('.json')}
 recipes=copy.deepcopy(original);forged=[];assembly=[]
 def item(path):return {'item':path}
 def forge(v,kind):
  v['type']='overgeared:forging';v.update(category='armors' if kind=='armor' else 'misc',tier='iron',hammering=5,has_quality=True,needs_minigame=True,has_polishing=False,need_quenching=False,requires_blueprint=False,blueprint=[kind],quality_difficulty='none',show_notification=True)
  for symbol,ingredient in list(v['key'].items()):
   if ingredient.get('item')=='minecraft:netherite_ingot':ingredient=item('tfc:metal/ingot/black_steel')
   tag=ingredient.get('tag','');name=ingredient.get('item','')
   if tag in ['forge:plates/steel','forge:ingots/steel']:ingredient=item('overgeared:steel_plate' if 'plates' in tag else 'overgeared:steel_ingot')
   elif tag=='forge:nuggets/steel':ingredient=item('overgeared:steel_nugget')
   elif tag=='magistuarmory:small_plates/steel':ingredient=item('magistuarmory:small_steel_plate')
   name=ingredient.get('item','')
   if name in ['overgeared:steel_ingot','overgeared:steel_plate','overgeared:steel_nugget','magistuarmory:small_steel_plate','tfc:metal/ingot/black_steel']:
    ingredient={'type':'tfc:heatable','ingredient':ingredient,'min_temp':900 if 'black_steel' not in name else 920}
   elif name.startswith(NS+':') and not any(w in name for w in ['cloth','fabric','fibers']):ingredient=dict(ingredient,transfer_nbt=True)
   v['key'][symbol]=ingredient
  forged.append(v['result']['item'])
 for name,v in recipes.items():
  if v['type']!='minecraft:crafting_shaped':continue
  path=v['result']['item'].split(':')[1]
  if any(k in json.dumps(v['key']) for k in ['forge:plates/steel','forge:ingots/steel','forge:nuggets/steel','small_plates/steel','netherite_ingot']):forge(v,'sword' if any(w in path for w in ['steel_','musket','pistol']) else 'armor')
  elif any(NS+':' in json.dumps(i) and not any(w in json.dumps(i) for w in ['cloth','fabric','fibers']) for i in v['key'].values()):v['type']=CAST
 # Dissimilar assemblies use the head that actually belongs to the weapon.
 heads={
 'pike':([' H ','NPN',' P '],{'H':item('tfc:metal/javelin_head/steel'),'N':item('overgeared:steel_nugget'),'P':{'tag':'magistuarmory:poles'}}),
 'partisan':([' HN',' SP','P  '],{'H':item('tfc:metal/javelin_head/steel'),'N':item('overgeared:steel_nugget'),'S':item('overgeared:steel_plate'),'P':{'tag':'magistuarmory:poles'}}),
 'halberd':([' AH',' SP','P  '],{'A':item('tfc:metal/axe_head/steel'),'H':item('tfc:metal/javelin_head/steel'),'S':item('overgeared:steel_plate'),'P':{'tag':'magistuarmory:poles'}}),
 'pollax':([' AH',' SP','P  '],{'A':item('tfc:metal/axe_head/steel'),'H':item('tfc:metal/hammer_head/steel'),'S':item('overgeared:steel_plate'),'P':{'tag':'magistuarmory:poles'}})}
 for weapon,(pattern,key) in heads.items():
  v=recipes['knights_baroque_'+weapon];v.update(type=CAST,pattern=pattern,key=key);assembly.append(v['result']['item']);forged.remove(v['result']['item'])
 # Distinguish the two otherwise identical rapier recipes even when mirrored.
 recipes['knights_baroque_cuphilt_rapier']['pattern']=['CC','DC']
 # Heavy barrel/lock/stock progression is shared with our current firearms.
 for weapon,pattern,key in [
 ('matchlock_musket',['BBL','PSP',' I '],{'B':item('tfc_muskets:musket_barrel'),'L':item('empirecraft_casting:matchlock'),'P':item('overgeared:steel_plate'),'S':item('tfc_muskets:wooden_stock'),'I':item('overgeared:steel_ingot')}),
 ('wheellock_pistol',[' BL',' PS'],{'B':item('tfc_muskets:musket_barrel'),'L':item('empirecraft_tfc:gun_lock'),'P':item('overgeared:steel_plate'),'S':item('tfc_muskets:wooden_stock')})]:
  v=recipes['knights_baroque_'+weapon];v['pattern']=pattern;v['key']=key;forge(v,'sword')
 recipes['knights_paper_cartridge']={'type':'minecraft:crafting_shapeless','ingredients':[item('tfc_muskets:bullet'),item('tfc_muskets:gunpowder_pinch'),item('minecraft:paper'),item('minecraft:string')],'result':item(NS+':paper_cartridge')}
 # Textiles start with TFC's spun wool and woven cloth, without a cloth duplication loop.
 recipes['knights_wool_fibers']={'type':'minecraft:crafting_shapeless','ingredients':[item('tfc:wool_yarn')],'result':dict(item(NS+':wool_fibers'),count=1)}
 recipes['knights_woollen_cloth']={'type':'minecraft:crafting_shapeless','ingredients':[item('tfc:wool_cloth')],'result':item(NS+':woollen_cloth')}
 # Distinguish standalone leather boots from one another and existing footwear.
 for name,base in [('baroque_apron_skirt_boots','baroque_apron_skirt'),('baroque_breeches_boots','baroque_breeches'),('baroque_riders_breeches_boots','baroque_riders_breeches'),('baroque_skirt_boots','baroque_skirt')]:
  recipes[name]={'type':CAST,'pattern':[' B ','L L','S S'],'key':{'B':item(NS+':'+base),'L':{'tag':'forge:leather'},'S':{'tag':'magistuarmory:leather_strips'}},'result':item(NS+':'+name)}
 # Both manual spinning and Create use the native TFC 8-yarn wool ratio.
 recipes['spinning/wool_block']={'type':'tfc:damage_inputs_shapeless_crafting','recipe':{'type':'minecraft:crafting_shapeless','ingredients':[item('tfc:spindle'),{'tag':'minecraft:wool'}],'result':dict(item('tfc:wool_yarn'),count=8)}}
 recipes['cutting/wool_block']={'type':'create:cutting','ingredients':[{'tag':'minecraft:wool'}],'results':[dict(item('tfc:wool_yarn'),count=8)],'processingTime':100}
 # Keep result metadata available to validation without changing Create/TFC JSON schemas.
 files={}
 for name,v in recipes.items():files['data/'+NS+'/recipes/'+name+'.json']=encoded(v)
 # Reassert after both shared smithing and food recipe passes on every /reload.
 script='// priority: -20000\n// Generated by thirtyyearswar-integration/generate.py.\nconst EC_TYW_RECIPES = '+json.dumps(recipes,ensure_ascii=False,indent=2)+'''\nServerEvents.recipes(event => {
  Object.keys(EC_TYW_RECIPES).forEach(name => {
    event.remove({id:'thirtyyearswar:' + name})
    event.custom(EC_TYW_RECIPES[name]).id('thirtyyearswar:' + name)
  })
  console.info('Empirecraft Thirty Years War: 84 integrated recipes')
})
'''
 files['server_scripts/zzzzzzzzzzzz_thirtyyearswar.js']=script.encode()
 weapons={k:[] for k in ['piercing','slashing','crushing']}
 for v in recipes.values():
  if 'result' not in v:continue
  out=v['result']['item'];p=out.split(':')[1]
  if any(w in p for w in ['rapier','pike','partisan','musket','pistol']):weapons['piercing'].append(out)
  elif any(w in p for w in ['sidesword','walloon_sword','halberd','pollax']):weapons['slashing'].append(out)
 for kind,items in weapons.items():files['data/tfc/tags/items/deals_'+kind+'_damage.json']=encoded(dict(replace=False,values=sorted(set(items))))
 # New equipment carries native TFC heat, including mixed armor and colored variants.
 metal=set(forged+assembly);metal.add('magistuarmory:small_steel_plate')
 for v in recipes.values():
  if v['type']=='minecraft:smithing_transform':metal.add(v['result']['item'])
 files['data/empirecraft_thirtyyearswar/tfc/item_heats/steel_equipment.json']=encoded(dict(ingredient=[item(v) for v in sorted(metal)],heat_capacity=1.5,forging_temperature=900,welding_temperature=1200))
 # No custom namespace is needed for quality modifiers: the existing handler accepts ArmorItem/TieredItem.
 sys.path.insert(0,str(REPO/'handbook'));import layout
 for lang,title,texts in [
 ('uk_ua','Спорядження Тридцятилітньої війни',[
 'Одяг Thirty Years War використовує вовну TFC: блок вовни з прядкою TFC або під пилкою Create дає 8 ниток. Одна нитка дає одне волокно мода; готова вовняна тканина TFC дає одне вовняне полотно мода. Із шести волокон зробіть повсть, а з неї — капелюхи та підкладки.',
 'Сталеву броню й клинки куйте на ковадлі Overgeared. Сталь, листи, самородки й малі пластини перед ковкою нагрійте від 900 °C. Для кірасирського обладунку замість незериту потрібен злиток чорної сталі від 920 °C. Мінігра визначає якість.',
 'Для піки й партизани потрібне сталеве вістря списа TFC; для алебарди — вістря й головка сокири; для полекса — головки сокири й молота. Деталі спершу куйте з нагрітої сталі. При складанні зберігається найнижча якість деталей.',
 'Для нового мушкета потрібні два готових стволи TFC Muskets, ґнотовий замок, дерев’яне ложе, два сталеві листи та злиток. Для колісцевого пістоля — один ствол, складний замок, ложе й сталевий лист. Обидві зброї збираються ковкою.',
 'Паперовий патрон: одна готова куля TFC Muskets, одна щіпка пороху аркуш паперу й нитка дають один патрон Thirty Years War. Він призначений для його мушкета та пістоля. Залізні самородки не замінюють готові кулі.',
 'Чорніння броні залишається у ковальському столі з шаблоном чорніння й чорним барвником. Воно зберігає NBT базової броні. Черевики зі спідницями чи штанами тепер потребують відповідного готового одягу.']),
 ('en_us','Thirty Years War equipment',[
 'Start textiles with TFC wool: spin a wool block with a TFC spindle or cut it with a Create saw for eight yarn. One yarn becomes one wool fiber, and one TFC wool cloth becomes one Thirty Years War woollen cloth. Six fibers make felt fabric.',
 'Forge steel armor and blades on the Overgeared anvil. Heat steel ingots, sheets, nuggets and small plates to at least 900 C. Cuirassier armor uses black steel at 920 C instead of netherite. The minigame assigns equipment quality.',
 'The pike and partisan use a forged TFC steel spearhead. The halberd needs spear and axe heads; the pollax uses axe and hammer heads. Assembly inherits the lowest input quality.',
 'The matchlock musket costs two finished TFC Muskets barrels, a matchlock, wooden stock, two steel sheets and an ingot. The wheellock pistol uses one barrel, a complex lock, stock and steel sheet. Assemble them by forging.',
 'One finished TFC Muskets bullet, one gunpowder pinch and one paper and one string produce one Thirty Years War paper cartridge for its firearms. Darkening remains a smithing transform, preserving base NBT. Combined boots now require their matching skirt or breeches.'])]:
  entry=dict(name=title,icon=NS+':baroque_cuirass',category='empirecraft_guidebook:metals',read_by_default=True,sortnum=16,pages=[dict(type='patchouli:text',text=t) for t in texts]);layout.fit({}, {'tyw':entry});files['assets/empirecraft_guidebook/patchouli_books/handbook/'+lang+'/entries/metals/thirtyyearswar.json']=encoded(entry)
 for side in [REPO,REPO/'server']:
  for name,raw in files.items():p=side/'kubejs'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
 (ROOT/'recipes.json').write_bytes(encoded(recipes));(ROOT/'audit/generated.json').write_bytes(encoded(dict(upstream_sha256=hashlib.sha256((REPO/'mods'/JAR).read_bytes()).hexdigest(),original_recipes=len(original),integrated_recipes=len(recipes),forged=sorted(set(forged)),assembly=assembly,damage_tags=weapons,files={n:hashlib.sha256(v).hexdigest() for n,v in files.items()})))
 print('TYW_GENERATED recipes='+str(len(recipes))+' forging='+str(len(set(forged))))
if __name__=='__main__':generate()
