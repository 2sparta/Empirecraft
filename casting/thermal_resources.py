"""Temperature-based metalworking and a cheaper matchlock weapon route."""
import json,zipfile
from pathlib import Path
ID='empirecraft_casting'
def generate(ref):
 files={};recipes={}
 def add(path,value):files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
 with zipfile.ZipFile(next((Path(ref)/'mods').glob('TerraFirmaCraft-*.jar'))) as z:
  metals={n.rsplit('/',1)[-1][:-5]:json.loads(z.read(n)) for n in z.namelist() if n.startswith('data/tfc/tfc/metals/') and n.endswith('.json')}
  properties={m:dict(working=round(v['melt_temperature']*.6),welding=round(v['melt_temperature']*.8),capacity=round(v['specific_heat_capacity']*333.333,3),melting=v['melt_temperature']) for m,v in metals.items()}
 legacy={'overgeared:heated_'+m+'_ingot':i for m,i in {'iron':'minecraft:iron_ingot','copper':'minecraft:copper_ingot','steel':'overgeared:steel_ingot','silver':'magistuarmory:silver_ingot'}.items()}
 add('thermal.json',dict(metals=properties,legacy=legacy))
 for tag in ['heated_metals','hot_items']:add('data/overgeared/tags/items/'+tag+'.json',dict(replace=True,values=[]))
 def recipe(name,value):recipes[ID+':'+name]=value;add('data/'+ID+'/recipes/'+name+'.json',value)
 def forge(pattern,key,result):return dict(type='overgeared:forging',category='tool_heads',tier='stone',pattern=pattern,key=key,result=dict(item=result),hammering=3,has_quality=False,needs_minigame=False,requires_blueprint=False,quality_difficulty='none',need_quenching=False)
 recipe('arquebus/barrel_blank',forge(['II','P '],{'I':{'item':'minecraft:iron_ingot'},'P':{'item':'create:iron_sheet'}},ID+':arquebus_barrel_blank'))
 recipe('arquebus/barrel',dict(type='create:cutting',ingredients=[dict(item=ID+':arquebus_barrel_blank')],results=[dict(item=ID+':arquebus_barrel')],processingTime=120))
 recipe('arquebus/matchlock',forge(['NSN',' I ',' R '],{'N':{'item':'minecraft:iron_nugget'},'S':{'item':'minecraft:string'},'I':{'item':'create:iron_sheet'},'R':{'item':'minecraft:stick'}},ID+':matchlock'))
 weapon=forge([' B','IM',' S'],{'B':{'item':ID+':arquebus_barrel'},'I':{'item':'create:iron_sheet'},'M':{'item':ID+':matchlock'},'S':{'item':'tfc_muskets:wooden_stock'}},'medieval_boomsticks:arquebus');weapon.update(has_quality=True,needs_minigame=True,blueprint=['sword']);recipe('arquebus/assembly',weapon)
 recipe('arquebus/ammunition',dict(type='minecraft:crafting_shapeless',ingredients=[dict(item='tfc_muskets:bullet'),dict(item='tfc_muskets:bullet'),dict(item='tfc_muskets:gunpowder_pinch'),dict(item='minecraft:paper')],result=dict(item='medieval_boomsticks:round_ball',count=4)))
 for item,texture in [('arquebus_barrel_blank','iron_ingot'),('arquebus_barrel','iron_ingot'),('matchlock','flint')]:add('assets/'+ID+'/models/item/'+item+'.json',dict(parent='minecraft:item/generated',textures=dict(layer0='minecraft:item/'+texture)))
 for locale,names in {'uk_ua':['Заготовка ствола аркебузи','Ствол аркебузи','Ґнотовий замок'],'en_us':['Arquebus Barrel Blank','Arquebus Barrel','Matchlock'],'ru_ru':['Заготовка ствола аркебузы','Ствол аркебузы','Фитильный замок']}.items():
  add('assets/'+ID+'/lang/'+locale+'.json',dict(zip(['item.'+ID+'.'+n for n in ['arquebus_barrel_blank','arquebus_barrel','matchlock']],names)))
 import layout
 guides={
 'uk_ua':('Нагрівання та щипці',[
 'Злитки більше не мають окремих «гарячих» версій Overgeared. Звичайний злиток нагрівається за системою TFC. Температура зберігається з предметом і природно спадає; її видно в підказці.',
 'Повторний нагрів злитків у звичайній або плавильній печі дають теплий звичайний метал. Горн TFC також працює. Металеві компоненти рецептів ковадла Overgeared повинні мати робочу температуру свого металу; точну вимогу показує JEI.',
 'Гарячі предмети від 80 °C в інвентарі, руках або на курсорі завдають 1 одиницю шкоди щосекунди. Тримайте придатні щипці Overgeared у головній або другій руці. Саме лежання щипців в інвентарі не захищає.',
 'Під час утримання гарячих предметів щипці витрачають 1 одиницю міцності кожні 2 секунди. Зламані щипці не захищають. Творчий режим і вогнестійкість захищають від опіків.',
 'Кинутий у воду гарячий предмет одразу охолоджується. Зберігаються кількість, якість, назва й зачарування. Сухі предмети охолоджуються звичайним способом TFC. Гарячий результат ковки успадковує температуру металу.',
 'Старі гарячі злитки Overgeared автоматично замінюються звичайними нагрітими злитками в інвентарі чи після викидання. Якість і власні назви зберігаються.',
 'Аркебуза дешевша за мушкет: викуйте заготовку з двох гарячих залізних злитків і залізного листа Create. Обробіть її пилкою Create, щоб отримати один ствол.',
 'Для ґнотового замка потрібні два залізні самородки, залізний лист, нитка й палиця. Зберіть аркебузу на ковадлі зі ствола, замка, ще одного залізного листа й дерев’яного ложа TFC Muskets. Ковальська мінігра визначає якість зброї.',
 'Заряди аркебузи: дві готові кулі TFC Muskets, одна щіпка пороху й один аркуш паперу дають 4 круглі заряди Medieval Boomsticks. Кулі виготовляються чинним шляхом через металеві заготовки й обробку. Мушкетний патрон і далі коштує одну кулю, щіпку й папір.']),
 'en_us':('Heat and tongs',[
 'Ordinary ingots now use native TFC temperature instead of separate Overgeared heated item IDs. Reheating finished ingots in a furnace or blast furnace produce warm ordinary metal; the TFC forge remains available.',
 'Metal ingredients on the Overgeared anvil must reach their material working temperature. JEI shows the exact heated ingredient requirement. Forged outputs and assembled tools retain their input heat.',
 'At 80 C or above, items in your inventory, hands or cursor cause one damage per second. Hold usable Overgeared tongs in either hand to protect yourself. Tongs in a backpack do not protect you; broken tongs do not work.',
 'Tongs lose one durability every two seconds while handling hot items. Creative mode and Fire Resistance prevent burns. Dropping a hot item in water instantly cools it without losing count, quality, names or enchantments.',
 'Old Overgeared heated ingots migrate to warm ordinary ingots when carried or dropped. Dry items cool naturally through TFC.',
 'The cheaper arquebus uses one iron barrel: forge two warm iron ingots and one Create iron sheet into a blank, then cut it with a Create saw. Forge a matchlock from two iron nuggets, a sheet, string and a stick.',
 'Forge the arquebus from its barrel, matchlock, one more iron sheet and a TFC Muskets wooden stock. The forging minigame assigns weapon quality. The musket retains its more expensive steel parts.',
 'Two finished TFC Muskets bullets, one gunpowder pinch and one paper make four arquebus round-ball charges. The existing scrap and bullet finishing chain still applies. A musket cartridge continues to cost one bullet, pinch and paper.'])}
 for locale,(name,paragraphs) in guides.items():
  entry=dict(name=name,icon='overgeared:iron_tongs',category='empirecraft_guidebook:metals',sortnum=14,read_by_default=True,pages=[dict(type='patchouli:text',text=p) for p in paragraphs]);layout.fit({}, {'heat':entry});add('assets/empirecraft_guidebook/patchouli_books/handbook/'+locale+'/entries/metals/heat_and_arquebus.json',entry)
 for locale,text in {'uk_ua':'Сталь: розплавте білий чавун у тигелі при 1540 °C і додайте флюс у вхідні слоти. Один порошок потрібен на кожні розпочаті 1000 одиниць металу (до 10 злитків). У тигелі може залишатися сталь: флюс потрібен лише на чавун. Коли його достатньо, весь чавун стає сталлю без втрат. Флюс у тигелі не стає вапном і чекає на реакцію. Відлийте злитки формою або ливарним столом. Насичена сталь більше не є етапом виробництва.','en_us':'Steel: melt pig iron in a crucible at 1540 C and add flux to the input slots. Each started 1000 units (up to ten ingots) costs one flux powder. Existing steel may remain: flux is charged only for pig iron. With enough flux, all pig iron becomes steel without metal loss. Flux waits in the crucible and does not turn into lime. Cast it with a mold or casting table. High-carbon steel is no longer a production stage.'}.items():
  path='assets/'+ID+'/lang/'+locale+'.json';value=json.loads(files[path]);value['empirecraft_casting.refining']=text;add(path,value)
 add('thermal-recipes.json',recipes)
 return files
