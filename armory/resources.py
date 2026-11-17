"""Native recipes, original pixel textures and a concise in-game workshop manual."""
import io,json,zipfile
from pathlib import Path
from PIL import Image,ImageDraw
ID='empirecraft_armory'
FORMS={'front':2,'back':2,'shoulders':1,'arms':1,'waist':1,'dome':3,'nape':1,'visor':1,'thighs':3,'knees':1,'greaves':2,'feet':2,'ankles':1,'mail_panel':2,'mail_sleeves':2,'lamellar_panel':2,'brigandine_panel':2,'fluted_front':3,'fluted_back':3,'joints':1}
METALS={'iron':('minecraft:iron_ingot','wrought_iron',1535,(181,188,188)),'steel':('overgeared:steel_ingot','steel',1540,(112,131,142)),'bronze':('createbigcannons:bronze_ingot','bronze',950,(179,123,61)),'copper':('minecraft:copper_ingot','copper',1080,(202,115,71)),'black_steel':('tfc:metal/ingot/black_steel','black_steel',1485,(65,73,82)),'silver':('magistuarmory:silver_ingot','silver',961,(205,218,225)),'gold':('minecraft:gold_ingot','gold',1060,(229,183,57))}
NAMES={'front':('Нагрудна пластина','Breastplate plate'),'back':('Спинна пластина','Backplate plate'),'shoulders':('Пара наплічників','Pauldron pair'),'arms':('Пара наручів','Vambrace pair'),'waist':('Поясні пластини','Fauld plates'),'dome':('Купол шолома','Helmet dome'),'nape':('Потиличний захист','Nape guard'),'visor':('Забрало','Visor'),'thighs':('Пара набедреників','Cuisse pair'),'knees':('Пара наколінників','Poleyn pair'),'greaves':('Пара наголінників','Greave pair'),'feet':('Пара латних черевиків','Sabaton pair'),'ankles':('Захист щиколоток','Ankle guards'),'mail_panel':('Кольчужне полотно','Mail panel'),'mail_sleeves':('Кольчужні рукави','Mail sleeves'),'lamellar_panel':('Ламелярна секція','Lamellar panel'),'brigandine_panel':('Секція бригантини','Brigandine panel'),'fluted_front':('Рифлена нагрудна пластина','Fluted breastplate plate'),'fluted_back':('Рифлена спинна пластина','Fluted backplate plate'),'joints':('Шарнірні з’єднання','Articulated joints')}
def generate():
 files={}
 def add(path,value):files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
 def recipe(name,value):add('data/'+ID+'/recipes/'+name+'.json',value)
 def model(name,texture=None):add('assets/'+ID+'/models/item/'+name+'.json',{'parent':'minecraft:item/generated','textures':{'layer0':ID+':item/'+(texture or name)}})
 for form,cost in FORMS.items():
  model('pattern_'+form)
  recipe('patterns/'+form,dict(type='minecraft:stonecutting',ingredient=dict(item=ID+':pattern_blank'),result=ID+':pattern_'+form,count=1))
 for metal,(ingot,fluid,melt,color) in METALS.items():
  for form,cost in FORMS.items():
   item=metal+'_'+form;model(item)
   source=ingot;number=cost
   if metal in ['steel','iron'] and form.startswith('mail_'):source='magistuarmory:steel_chainmail' if metal=='steel' else 'antiquelegacy:iron_chainmail';number=cost*2
   if metal in ['steel','iron','bronze'] and form=='lamellar_panel':source='magistuarmory:lamellar_rows' if metal=='steel' else 'antiquelegacy:'+metal+'_lamellar_rows';number=cost*2
   pattern=['I'*min(3,number)]
   if number>3:pattern.append('I'*(number-3))
   pattern.append('T')
   width=max(map(len,pattern));pattern=[row.ljust(width) for row in pattern]
   slots=['helmet'] if form in ['dome','nape','visor'] else ['leggings'] if form in ['thighs','knees','greaves'] else ['boots'] if form in ['feet','ankles'] else ['helmet','chestplate','leggings','boots'] if form in ['mail_panel','mail_sleeves','lamellar_panel','brigandine_panel'] else ['chestplate']
   recipe('parts/'+item,dict(type='overgeared:forging',category='tool_heads',tier='iron',pattern=pattern,key={'I':{'item':source},'T':{'item':ID+':pattern_'+form}},result={'item':ID+':'+item},hammering=3 if cost<3 else 4,has_quality=True,needs_minigame=True,blueprint=['armor',*slots],requires_blueprint=False,quality_difficulty='none',minimum_quality='poor',need_quenching=False,has_polishing=False))
   add('data/'+ID+'/tfc/item_heats/'+item+'.json',dict(ingredient=dict(item=ID+':'+item),heat_capacity=cost,forging_temperature=round(melt*.6),welding_temperature=round(melt*.8)))
   recipe('recycle/'+item,dict(type='tfc:heating',ingredient=dict(item=ID+':'+item),result_fluid=dict(fluid='tfc:metal/'+fluid,amount=cost*100),temperature=melt))
 for name in ['pattern_blank','rivets','buckle','padded_lining','leather_panel','unfinished_armor']:model(name)
 recipe('pattern_blank',dict(type='minecraft:crafting_shaped',pattern=['PP','WW'],key={'P':{'item':'minecraft:paper'},'W':{'tag':'minecraft:planks'}},result={'item':ID+':pattern_blank','count':4}))
 recipe('rivets',dict(type='overgeared:forging',tier='stone',category='tool_heads',pattern=['N'],key={'N':{'item':'minecraft:iron_nugget'}},result={'item':ID+':rivets','count':16},hammering=2,has_quality=False,needs_minigame=False,need_quenching=False))
 recipe('buckle',dict(type='minecraft:crafting_shaped',pattern=['N N',' N '],key={'N':{'item':'minecraft:iron_nugget'}},result={'item':ID+':buckle','count':4}))
 recipe('padded_lining',dict(type='minecraft:crafting_shaped',pattern=['SWS','WLW','SWS'],key={'S':{'item':'minecraft:string'},'W':{'tag':'minecraft:wool'},'L':{'tag':'forge:leather'}},result={'item':ID+':padded_lining','count':4}))
 recipe('leather_panel',dict(type='minecraft:crafting_shaped',pattern=['LS','SL'],key={'S':{'item':'minecraft:string'},'L':{'tag':'forge:leather'}},result={'item':ID+':leather_panel','count':2}))
 recipe('armorer_table',dict(type='minecraft:crafting_shaped',pattern=['I I','LCL','P P'],key={'I':{'item':'create:iron_sheet'},'L':{'tag':'minecraft:logs'},'C':{'item':'minecraft:crafting_table'},'P':{'tag':'minecraft:planks'}},result={'item':ID+':armorer_table'}))
 # Mechanize bulk preparation, while shaped protective parts still use the native anvil.
 recipe('create/rivets',dict(type='create:pressing',ingredients=[dict(item='minecraft:iron_nugget')],results=[dict(item=ID+':rivets',count=16)]))
 recipe('create/buckles',dict(type='create:cutting',ingredients=[dict(item='create:iron_sheet')],results=[dict(item=ID+':buckle',count=12)],processingTime=100))
 add('data/minecraft/tags/blocks/mineable/axe.json',dict(replace=False,values=[ID+':armorer_table']))
 add('data/'+ID+'/loot_tables/blocks/armorer_table.json',dict(type='minecraft:block',pools=[dict(rolls=1,entries=[dict(type='minecraft:item',name=ID+':armorer_table')])]))
 add('assets/'+ID+'/blockstates/armorer_table.json',dict(variants={'':{'model':ID+':block/armorer_table'}}))
 add('assets/'+ID+'/models/block/armorer_table.json',dict(parent='minecraft:block/cube',textures=dict(particle=ID+':block/workbench_side',up=ID+':block/workbench_top',down=ID+':block/workbench_bottom',north=ID+':block/workbench_front',south=ID+':block/workbench_side',east=ID+':block/workbench_side',west=ID+':block/workbench_side')))
 import ui_resources
 files.update(ui_resources.block_art())
 add('assets/'+ID+'/models/item/armorer_table.json',dict(parent=ID+':block/armorer_table'))
 # Original 16px silhouettes, distinct metal colors; no external art dependency.
 def png(name,color,form):
  im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);dark=tuple(max(0,c-55) for c in color)+(255,);light=tuple(min(255,c+45) for c in color)+(255,);c=color+(255,)
  if form.startswith('pattern_') or form=='pattern_blank':
   d.rectangle((2,1,13,14),fill=(194,167,112,255),outline=(91,66,36,255));d.rectangle((4,4,11,10),outline=(58,73,88,255));d.line((5,12,10,12),fill=(91,66,36,255))
  elif form=='dome':d.polygon([(3,12),(3,6),(6,2),(10,2),(13,6),(13,12)],fill=c,outline=dark);d.line((5,5,8,3),fill=light)
  elif form in ['front','back','fluted_front','fluted_back']:d.polygon([(4,2),(6,4),(10,4),(12,2),(14,6),(12,13),(4,13),(2,6)],fill=c,outline=dark);d.line((5,6,5,11),fill=light)
  elif form in ['mail_panel','mail_sleeves','lamellar_panel','brigandine_panel']:
   d.rectangle((2,3,13,13),fill=dark)
   for y in range(4,13,3):
    for x in range(3,13,3):d.rectangle((x,y,x+1,y+1),fill=light if form.startswith('mail') else c)
  elif form=='rivets':
   for x,y in [(4,4),(10,3),(7,9),(12,12),(2,12)]:d.rectangle((x,y,x+2,y+2),fill=c,outline=dark)
  elif form=='buckle':d.rectangle((3,4,12,11),outline=light,width=2);d.line((7,5,7,10),fill=c)
  elif form in ['padded_lining','leather_panel','unfinished_armor']:d.polygon([(4,2),(7,4),(10,2),(14,6),(12,8),(11,13),(4,13),(3,8),(1,6)],fill=c,outline=dark);d.line((6,6,10,10),fill=light)
  else:
   for x in [2,9]:d.polygon([(x,3),(x+4,2),(x+5,8),(x+4,13),(x,12)],fill=c,outline=dark);d.line((x+1,4,x+1,10),fill=light)
  if form.startswith('fluted'):
   for x in [7,9,11]:d.line((x,6,x,12),fill=light)
  raw=io.BytesIO();im.save(raw,format='PNG');files['assets/'+ID+'/textures/item/'+name+'.png']=raw.getvalue()
 for metal,(_,_,_,color) in METALS.items():
  for form in FORMS:png(metal+'_'+form,color,form)
 for form in FORMS:png('pattern_'+form,(194,167,112),'pattern_'+form)
 for form,color in {'pattern_blank':(194,167,112),'rivets':(171,182,187),'buckle':(171,182,187),'padded_lining':(193,183,151),'leather_panel':(153,95,51),'unfinished_armor':(109,124,133)}.items():png(form,color,form)
 uk={'block.'+ID+'.armorer_table':'Стіл складання обладунків','item.'+ID+'.unfinished_armor':'Незавершений обладунок','item.'+ID+'.pattern_blank':'Заготовка оправки','item.'+ID+'.rivets':'Заклепки','item.'+ID+'.buckle':'Пряжка','item.'+ID+'.padded_lining':'Підкладка обладунку','item.'+ID+'.leather_panel':'Шкіряна секція','armory.quality':'Якість: %s','armory.metal_cost':'Метал: %s злитків','armory.treatment.0':'Потрібні загартування та відпуск','armory.treatment.1':'Загартовано — потрібен відпуск 250–450 °C','armory.treatment.2':'Загартовано та відпущено','armory.work_target':'Виріб: %s','armory.work_stage':'Завершено етапів: %s / %s','armory.work_saved':'Деталі збережено. Продовжіть на столі складання обладунків.','armory.search':'Пошук виробу','armory.assemble':'Зібрати етап','armory.repair':'Ремонт','armory.quench':'Гартувати','armory.temper':'Відпуск','armory.rework':'Докувати','armory.step':'Етап %s/4','armory.stage.0':'Основа й підкладка','armory.stage.1':'Основні захисні секції','armory.stage.2':'Рухомі частини й решта секцій','armory.stage.3':'Кріплення та завершення'}
 en={'block.'+ID+'.armorer_table':"Armorer's Workbench",'item.'+ID+'.unfinished_armor':'Unfinished Armor','item.'+ID+'.pattern_blank':'Forming Pattern Blank','item.'+ID+'.rivets':'Rivets','item.'+ID+'.buckle':'Buckle','item.'+ID+'.padded_lining':'Armor Lining','item.'+ID+'.leather_panel':'Leather Panel','armory.quality':'Quality: %s','armory.metal_cost':'Metal: %s ingots','armory.treatment.0':'Requires hardening and tempering','armory.treatment.1':'Hardened — temper at 250–450 C','armory.treatment.2':'Hardened and tempered','armory.work_target':'Workpiece: %s','armory.work_stage':'Completed stages: %s / %s','armory.work_saved':'Installed parts are saved. Continue at an armorer workbench.','armory.search':'Search armor','armory.assemble':'Assemble stage','armory.repair':'Repair','armory.quench':'Harden','armory.temper':'Temper','armory.rework':'Rework','armory.step':'Stage %s/4','armory.stage.0':'Foundation and lining','armory.stage.1':'Main protective sections','armory.stage.2':'Articulated parts and remaining sections','armory.stage.3':'Fasteners and completion'}
 statuses=[('Готово до роботи','Ready'),('Бракує деталей','Missing components'),('Заберіть або продовжіть поточний виріб','Remove or continue the current workpiece'),('Спочатку обробіть сталеві деталі','Temper the steel components first'),('Заберіть готовий виріб','Output is occupied'),('Температура не підходить','Temperature outside the required range'),('Поруч потрібен казан із водою','A filled water cauldron is required beside the bench'),('Потрібен придатний ковальський молот','A usable smithing hammer is required'),('Потрібне краще креслення броні','A better armor blueprint is required'),('Триває робота…','Working…'),('Операцію завершено','Operation complete'),('Оберіть чинний рецепт / перевірте стан деталі','Select a valid recipe / check the component state')]
 for i,(u,e) in enumerate(statuses):uk['armory.status.'+str(i)]=u;en['armory.status.'+str(i)]=e
 uk['armory.jei']='Чотири рядки рецепта відповідають чотирьом етапам складання. Сталеві деталі спочатку загартуйте при 800–1050 °C, потім відпустіть при 250–450 °C. Докладний посібник є в книзі збірки.';en['armory.jei']='The four recipe rows are the four assembly stages. Harden steel components at 800–1050 C, then temper at 250–450 C. See the modpack handbook for the complete workflow.'
 uk['armory.jei_cost']='Метал: %s злитків';en['armory.jei_cost']='Metal: %s ingots'
 for state,u,e in [('cold','Для наступної обробки холодно: потрібно %s–%s °C','Too cold for the next treatment: needs %s–%s C'),('ready','Температура придатна: %s–%s °C','Ready for the next treatment: %s–%s C'),('hot','Для наступної обробки перегріто: потрібно %s–%s °C','Too hot for the next treatment: needs %s–%s C')]:uk['armory.thermal.'+state]=u;en['armory.thermal.'+state]=e
 for key,u,e in [('poor','Погана','Poor'),('well','Хороша','Well forged'),('expert','Майстерна','Expert'),('perfect','Ідеальна','Perfect'),('master','Шедевральна','Masterwork')]:uk['armory.quality.'+key]=u;en['armory.quality.'+key]=e
 for metal,u,e in [('iron','Залізо','Iron'),('steel','Сталь','Steel'),('bronze','Бронза','Bronze'),('copper','Мідь','Copper'),('black_steel','Чорна сталь','Black steel'),('silver','Срібло','Silver'),('gold','Золото','Gold')]:
  for form,(uform,eform) in NAMES.items():uk['item.'+ID+'.'+metal+'_'+form]=uform+' · '+u.lower();en['item.'+ID+'.'+metal+'_'+form]=eform+' · '+e.lower()
 for form,(u,e) in NAMES.items():uk['item.'+ID+'.pattern_'+form]='Оправка: '+u.lower();en['item.'+ID+'.pattern_'+form]='Forming pattern: '+e.lower()
 ui_resources.translations(uk,en)
 for lang,data in [('uk_ua',uk),('en_us',en)]:add('assets/'+ID+'/lang/'+lang+'.json',data)
 guides={'uk_ua':('Майстерня обладунків',[
 'Нову броню складають на столі складання обладунків: оберіть виріб у списку, натисніть номер етапу для переліку деталей і кількостей; натискання на деталь відкриває її рецепт у JEI. Пошук працює за назвою предмета. Ковадло Overgeared виготовляє деталі; стіл збирає їх у готовий обладунок.',
 'Зробіть заготовки оправок із паперу та дощок. На каменерізі оберіть потрібну форму. Оправка лежить у сітці ковадла разом із металом і не витрачається; креслення якості броні кладеться у звичайний слот креслення. Злитки повинні мати робочу температуру TFC. Потрібні 3–4 прості удари.',
 'Великі пластини мають форму нагрудника, спини, шолома, ніг тощо. Кольчужні секції із заліза/сталі виготовляються з наявних полотен Epic Knights; ламелярні секції — з рядів пластинок. Кільця, малі пластини й смужки шкіри зберігають свої виробничі маршрути.',
 'Сталеві деталі та чорна сталь потребують термообробки. Покладіть одну деталь у комірку T стола на вкладці «Обробка». При 800–1050 °C натисніть «Гартувати»: сусідній казан із водою витратить один рівень. Потім нагрійте деталь до 250–450 °C та натисніть «Відпуск». Для нагрівання використовуйте горн TFC. Просто кинути предмет у воду означає охолодити його, але не виконати контрольоване загартування.',
 'Чотири етапи: основа й підкладка; головні захисні секції; рухомі та додаткові частини; заклепки, пряжка й шкіряні кріплення. Матеріали кладіть у 12 комірок запасу. Натискайте «Зібрати етап» після підготовки. Робота триває дві секунди поблизу гравця.',
 'Незавершений обладунок можна забрати, покласти у скриню й перенести до іншого столу. Він містить установлені частини. Ламання столу викидає його та інвентар. Надлишок матеріалів не витрачається. Готова річ з’являється у вихідній комірці під коміркою T.',
 'Якість готової броні обмежена найгіршою захисною деталлю. Пряжки, заклепки та підкладка не підвищують її. Для виправлення деталі потрібні робоча температура, молот і краще креслення броні серед запасів. «Докувати» піднімає якість на один ступінь до межі креслення, витрачає міцність молота та скидає термообробку. Ця операція не розвиває креслення.',
 'Для ремонту покладіть пошкоджену броню у комірку W на вкладці «Ремонт». Додайте сумісну готову деталь та дві заклепки до запасів, натисніть «Ремонт». Відновлення міцності пропорційне частці металу в цій деталі. Замінюється відповідна деталь найгіршої якості; повторно обчислюється якість цілого виробу.',
 'Старі обладунки залишаються придатними. При першому ремонті їхні деталі отримують початкову якість наявної речі. Чари та назва зберігаються. Декоративне перефарбування, геральдика та перероблення з тим самим захистом залишаються рідними. Система шарів не змінюється.',
 'Create готує матеріали великими партіями: прес робить заклепки із залізних самородків, пилка — пряжки із залізних листів. Пластини, кільця й ламелярні ряди надалі використовують чинні рецепти збірки. Остаточна якість залежить від захисних деталей, а не від автоматизованих кріплень.'
 ]),'en_us':('The armorer workshop',[
 'Forge protective components on the Overgeared anvil, then assemble them at an Armorer Workbench. Search for the target armor and click a stage to inspect counted requirements, then click a component for its JEI recipe. Materials go into the twelve supply slots.',
 'Craft pattern blanks from paper and planks, select their shape in a stonecutter, then put a reusable pattern beside heated metal in the anvil grid. Armor quality blueprints use the normal blueprint slot. Components take three or four gentle hits.',
 'Steel and black-steel components require hardening at 800–1050 C using a neighboring water cauldron, then reheating to 250–450 C and tempering. Use a TFC forge to heat components. Dropping an item in water still cools it but does not perform the controlled hardening operation.',
 'Assembly has four stages: foundation/lining, main sections, remaining/articulated sections, then fasteners. Each operation takes two seconds with the operator nearby. A removable unfinished workpiece stores the selected recipe and every installed component.',
 'Final armor quality is capped by its weakest protective component. Rivets and lining cannot improve it. Rework a hot component using a usable smithing hammer and a better armor blueprint in the supply slots. Each operation gains one quality tier, costs hammer durability and resets heat treatment; it grants no blueprint research.',
 'Repair damaged armor in the workpiece slot using one matching tempered component and two rivets in the supply slots. Durability restored is proportional to the component metal cost. The weakest matching installed component is replaced and overall quality is recalculated. Existing armor, names, enchantments and the layered armor system are preserved.',
 'Iron/steel mail sections reuse Epic Knights mail; lamellar sections reuse rows. Create can press rivets and cut buckles in batches. Cosmetic restyling with the same protection retains its existing routes. JEI lists all assembly components and native recipes for preparing them.'
 ])}
 guides['uk_ua'][1].append('Османські обладунки також складають тут. Шишак потребує сталевих деталей, термообробки й золотого оздоблення. Кафтан, жилети та шапки використовують тканинні секції, оригінальні тканини й повсть. Перефарбування жилета зберігає назву, чари, міцність і встановлені деталі. Зброю та калкан кують на ковадлі зі звичайної сталі Overgeared.')
 guides['uk_ua'][1].append('Спеціалізація PR 149 діє і для османського спорядження. Для повного комплекту лат: кулі −15%, різальні удари/стріли −30%, ударні +20%; для м’якого одягу: кулі +30%, різальні/стріли −10%, ударні −20%. Внесок шолома — 20%, нагрудника — 40%, поножів — 30%, чобіт — 10%. Це зміна вхідного урону до звичайного захисту броні. Топуз використовує загальну ударну спеціалізацію без окремого нищення броні; калкан блокує спереду при утриманні ПКМ і не відкидає нападника.')
 guides['en_us'][1].append('Ottoman armor uses this workbench too: the shishak needs treated steel sections and gold decoration; kaftans, yeleks and hats use textile sections plus their original fabrics and felt. Dyeing a yelek preserves its name, enchantments, wear and installed parts. Forge Ottoman weapons and the kalkan using normal Overgeared steel. The kalkan uses held directional blocking without shield knockback; the topuz has no separate armor-destruction bonus.')
 guides['en_us'][1].append('PR 149 specializations apply: plate takes −15% bullet, −30% cutting/arrow and +20% blunt damage; padded clothing takes +30%, −10% and −20%, respectively. These are full-set values before normal armor protection. Slot weights are helmet 20%, chest 40%, legs 30%, boots 10%. Ottoman sabres count as cutting weapons and the topuz as blunt.')
 for lang,(title,paragraphs) in guides.items():
  entry=dict(name=title,icon=ID+':armorer_table',category='empirecraft_guidebook:metals',sortnum=30,read_by_default=True,pages=[dict(type='patchouli:text',text=p) for p in paragraphs])
  # Keep individual pages within Patchouli's narrow text area.
  pages=[]
  for p in paragraphs:
   words=p.split();current=[]
   for word in words:
    if len(' '.join(current+[word]))>360:pages.append(dict(type='patchouli:text',text=' '.join(current)));current=[]
    current.append(word)
   if current:pages.append(dict(type='patchouli:text',text=' '.join(current)))
  entry['pages']=pages
  import sys
  sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'handbook'))
  import layout
  layout.fit({}, {'armorer_workshop':entry})
  add('assets/empirecraft_guidebook/patchouli_books/handbook/'+lang+'/entries/metals/armorer_workshop.json',entry)
 return files
