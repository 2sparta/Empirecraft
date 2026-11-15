"""Post-policy metal progression and quality-preserving weapon assembly."""
import json,re,zipfile
from pathlib import Path
ID='empirecraft_casting'
REMOVED={'bismuth_bronze','red_steel','blue_steel','black_bronze','high_carbon_red_steel','high_carbon_blue_steel','weak_red_steel','weak_blue_steel'}
def generate(ref):
 recipes={};assembly=[];forged=[];files={};matrix=[]
 def add(n,v):files[n]=(json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
 def recipe(name,value):recipes[name]=value;ns,path=name.split(':');add('data/'+ns+'/recipes/'+path+'.json',value)
 def forge(item,metal,amount,blueprint='sword',quality=True):
  ingot=canonical.get(metal,'tfc:metal/ingot/'+metal);temp=max(400,int(metals[metal]['melt_temperature']*.6));ingredient={'type':'tfc:heatable','ingredient':{'item':ingot},'min_temp':temp}
  count=max(1,(amount+99)//100);pattern=['I'*min(count,3)] if count<=3 else ['II','II']
  return {'type':'overgeared:forging','category':'tool_heads','tier':'iron' if metals[metal]['tier']>=3 else 'stone','pattern':pattern,'key':{'I':ingredient},'result':{'item':item},'hammering':5,'has_quality':quality,'needs_minigame':quality,'has_polishing':False,'need_quenching':False,'requires_blueprint':quality,'blueprint':[blueprint] if quality else [],'minimum_quality':'poor','quality_difficulty':'none','show_notification':True}
 canonical=json.loads((Path(ref)/'tfc-integration/decisions.json').read_text())['canonical_metals']
 with zipfile.ZipFile(next((Path(ref)/'mods').glob('TerraFirmaCraft-*.jar'))) as z:
  metals={n.rsplit('/',1)[-1][:-5]:json.loads(z.read(n)) for n in z.namelist() if n.startswith('data/tfc/tfc/metals/') and n.endswith('.json')}
  heat_templates={m:json.loads(z.read('data/tfc/tfc/item_heats/metal/'+m+'_ingot.json')) for m in ['gold','silver']}
  heads={}
  for name in z.namelist():
   match=re.fullmatch('assets/tfc/models/item/metal/(javelin_head|scythe_blade|pickaxe_head|axe_head|shovel_head|hoe_head|hammer_head|knife_blade|saw_blade|chisel_head|tuyere)/([^/]+).json',name)
   if not match:continue
   kind,metal=match.groups()
   if metal in REMOVED:continue
   item='tfc:metal/'+kind+'/'+metal
   heatname='data/tfc/recipes/heating/metal/'+metal+'_'+kind+'.json';amount=json.loads(z.read(heatname)).get('result_fluid',{}).get('amount',100) if heatname in z.namelist() else 100
   spec=forge(item,metal,amount,kind,kind!='tuyere');recipe(ID+':forging/'+metal+'/'+kind,spec);forged.append(item);heads[(metal,kind)]=item
   matrix.append({'metal':metal,'part':kind,'item':item,'metal_units':amount,'quality':kind!='tuyere'})
  for name in z.namelist():
   if not name.startswith('data/tfc/recipes/') or not name.endswith('.json'):continue
   value=json.loads(z.read(name));result=value.get('result',{});result=result.get('stack',result) if isinstance(result,dict) else result;item=result.get('item','') if isinstance(result,dict) else result
   match=re.fullmatch('tfc:metal/(javelin|scythe|pickaxe|axe|shovel|hoe|hammer|knife|saw|chisel)/([^/]+)',item)
   if not match or match[2] in REMOVED:continue
   tool,metal=match.groups();kind={'javelin':'javelin_head','scythe':'scythe_blade','knife':'knife_blade','saw':'saw_blade'}.get(tool,tool+'_head');head=heads.get((metal,kind))
   if head:
    spec={'type':ID+':quality_shaped','pattern':['H','S'],'key':{'H':{'item':head},'S':{'tag':'forge:rods/wooden'}},'result':{'item':item}};recipe(ID+':assembly/tfc/'+metal+'/'+tool,spec);assembly.append(item)
  # Remove black-bronze dependency while retaining the surviving black-steel route.
  name='data/tfc/recipes/alloy/weak_steel.json';weak=json.loads(z.read(name));
  for component in weak['contents']:
   if component['metal']=='tfc:black_bronze':component['metal']='tfc:bronze'
  recipe('tfc:alloy/weak_steel',weak)
 for material in ['gold','silver']:
  item=ID+':'+material+'_javelin_head';heads[(material,'javelin_head')]=item;spec=forge(item,material,100,'javelin_head');recipe(ID+':forging/'+material+'/javelin_head',spec);forged.append(item)
  heat=heat_templates[material];heat['ingredient']={'item':item};add('data/'+ID+'/tfc/item_heats/'+material+'_javelin_head.json',heat)
  recipe(ID+':heating/'+material+'_javelin_head',{'type':'tfc:heating','ingredient':{'item':item},'result_fluid':{'fluid':'tfc:metal/'+material,'amount':100},'temperature':metals[material]['melt_temperature']})
  add('assets/'+ID+'/models/item/'+material+'_javelin_head.json',{'parent':'minecraft:item/generated','textures':{'layer0':'tfc:item/metal/javelin_head/'+('copper' if material=='gold' else 'wrought_iron')}})
 with zipfile.ZipFile(next((Path(ref)/'mods').glob('epic-knights-*.jar'))) as z:
  for name in z.namelist():
   if '/recipes/' not in name or not name.endswith('.json') or not any(w in name.rsplit('/',1)[-1] for w in ['spear','pike','lance','ahlspiess','ranseur','glaive','guisarme','concavehalberd','scythe']):continue
   value=json.loads(z.read(name));result=value.get('result',{});item=result.get('item','') if isinstance(result,dict) else result;path=item.split(':')[-1]
   if not any(word in path for word in ['spear','pike','lance','ahlspiess','ranseur','glaive','guisarme','concavehalberd','scythe']):continue
   material=next((m for m in ['copper','iron','bronze','gold','silver','steel'] if path.startswith(m+'_')),None)
   if not material or 'pattern' not in value:continue
   metal={'iron':'wrought_iron'}.get(material,material);kind='scythe_blade' if any(k in path for k in ['glaive','guisarme','concavehalberd','scythe']) else 'javelin_head';head=heads.get((metal,kind)) or heads.get((metal,'javelin_head'))
   if not head:continue
   key=value['key'];symbol=next((k for k,v in key.items() if any(x in json.dumps(v) for x in ['/ingots/','ingots/','ingot','plate','sheet']) and any(x in json.dumps(v) for x in [material,metal])),None)
   if not symbol:continue
   pattern=list(value['pattern']);changed=False
   for i,row in enumerate(pattern):
    if symbol in row:pattern[i]=row.replace(symbol,'Z',1);changed=True;break
   if not changed:continue
   key['Z']={'item':head}
   if not any(symbol in row for row in pattern):key.pop(symbol)
   # These three native spear recipes collide after their metal is replaced by one head.
   # Additional steel bindings distinguish every mirrored crafting layout.
   if item in {'darkagesarmory:steel_kingston_spear','magistuarmoryaddon:steel_boar_spear'}:
    pattern=['NZ','PN',' N' if item.startswith('darkagesarmory:') else 'NN'];key={'N':{'tag':'forge:nuggets/steel'},'P':{'tag':'magistuarmory:poles'},'Z':{'item':head}}
   spec={'type':ID+':quality_shaped','pattern':pattern,'key':key,'result':result};recipe(ID+':assembly/'+item.replace(':','/'),spec);assembly.append(item)
 recipe(ID+':refining/high_carbon_steel',{'type':'overgeared:forging','category':'misc','tier':'iron','pattern':['I'],'key':{'I':{'type':'tfc:heatable','ingredient':{'item':'tfc:metal/ingot/pig_iron'},'min_temp':900}},'result':{'item':'tfc:metal/ingot/high_carbon_steel'},'hammering':4,'has_quality':False,'needs_minigame':False,'requires_blueprint':False,'quality_difficulty':'none','show_notification':True})
 steel=recipes[ID+':refining/high_carbon_steel'].copy();steel['key']={'I':{'type':'tfc:heatable','ingredient':{'item':'tfc:metal/ingot/high_carbon_steel'},'min_temp':900}};steel['result']={'item':'overgeared:steel_ingot'};recipe(ID+':refining/steel',steel)
 black=steel.copy();black['key']={'I':{'type':'tfc:heatable','ingredient':{'item':'tfc:metal/ingot/high_carbon_black_steel'},'min_temp':900}};black['result']={'item':'tfc:metal/ingot/black_steel'};recipe(ID+':refining/black_steel',black)
 recipe('tfc:heating/metal/high_carbon_steel_ingot',{'type':'tfc:heating','ingredient':{'item':'tfc:metal/ingot/high_carbon_steel'},'result_fluid':{'fluid':'tfc:metal/high_carbon_steel','amount':100},'temperature':1540})
 recipe('tfc:crafting/blast_furnace',{'type':'minecraft:crafting_shaped','pattern':['FIF','BCB','FIF'],'key':{'F':{'item':'tfc:fire_bricks'},'I':{'item':'minecraft:iron_ingot'},'B':{'item':'createbigcannons:bronze_ingot'},'C':{'item':'tfc:crucible'}},'result':{'item':'tfc:blast_furnace'}})
 add('data/'+ID+'/blueprint_tooltypes/parts.json',{'tooltypes':sorted({r['blueprint'][0] for r in recipes.values() if r.get('requires_blueprint') and r.get('blueprint')})})
 # Remove obsolete alloy articles from navigation, retaining saved entry IDs.
 add('data/'+ID+'/advancements/retired.json',{'criteria':{'never':{'trigger':'minecraft:impossible'}}})
 book=next((Path(ref)/'handbook/dist').glob('empirecraft-handbook-1.0.4.jar'))
 with zipfile.ZipFile(book) as z:
  for name in z.namelist():
   if '/entries/metals/alloy_' not in name or not name.endswith('.json'):continue
   entry=json.loads(z.read(name))
   if any(word in name for word in REMOVED):entry['secret']=True;entry['advancement']=ID+':retired';add(name,entry)
   elif 'weak_steel' in name:
    for page in entry.get('pages',[]):
     if 'text' in page:
      for old,new in [('Темна бронза','Бронза'),('темна бронза','бронза'),('Black bronze','Bronze'),('black bronze','bronze')]:page['text']=page['text'].replace(old,new)
    add(name,entry)
 import layout
 instructions={
 'uk_ua':('Сталь, ковка і подача',[
  'Тигель приймає сировину з лійки згори й воронки Create збоку. Вхідні слоти залишаються по одному предмету: це зберігає правильну кількість металу. Збоку можна витягувати лише предмет із вихідного слота тигля.',
  'Паливні слоти ковальського горна вміщують по 16 вугілля. Кожне горіння витрачає одну одиницю. Один рівень доменної печі вміщує до 16 порцій руди, флюсу й палива. Вогнетривкі стінки та листове обшиття залишаються потрібними.',
  'Доменна піч тепер виготовляється до першої сталі: чотири блоки вогнетривкої цегли, два злитки заліза, два злитки бронзи CBC та тигель. Фурму викуйте з чотирьох гарячих злитків відповідного металу.',
  'Для першої сталі подайте сире залізо, флюс і паливо в сформовану доменну піч. Підтримуйте потрібний жар і наддув. Отриманий рідкий чавун відлийте у злиток.',
  'На ковадлі Overgeared куйте гарячий чавун від 900 °C у високовуглецевий злиток. Його можна або довести ковкою до звичайної сталі Overgeared, або розплавити при 1540 °C у рідку високовуглецеву сталь.',
  'Чорна сталь: у тиглі змішайте 50–70% сталі, 15–25% нікелю й 15–25% звичайної бронзи. Наприклад, 600 одиниць сталі, 200 нікелю й 200 бронзи. Це дає слабку сталь.',
  'Відлийте слабку сталь, потім переплавте її окремо з рівною кількістю чавуну: 50% слабкої сталі й 50% чавуну. Отриману високовуглецеву чорну сталь відлийте й доведіть ковкою від 900 °C до чорної сталі.',
  'Вістря списів, леза кіс та інші деталі куйте з нагрітих злитків. У кресленні Overgeared виберіть відповідний тип деталі й поставте креслення у слот ковадла. Для різних металів використовується той самий тип креслення.',
  'Якість деталі визначає мінігра ковки й чинна система креслень. Вона переноситься на готові спис, піку, косу чи інструмент при складанні. Якщо є кілька деталей із якістю, результат бере найнижчу з них.',
  'Темну бронзу, червону та воронену сталь вилучено з нових рецептів і JEI. Чорна сталь залишається. Початковий маршрут Overgeared із заліза й вугілля більше не виробляє сталь; перепакування вже наявної сталі збережене.']),
 'en_us':('Steel and forged parts',[
  'Feed the crucible from a hopper above or a Create funnel on a side. Input slots stay limited to one item so each smelting operation conserves metal. Side extraction accesses only its output slot.',
  'Each charcoal-forge fuel slot holds 16 items and burns one at a time. Each blast-furnace chimney level supports 16 ore, flux and fuel portions. The normal insulation and sheet cladding are still required.',
  'Build the blast furnace before your first steel: four TFC fire-brick blocks, two iron ingots, two CBC bronze ingots and a crucible. Forge a tuyere from four heated ingots of its material.',
  'Smelt raw iron with flux and fuel in a formed, heated blast furnace with air supply. Cast its liquid pig iron. Forge heated pig iron at 900 C into a high-carbon steel ingot.',
  'Forge the high-carbon ingot at 900 C into normal Overgeared steel, or melt it at 1540 C into actual liquid high-carbon steel.',
  'For black steel, mix 50–70% steel, 15–25% nickel and 15–25% ordinary bronze. A 600/200/200-unit batch makes weak steel. Cast it first, then remelt it with an equal amount of pig iron.',
  'A 50/50 mix of remelted weak steel and pig iron produces high-carbon black steel. Cast it and forge it at 900 C into black steel.',
  'Use the corresponding part blueprint in the Overgeared anvil for spearheads, scythe blades and other heads. The same part blueprint works across metals. Heat each material before forging.',
  'The normal forging minigame and blueprint progression assign quality. Assembly preserves it on the finished weapon or tool; multiple qualified parts use the lowest quality.',
  'Black bronze, red steel and blue steel have no new recipes and are hidden from JEI. Black steel remains available. The ordinary iron/coal Overgeared steel shortcut is removed; existing steel can still be repacked.'])}
 for locale,(title,paragraphs) in instructions.items():
  entry={'name':title,'icon':'overgeared:steel_ingot','category':'empirecraft_guidebook:metals','sortnum':13,'read_by_default':True,'pages':[{'type':'patchouli:text','text':text} for text in paragraphs]};layout.fit({}, {'metallurgy':entry});add('assets/empirecraft_guidebook/patchouli_books/handbook/'+locale+'/entries/metals/metallurgy.json',entry)
 data={'recipes':recipes,'assembly_outputs':sorted(set(assembly)),'forged_outputs':sorted(set(forged))};add('metallurgy.json',data);add('META-INF/metallurgy-matrix.json',matrix)
 return files
