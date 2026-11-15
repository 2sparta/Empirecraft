import json,zipfile
from pathlib import Path
ID='empirecraft_casting'
def generate(ref):
 files={};groups={};content={}
 def add(n,v):files[n]=(json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
 grains=['barley','maize','oat','rice','rye','wheat']
 for kind in ['flour','dough']:
  values=['tfc:food/'+g+'_'+kind for g in grains];add('data/'+ID+'/tags/items/food/'+kind+'.json',dict(replace=True,values=values))
  for tag in [kind,kind+'/wheat']:add('data/forge/tags/items/'+tag+'.json',dict(replace=True,values=values))
  for old in (['create:dough','farmersdelight:wheat_dough'] if kind=='dough' else ['create:wheat_flour']):groups[old]={'tag':ID+':food/'+kind}
 with zipfile.ZipFile(next((Path(ref)/'mods').glob('TerraFirmaCraft-*.jar'))) as z:
  foods={n.split('/models/item/')[1][:-5] for n in z.namelist() if n.startswith('assets/tfc/models/item/food/') and n.endswith('.json')}
  containers=json.loads(z.read('data/tfc/tags/items/fluid_item_ingredient_empty_containers.json'));containers['values']=[v for v in containers['values'] if not any(m in str(v) for m in ['red_steel','blue_steel'])];containers['replace']=True;add('data/tfc/tags/items/fluid_item_ingredient_empty_containers.json',containers)
 def equiv(vanilla,tfc):
  ids=['tfc:food/'+s for s in tfc if 'food/'+s in foods]
  if ids:groups[vanilla]=[{'item':vanilla}]+[{'item':v} for v in ids]
 equiv('minecraft:bread',[g+'_bread' for g in grains])
 for food in ['beef','chicken','mutton','rabbit','cod','salmon','tropical_fish','carrot','potato','beetroot','pumpkin','melon_slice','sweet_berries','glow_berries','cooked_beef','cooked_chicken','cooked_mutton','cooked_rabbit','cooked_cod','cooked_salmon']:
  equiv('minecraft:'+food,[food])
 equiv('minecraft:porkchop',['pork']);equiv('minecraft:cooked_porkchop',['cooked_pork']);equiv('minecraft:apple',['red_apple','green_apple'])
 for food in ['cabbage','onion','tomato','rice']:equiv('farmersdelight:'+food,[food])
 equiv('farmersdelight:rice',['rice_grain']);equiv('farmersdelight:cooked_rice',['cooked_rice']);equiv('farmersdelight:fried_egg',['cooked_egg'])
 fish=['cod','salmon','tropical_fish','bluegill','rainbow_trout','lake_trout','largemouth_bass','smallmouth_bass','crappie','shellfish','calamari']
 for tag,ids in [('raw_fishes',fish),('cooked_fishes',['cooked_'+v for v in fish]),('cooked_eggs',['cooked_egg','boiled_egg']),('cheese',['cheese']),('fruits/berries',[v.split('/')[1] for v in foods if v.endswith('berry')])]:
  add('data/forge/tags/items/'+tag+'.json',dict(replace=False,values=['tfc:food/'+v for v in ids if 'food/'+v in foods]))
 for source,values in list(groups.items()):
  if isinstance(values,list):
   tag='food/compat/'+source.replace(':','_');add('data/'+ID+'/tags/items/'+tag+'.json',dict(replace=True,values=[v['item'] for v in values]));groups[source]={'tag':ID+':'+tag}
 add('food-policy.json',dict(ingredients=groups))
 for g in grains:
  add('data/'+ID+'/recipes/food/milling_'+g+'.json',dict(type='create:milling',ingredients=[dict(item='tfc:food/'+g+'_grain')],results=[dict(item='tfc:food/'+g+'_flour')],processingTime=100))
  add('data/'+ID+'/recipes/food/baking_'+g+'.json',dict(type='minecraft:smelting',ingredient=dict(item='tfc:food/'+g+'_dough'),result='tfc:food/'+g+'_bread',experience=.1,cookingtime=200))
 add('data/tfc/tags/fluids/molten_metals.json',dict(replace=False,values=[ID+':andesite_melt',ID+':andesite_alloy']))
 # Andesite is a separate molten mineral, mixed with 10% cast iron or zinc.
 for material in ['andesite_melt','andesite_alloy']:
  add('data/'+ID+'/tfc/metals/'+material+'.json',dict(tier=1,fluid=ID+':'+material,melt_temperature=1200,specific_heat_capacity=.01,ingots=dict(item='minecraft:andesite' if material=='andesite_melt' else 'create:andesite_alloy')))
 add('data/'+ID+'/tfc/item_heats/andesite.json',dict(ingredient=[dict(item='minecraft:andesite'),dict(item='tfc:rock/raw/andesite')],heat_capacity=3.0))
 content[ID+':heating/andesite']=dict(type='tfc:heating',ingredient=[dict(item='minecraft:andesite'),dict(item='tfc:rock/raw/andesite')],result_fluid=dict(fluid=ID+':andesite_melt',amount=90),temperature=1200)
 for metal,item in [('cast_iron','minecraft:iron_nugget'),('zinc','create:zinc_nugget')]:
  add('data/'+ID+'/tfc/item_heats/nugget_'+metal+'.json',dict(ingredient=dict(item=item),heat_capacity=.3))
  content[ID+':heating/nugget_'+metal]=dict(type='tfc:heating',ingredient=dict(item=item),result_fluid=dict(fluid='tfc:metal/'+metal,amount=10),temperature=1535 if metal=='cast_iron' else 420)
  content[ID+':alloy/andesite_'+metal]=dict(type='tfc:alloy',result=ID+':andesite_alloy',contents=[dict(metal=ID+':andesite_melt',min=.9,max=.9),dict(metal='tfc:'+metal,min=.1,max=.1)])
 content[ID+':casting/andesite_alloy']=dict(type='tfc:casting',mold=dict(item='tfc:ceramic/ingot_mold'),fluid=dict(ingredient=ID+':andesite_alloy',amount=100),result=dict(item='create:andesite_alloy'),break_chance=.1)
 content[ID+':casting/fire_andesite_alloy']=dict(content[ID+':casting/andesite_alloy']);content[ID+':casting/fire_andesite_alloy']['mold']=dict(item='tfc:ceramic/fire_ingot_mold')
 content[ID+':gunpowder']=dict(type='minecraft:crafting_shapeless',ingredients=[dict(item='tfc:powder/saltpeter')]*3+[dict(item='tfc:powder/sulfur'),dict(item='minecraft:charcoal')],result=dict(item='minecraft:gunpowder',count=6))
 # Authoritative cross-mod equipment/ammunition recipes, applied after legacy policy.
 content[ID+':assembly/boomsticks/javelin']=dict(type=ID+':quality_shaped',pattern=['TH','S '],key={'T':dict(item='minecraft:string'),'H':dict(item='tfc:metal/javelin_head/wrought_iron'),'S':dict(item='minecraft:stick')},result=dict(item='medieval_boomsticks:javelin'))
 content[ID+':assembly/boomsticks/throwing_knife']=dict(type=ID+':quality_shaped',pattern=[' H ','S H',' S '],key={'H':dict(item='tfc:metal/knife_blade/wrought_iron'),'S':dict(item='minecraft:stick')},result=dict(item='medieval_boomsticks:iron_throwing_knife',count=2))
 content[ID+':ammo/musket_cartridge']=dict(type='minecraft:crafting_shapeless',ingredients=[dict(item='tfc_muskets:bullet'),dict(item='tfc_muskets:gunpowder_pinch'),dict(item='minecraft:paper')],result=dict(item='musketmod:cartridge'))
 for name,value in content.items():add('data/'+ID+'/recipes/'+name.split(':')[1]+'.json',value)
 add('content-recipes.json',content)
 for locale,names in {'uk_ua':['Розплав андезиту','Розплав андезитового сплаву'],'en_us':['Molten Andesite','Molten Andesite Alloy']}.items():
  add('assets/empirecraft_crucible/lang/'+locale+'.json',{'fluid_type.'+ID+'.'+key:label for key,label in zip(['andesite_melt','andesite_alloy'],names)})
 import layout
 for locale,title,text in [('uk_ua','Борошно й тісто','Усі шість видів борошна й тіста TFC приймаються в рецептах їжі інших модів. Перемелюйте зерно у жорні TFC або млині Create. Замішуйте відповідне тісто за рецептом TFC з водою. Його можна різати на пасту Farmer’s Delight, використовувати у випічці й випікати у звичайній печі. Варіанти тіста самого TFC зберігають власні рецепти. Старе тісто Create й Farmer’s Delight автоматично переходить у пшеничне TFC при отриманні в інвентар. Відповідні хліб, м’ясо, риба, рис, яйця та овочі TFC також приймаються в інших рецептах. Сирі продукти й готові залишаються окремими.'),('en_us','Flour and dough','All six TFC flour and dough varieties work in other mods’ food recipes. Grind grain in a TFC quern or Create millstone. Mix the corresponding TFC dough with water, cut it into Farmer’s Delight pasta, use it in baking or bake it in a furnace. Existing Create and Farmer’s Delight dough migrates to TFC wheat dough when carried. Equivalent TFC bread, meat, fish, rice, eggs and vegetables work in matching recipes. Raw and cooked ingredients remain separate.')]:
  entry=dict(name=title,icon='tfc:food/maize_flour',category='empirecraft_guidebook:food',read_by_default=True,pages=[dict(type='patchouli:text',text=text)]);layout.fit({}, {'food':entry});add('assets/empirecraft_guidebook/patchouli_books/handbook/'+locale+'/entries/food/shared_ingredients.json',entry)
 for locale,title,text in [('uk_ua','Андезитовий сплав','Нагрійте андезит у тиглі до 1200 °C. Один блок дає 90 одиниць розплаву. Додайте 10 одиниць розплавленого заліза або цинку, тобто один самородок. Відлийте 100 одиниць суміші у форму злитка або ливарним столом: отримаєте один андезитовий сплав Create. Для залізного самородка потрібен жар 1535 °C, для цинкового — 420 °C. Руда й сирі метали більше не перетворюються на злитки у ванільних печах; вони плавляться через TFC. Готовий злиток у печі можна лише повторно нагріти. Сірка тепер походить із TFC. Для шести порохів змішайте три порошки селітри, порошок сірки TFC і деревне вугілля.'),('en_us','Andesite alloy','Melt andesite at 1200 C: one block yields 90 units. Add 10 units of cast iron or zinc from one nugget. Cast 100 units with an ingot mold or casting table to obtain one Create andesite alloy. Iron nuggets need 1535 C; zinc needs 420 C. Vanilla furnaces no longer turn ore or raw metal into ingots; finished ingots can still be reheated. Sulfur now comes from TFC. Three saltpeter powders, one TFC sulfur powder and charcoal produce six gunpowder.')]:
  entry=dict(name=title,icon='create:andesite_alloy',category='empirecraft_guidebook:metals',read_by_default=True,pages=[dict(type='patchouli:text',text=text)]);layout.fit({}, {'andesite':entry});add('assets/empirecraft_guidebook/patchouli_books/handbook/'+locale+'/entries/metals/andesite_alloy.json',entry)
 return files
