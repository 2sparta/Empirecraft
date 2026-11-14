import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent;ID='empirecraft_casting'
def generate(ref):
 files={}
 def add(name,data):files[name]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
 recipe={'type':'minecraft:crafting_shaped','pattern':['BPB','SHS','FCF'],'key':{'B':{'item':'create:brass_sheet'},'P':{'item':'create:mechanical_press'},'S':{'item':'overgeared:steel_ingot'},'H':{'item':'minecraft:hopper'},'F':{'item':'tfc:fire_bricks'},'C':{'item':'create:andesite_casing'}},'result':{'item':ID+':casting_table'}}
 add('data/'+ID+'/recipes/casting_table.json',recipe)
 add('data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':[ID+':casting_table']})
 add('data/'+ID+'/loot_tables/blocks/casting_table.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':ID+':casting_table'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 add('data/'+ID+'/advancements/recipes/casting_table.json',{'criteria':{'has_crucible':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['tfc:crucible']}]}}},'rewards':{'recipes':[ID+':casting_table']}})
 textures={'base':'tfc:block/fire_bricks','frame':'minecraft:block/iron_block','plate':'create:block/brass_block','mold':'minecraft:block/deepslate_tiles','active':'minecraft:block/raw_copper_block','particle':'tfc:block/fire_bricks'}
 def cube(a,b,texture):return {'from':a,'to':b,'faces':{face:{'texture':'#'+texture} for face in ['up','down','north','south','east','west']}}
 for variant in ['empty','mold','active']:
  elements=[cube([0,0,0],[16,3,16],'base'),cube([0,3,0],[3,10,3],'base'),cube([13,3,0],[16,10,3],'base'),cube([0,3,13],[3,10,16],'base'),cube([13,3,13],[16,10,16],'base'),cube([0,10,0],[16,11,16],'frame'),cube([3,11,4],[13,12,14],'plate'),cube([6,11,0],[10,12,5],'plate'),cube([5,11,0],[6,13,5],'frame'),cube([10,11,0],[11,13,5],'frame')]
  if variant!='empty':elements += [cube([5,12,6],[6,13,13],'mold'),cube([10,12,6],[11,13,13],'mold'),cube([6,12,6],[10,13,7],'mold'),cube([6,12,12],[10,13,13],'mold'),cube([6,12,7],[10,12.5,12],'active' if variant=='active' else 'mold')]
  add('assets/'+ID+'/models/block/casting_table_'+variant+'.json',{'parent':'minecraft:block/block','textures':textures,'elements':elements})
 variants={}
 for face,rotation in [('north',0),('east',90),('south',180),('west',270)]:
  for mold in [False,True]:
   for lit in [False,True]:variants['facing='+face+',lit='+str(lit).lower()+',mold='+str(mold).lower()]={'model':ID+':block/casting_table_'+('active' if lit and mold else 'mold' if mold else 'empty'),'y':rotation}
 add('assets/'+ID+'/blockstates/casting_table.json',{'variants':variants});add('assets/'+ID+'/models/item/casting_table.json',{'parent':ID+':block/casting_table_empty'})
 text={
 'block.'+ID+'.casting_table':('Ливарний стіл','Ingot Casting Table','Литейный стол'),
 'tooltip.'+ID+'.beside':('Поставте впритул до тигля TFC.','Place directly beside a TFC crucible.','Поставьте вплотную к тиглю TFC.'),
 'tooltip.'+ID+'.automatic':('Форма всередині, злитки на виході — до 64.','Insert a mold; output ingots stack to 64.','Форма внутри, слитки на выходе — до 64.'),
 'tooltip.'+ID+'.retainer':('Кріплення захищає форму від руйнування.','The retainer protects the mold from breaking.','Крепление защищает форму от разрушения.'),
 'gui.'+ID+'.mold':('Форма','Mold','Форма'), 'gui.'+ID+'.output':('Злитки','Ingots','Слитки'),
 'gui.'+ID+'.no_mold':('Вставте випалену форму злитка.','Insert a fired ingot mold.','Вставьте обожжённую форму слитка.'),
 'gui.'+ID+'.no_crucible':('Поставте тигель поруч із жолобом.','Place a crucible beside the pouring channel.','Поставьте тигель рядом с желобом.'),
 'gui.'+ID+'.cold':('Нагрійте метал у тиглі до розплавлення.','Heat the crucible until its metal is molten.','Нагрейте металл в тигле до расплавления.'),
 'gui.'+ID+'.not_enough':('Бракує металу для повного злитка.','Not enough metal for one complete ingot.','Недостаточно металла для целого слитка.'),
 'gui.'+ID+'.full':('Вихід зайнятий. Заберіть злитки.','Output blocked. Collect the ingots.','Выход занят. Заберите слитки.'),
 'gui.'+ID+'.cooling':('Охолодження та автоматичне виймання…','Cooling and automatically releasing the ingot…','Охлаждение и автоматическое извлечение…'),
 'gui.'+ID+'.no_recipe':('Перевірте метал і вміст форми.','Check the metal and mold contents.','Проверьте металл и содержимое формы.'),
 'gui.'+ID+'.ready':('Злитки готові. Чекаю наступну порцію.','Ingots ready. Waiting for more metal.','Слитки готовы. Ожидание новой порции.')}
 text['item.'+ID+'.gold_javelin_head']=('Золоте вістря списа','Gold Javelin Head','Золотое остриё копья')
 text['item.'+ID+'.silver_javelin_head']=('Срібне вістря списа','Silver Javelin Head','Серебряное остриё копья')
 for kind,labels in {'javelin_head':('Вістря списа','Spearhead','Остриё копья'),'scythe_blade':('Лезо коси','Scythe blade','Лезвие косы'),'pickaxe_head':('Головка кайла','Pickaxe head','Головка кирки'),'axe_head':('Головка сокири','Axe head','Головка топора'),'shovel_head':('Головка лопати','Shovel head','Головка лопаты'),'hoe_head':('Головка мотики','Hoe head','Головка мотыги'),'hammer_head':('Головка молота','Hammer head','Головка молота'),'knife_blade':('Лезо ножа','Knife blade','Лезвие ножа'),'saw_blade':('Лезо пили','Saw blade','Лезвие пилы'),'chisel_head':('Головка долота','Chisel head','Головка долота')}.items():text['tooltype.overgeared.'+kind]=labels
 for i,locale in enumerate(['uk_ua','en_us','ru_ru']):add('assets/'+ID+'/lang/'+locale+'.json',{k:v[i] for k,v in text.items()})
 # Extend the installed handbook without replacing its JAR or earlier guide fixes.
 sys.path.insert(0,str(ROOT.parent/'handbook'));import layout
 guide={
 'uk_ua':('Ливарний стіл',[
  'Ливарний стіл автоматично розливає метал із тигля TFC, охолоджує його й виймає готові злитки. Плавлення руди та пропорції сплавів, як і раніше, готуються в тиглі.',
  'Поставте стіл на тій самій висоті впритул до боку тигля. Жолоб повернеться до сусіднього тигля. Відкрийте стіл правою кнопкою та вставте випалену звичайну або вогнетривку форму злитка.',
  'Одна порція використовує кількість металу з поточного рецепта TFC: зазвичай 100 одиниць. Охолодження триває 2 секунди. Кріплення зберігає форму цілою для наступних злитків.',
  'Готові холодні злитки складаються у виході до 64. Знизу або збоку їх можна забирати лійкою чи воронкою Create. Зверху автоматизація може вставити форму. Саму форму дістаньте вручну.',
  'Коли вихід заповнений, метал із тигля не витрачається. При іншому металі у виході спочатку заберіть попередні злитки. Холодний метал у тиглі не розливається.',
  'Якщо забрати форму під час охолодження або зламати стіл, метал залишиться у формі. Після завантаження чанка робота продовжиться. Не змішуйте різні метали в частково заповненій формі.',
  'Складний рецепт потребує двох латунних листів Create, механічного преса Create, двох злитків сталі Overgeared, лійки, двох блоків вогнетривкої цегли TFC й андезитового корпуса Create. Форму вставляють окремо.']),
 'en_us':('Ingot Casting Table',[
  'The table automatically pours molten TFC crucible metal, cools it and releases ingots. Smelting ores and preparing alloy ratios still happen in the crucible.',
  'Place it directly beside a crucible at the same height. Its channel turns toward the neighboring crucible. Open it and insert a fired normal or fire-clay ingot mold.',
  'Each ingot uses the current TFC recipe amount, normally 100 metal units. Cooling takes 2 seconds. The retainer protects the mold so it can be reused.',
  'Cold output ingots stack to 64. Hoppers or Create funnels can extract from below or the sides. Automation can insert a mold from above. Remove the mold manually.',
  'A full or incompatible output pauses casting without spending crucible metal. Cold crucible metal cannot be poured. Collect the previous metal before switching the output to another one.',
  'Removing the mold or breaking the table keeps its metal inside the mold item. Work resumes after reloading its chunk. Never mix different metals in a partly filled mold.',
  'The recipe requires two Create brass sheets, one mechanical press, two Overgeared steel ingots, one hopper, two TFC fire-brick blocks and one Create andesite casing. Insert the mold separately.'])}
 for locale,(name,paragraphs) in guide.items():
  entry={'name':name,'icon':ID+':casting_table','category':'empirecraft_guidebook:metals','sortnum':12,'read_by_default':True,'pages':[{'type':'patchouli:text','text':p} for p in paragraphs]};layout.fit({}, {'metals/automatic_casting':entry});add('assets/empirecraft_guidebook/patchouli_books/handbook/'+locale+'/entries/metals/automatic_casting.json',entry)
 from metallurgy_resources import generate as metallurgy
 files.update(metallurgy(ref))
 return files
