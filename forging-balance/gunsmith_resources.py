"""Generate workshop assets and the final KubeJS balance pass for both sides."""
import json,importlib.util
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
NS='empirecraft_forging'
def generate():
 res=ROOT/'src/main/resources'
 spec=importlib.util.spec_from_file_location('gunsmith_book_layout',REPO/'handbook/layout.py')
 layout=importlib.util.module_from_spec(spec);spec.loader.exec_module(layout)
 def put(path,data):
  p=res/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
 for name,texture in [('flintlock','minecraft:item/flint_and_steel'),('flintlock_blueprint','minecraft:item/paper'),('gun_workpiece','minecraft:item/iron_ingot'),('lockwork','minecraft:item/clock'),('wheellock','minecraft:item/clock')]:
  put(f'assets/{NS}/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':texture}})
 put(f'assets/{NS}/models/item/wheellock_blueprint.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/paper'}})
 put(f'assets/{NS}/blockstates/gunsmith_table.json',{'variants':{'':{'model':NS+':block/gunsmith_table'}}})
 # Open workbench: four legs, lower shelf and iron-edged wooden work surface.
 textures={'wood':'minecraft:block/spruce_planks','metal':'minecraft:block/iron_block','particle':'minecraft:block/spruce_planks'}
 elements=[]
 for a,b,tex in [([0,12,0],[16,15,16],'wood'),([0,15,0],[16,16,2],'metal'),([1,1,1],[3,12,3],'wood'),([13,1,1],[15,12,3],'wood'),([1,1,13],[3,12,15],'wood'),([13,1,13],[15,12,15],'wood'),([1,3,1],[15,4,15],'wood')]:
  elements.append({'from':a,'to':b,'faces':{face:{'texture':'#'+tex} for face in ['up','down','north','south','east','west']}})
 put(f'assets/{NS}/models/block/gunsmith_table.json',{'parent':'minecraft:block/block','textures':textures,'elements':elements})
 put(f'assets/{NS}/models/item/gunsmith_table.json',{'parent':NS+':block/gunsmith_table'})
 put(f'data/{NS}/loot_tables/blocks/gunsmith_table.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+':gunsmith_table'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 put('data/minecraft/tags/blocks/mineable/axe.json',{'replace':False,'values':[NS+':gunsmith_table']})
 names={'gunsmith_table':'Стіл зброяра','flintlock':'Кременевий замок','flintlock_blueprint':'Креслення кременевого замка','wheellock_blueprint':'Креслення колісцевого замка','gun_workpiece':'Заготовка рушничної деталі','lockwork':'Точний замковий механізм'}
 for locale in ['uk_ua','en_us','ru_ru']:
  p=res/f'assets/{NS}/lang/{locale}.json';d=json.loads(p.read_text());d.update({('block.' if k=='gunsmith_table' else 'item.')+NS+'.'+k:v for k,v in names.items()});put(p.relative_to(res),d)
 for locale,name,dated in [('uk_ua','Колісцевий замок','%1$s (зразка %2$s р.)'),('ru_ru','Колесцовый замок','%1$s (образца %2$s г.)'),('en_us','Wheellock','%1$s (%2$s pattern)')]:
  p=res/f'assets/{NS}/lang/{locale}.json';d=json.loads(p.read_text())
  d.update({f'item.{NS}.wheellock':name,f'item.{NS}.dated_gun':dated});put(p.relative_to(res),d)
 (res/f'data/{NS}/recipes/wheellock.json').unlink(missing_ok=True)
 for locale,label in [('uk_ua','Креслення колісцевого замка'),('en_us','Wheellock blueprint'),('ru_ru','Чертёж колесцового замка')]:
  path=res/f'assets/{NS}/lang/{locale}.json';strings=json.loads(path.read_text());strings[f'item.{NS}.wheellock_blueprint']=label;put(path.relative_to(res),strings)
 for locale,title,pages in [
  ('uk_ua','Зброярство',[
   'Стіл зброяра виготовляє стволи, ложа, ґнотові та кременеві замки, точні замкові механізми й креслення. ПКМ порожньою рукою показує вибір; Shift+ПКМ перемикає виріб. Для початку візьми один аркуш у головну руку й матеріали в інвентар.',
   'Тримай заготовку в головній руці. У другій чергуй молот TFC, зубило TFC та наждачний папір Create. ПКМ по столу: 1–3 секунди між операціями. Запізнення відкидає на крок, спам не прискорює роботу. Інструменти зношуються; прогрес зберігається в заготовці.',
   'Ствол: 3 залізні злитки, 12 операцій. Ложа: 2 дубові дошки й нитка, 6 операцій. Ґнотовий замок: Firearm Lock Siegeworks, самородок заліза й нитка, 6 операцій. До кожного виробу потрібен стартовий аркуш.',
   'Firearm Lock кується зі сталевої пластини, двох залізних самородків та мідного злитка. Він потрібен для ґнотового замка й точного механізму. Точний механізм додатково потребує 2 сталеві пластини, 2 шестерні, латунний лист та золото; 18 операцій.',
   'Дослідження кременевого або колісцевого замка потребує готового замка відповідного типу в інвентарі. Зразок витрачається разом із 3 аркушами (включно зі стартовим), чорнилом і точним механізмом; 18 операцій. Без зразка дослідження не почнеться.',
   'Кременевий замок без креслення: 4 пластини, 2 шестерні, точний механізм і 2 кремені; колісцевий: точний механізм, пірит, шестерня та пластина. Обидва мають 24 операції. Відповідне багаторазове креслення в інвентарі скорочує роботу до 6 операцій: Firearm Lock, пластина, шестерня й кремінь або пірит.',
   'Кірасирська броня найкраще стримує кулі, але вразлива до булав. Лати добре стримують рубальні удари; кольчуга слабша проти куль; підкладка гасить удари, але погано захищає від куль. Точні поправки є в підказках броні. У шарах спеціалізацію дає зовнішній предмет.'
  ]),
  ('en_us','Gunsmithing',[
   'Right-click the gunsmith table with an empty main hand to inspect the selected part; sneak-right-click to cycle. Start with exactly one paper in your main hand and materials in your inventory.',
   'Keep the workpiece in your main hand. Cycle a TFC hammer, TFC chisel and Create sandpaper in the offhand. Right-click every 1–3 seconds. Late work loses one operation. Tools wear out; paid progress stays in the workpiece.',
   'Flintlocks and wheellocks each take 24 operations without a blueprint. Research needs a completed matching lock in inventory and consumes it together with three papers including the starting sheet, ink and precision lockwork. Research takes 18 operations; its reusable blueprint reduces later locks to six operations.',
   'Forge the Siegeworks Firearm Lock from a hot steel plate, two iron nuggets and copper. Matchlocks and precision lockwork require it. With a matching blueprint, make a flintlock or wheellock from Firearm Lock, a steel plate, a cogwheel and flint or pyrite. Thirty Years War muskets use matchlocks and pistols use wheellocks.',
   'Cuirassier armor resists bullets but is vulnerable to maces. Plate favors slashing protection, mail is vulnerable to bullets, and padding favors blunt protection. Tooltips show the exact modifiers; only the outer layer adds specialization.'
  ])]:
  entry={'name':title,'icon':NS+':gunsmith_table','category':'empirecraft_guidebook:metals','read_by_default':True,'sortnum':17,'pages':[{'type':'patchouli:text','text':t} for t in pages]}
  layout.fit({}, {'gunsmith':entry})
  put(f'assets/empirecraft_guidebook/patchouli_books/handbook/{locale}/entries/metals/gunsmith.json',entry)
 def forge(pattern,key,out):
  return {'type':'overgeared:forging','category':'misc','pattern':pattern,'key':{k:({'type':'tfc:heatable','ingredient':{'item':v},'min_temp':900} if v=='overgeared:steel_plate' else {'item':v}) for k,v in key.items()},'result':{'item':out},'tier':'iron','hammering':5,'has_quality':True,'needs_minigame':True,'has_polishing':False,'need_quenching':False,'requires_blueprint':False,'blueprint':['sword'],'quality_difficulty':'none','show_notification':True}
 recipes={}
 recipes['siegeworks:firearm_lock']=forge(['PN','NC'],dict(P='overgeared:steel_plate',N='minecraft:iron_nugget',C='minecraft:copper_ingot'),'siegeworks:firearm_lock')
 recipes['thirtyyearswar:knights_baroque_matchlock_musket']=forge(['BBL','PSP',' M '],dict(B='tfc_muskets:musket_barrel',L='empirecraft_casting:matchlock',P='overgeared:steel_plate',S='tfc_muskets:wooden_stock',M=NS+':lockwork'),'thirtyyearswar:baroque_matchlock_musket')
 recipes['thirtyyearswar:knights_baroque_wheellock_pistol']=forge(['BL','PS'],dict(B='tfc_muskets:musket_barrel',L=NS+':wheellock',P='overgeared:steel_plate',S='tfc_muskets:wooden_stock'),'thirtyyearswar:baroque_wheellock_pistol')
 script='// priority: -21000\n// Generated by forging-balance/gunsmith_resources.py; final pass after Thirty Years War.\nconst EC_GUNSMITH_RECIPES = '+json.dumps(recipes,ensure_ascii=False,indent=2)+'''\nServerEvents.recipes(event => {
  // KubeJS remove filters original recipes; earlier scripts' added recipes need explicit removal.
  const retiredOutputs = ['tfc_muskets:musket_barrel','tfc_muskets:wooden_stock','empirecraft_casting:matchlock','empirecraft_forging:wheellock','empirecraft_forging:flintlock_blueprint','empirecraft_forging:wheellock_blueprint']
  Object.keys(EC_GUNSMITH_RECIPES).forEach(id => retiredOutputs.push(EC_GUNSMITH_RECIPES[id].result.item))
  event.addedRecipes.forEach(recipe => {
    const data = JSON.parse(recipe.json.toString())
    const result = data.result
    const output = typeof result === 'string' ? result : result ? result.item : ''
    if (retiredOutputs.indexOf(output) >= 0) recipe.remove()
  })
  retiredOutputs.forEach(output => event.remove({output:output}))
  Object.keys(EC_GUNSMITH_RECIPES).forEach(id => {
    event.remove({output:EC_GUNSMITH_RECIPES[id].result.item})
    event.custom(EC_GUNSMITH_RECIPES[id]).id('empirecraft_forging:assembly/' + id.replace(':','/'))
  })
  event.shaped('empirecraft_forging:gunsmith_table',['IPI','WCW','W W'],{
    I:'minecraft:iron_ingot',P:'overgeared:steel_plate',W:'minecraft:spruce_planks',C:'minecraft:crafting_table'
  }).id('empirecraft_forging:gunsmith_table')
})
'''
 for base in [REPO/'kubejs',REPO/'server/kubejs']:
  (base/'server_scripts/zzzzzzzzzzzzz_empirecraft_gunsmith.js').write_text(script)
if __name__=='__main__':generate()
