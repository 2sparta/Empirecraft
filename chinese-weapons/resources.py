import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parent;NS='chineseweapons';PREFIX=NS+':'
SLOTS=('helmet','chestplate','leggings','boots')
def policy():
 names=json.loads((ROOT/'inventory.json').read_text());aliases={}
 for name in names:
  target=None
  if name=='ancient_gem':target='minecraft:amethyst_shard'
  elif name.startswith('diamond_') and any(name.endswith('_'+family) for family in ('dagger_axe','glaive','guduo','sky_piercing_halberd','song_javelin','tang_dynasty_heng_saber')):target=PREFIX+'iron_'+name.split('_',1)[1]
  elif 'armor_pieces' in name:target='empirecraft_armory:iron_lamellar_panel'
  elif name.endswith('_forging_stone'):target='minecraft:paper'
  elif name.startswith(('diamond_','netherite_','golden_')) and (name.endswith(SLOTS) or name.endswith('_horse_armor')):target=PREFIX+'iron_'+name.split('_',1)[1]
  if target:aliases[PREFIX+name]=target
 for name,target in [('armor_casting_table','empirecraft_armory:armorer_table'),('ancient_gem_ore','minecraft:stone'),('deepslate_ancient_gem_ore','minecraft:deepslate'),('ancient_gem_block','minecraft:stone')]:aliases[PREFIX+name]=target
 kept=[n for n in names if PREFIX+n not in aliases]
 armor=[n for n in kept if n.endswith(SLOTS)];horse=[n for n in kept if n.endswith('_horse_armor')]
 return dict(aliases=aliases,kept=kept,armor=armor,horse=horse)
def encoded(obj):return (json.dumps(obj,ensure_ascii=False,indent=2)+'\n').encode()
def generate(files,tfc):
 p=policy();aliases=p['aliases'];removed=[];files['empirecraft/chinese-aliases.json']=encoded(aliases)
 def add(name,data):files[name]=encoded(data)
 def canonical(value):
  if isinstance(value,str):return aliases.get(value,value)
  if isinstance(value,list):return [canonical(v) for v in value]
  if isinstance(value,dict):return {k:canonical(v) for k,v in value.items()}
  return value
 with __import__('zipfile').ZipFile(tfc) as z:
  heads=sorted('tfc:'+n.removeprefix('assets/tfc/models/item/').removesuffix('.json') for n in z.namelist() if n.startswith('assets/tfc/models/item/stone/knife_head/') and n.endswith('.json'))
 assert len(heads)==4,heads
 add('data/chineseweapons/tags/items/knapped_blades.json',dict(replace=False,values=heads))
 weapons=[]
 for name in list(files):
  if name.startswith('data/'+NS+'/worldgen') or '/biome_modifier/' in name and name.startswith('data/'+NS+'/'):removed.append(name);del files[name];continue
  if name.startswith('data/'+NS+'/recipes/') and name.endswith('.json'):
   data=json.loads(files[name]);out=data.get('result',{});result=out.get('item') if isinstance(out,dict) else out
   if result in aliases or name.endswith('/forging_notes.json'):removed.append(name);del files[name];continue
   item=result.removeprefix(PREFIX) if isinstance(result,str) and result.startswith(PREFIX) else None
   if item in p['armor']:
    pattern={'helmet':['III','I I'],'chestplate':['I I','III','III'],'leggings':['III','I I','I I'],'boots':['I I','I I']}[next(s for s in SLOTS if item.endswith(s))]
    data=dict(type='minecraft:crafting_shaped',pattern=pattern,key={'I':{'item':'minecraft:iron_ingot'}},result={'item':result})
   elif item in p['horse']:data=dict(type='minecraft:crafting_shaped',pattern=['I I','III','LWL'],key={'I':{'item':'minecraft:iron_ingot'},'L':{'item':'minecraft:leather'},'W':{'tag':'minecraft:wool'}},result={'item':result})
   elif item and data.get('type')=='chineseweapons:weapon_casting_shaped':
    weapons.append(item);family=next((f for f in ['song_javelin','dagger_axe','sky_piercing_halberd','tang_dynasty_heng_saber','glaive','guduo'] if item.endswith(f)),None)
    if item=='green_loong_glaive':cost=[{'item':NS+':iron_glaive'},{'item':'overgeared:steel_plate'},{'item':'overgeared:steel_plate'},{'item':'minecraft:emerald'},{'item':'minecraft:gold_nugget'}];tier='iron'
    elif family:
     tier=item.split('_',1)[0];metal='wrought_iron' if tier=='iron' else 'steel';head='javelin_head' if family=='song_javelin' else 'axe_head' if family=='dagger_axe' else 'hammer_head' if family=='guduo' else 'knife_blade' if family=='tang_dynasty_heng_saber' else 'scythe_blade';cost=[{'item':'tfc:metal/'+head+'/'+metal},{'item':'minecraft:stick'},{'item':'minecraft:leather'}]
     if family=='sky_piercing_halberd':cost.append({'item':'tfc:metal/javelin_head/'+metal})
     if family in ['glaive','sky_piercing_halberd']:cost.extend([{'item':'minecraft:iron_ingot'}]*2+[{'item':'minecraft:stick'}])
     if tier in ['wooden','stone']:
      cost=[{'tag':'minecraft:planks'},{'item':'minecraft:stick'},{'item':'minecraft:string'}]
      if tier=='stone':cost.insert(0,{'tag':NS+':knapped_blades'})
     elif tier in ['diamond','golden','netherite']:cost.append({'item':{'diamond':'minecraft:diamond','golden':'minecraft:gold_ingot','netherite':'minecraft:netherite_ingot'}[tier]})
    else:
     cost=[{'item':'minecraft:iron_ingot'},{'item':'minecraft:iron_ingot'},{'item':'minecraft:iron_ingot'},{'tag':'minecraft:planks'},{'item':'minecraft:leather'}];tier='iron'
    keys={chr(65+i):v for i,v in enumerate(cost)};flat=''.join(keys).ljust(9);data=dict(type='minecraft:crafting_shaped',pattern=[flat[i:i+3] for i in range(0,9,3)],key=keys,result={'item':result})
    if tier not in ['wooden','stone']:data.update(type='overgeared:forging',category='misc',tier='iron',hammering=4,has_quality=True,needs_minigame=True,blueprint=['shield' if item.endswith('shield') else 'axe' if family in ['dagger_axe','guduo','sky_piercing_halberd'] else 'sword'],requires_blueprint=False,minimum_quality='poor',quality_difficulty='none',need_quenching=False)
   add(name,canonical(data))
  elif name.startswith('data/') and '/tags/' in name and name.endswith('.json'):
   data=json.loads(files[name]);data['values']=[v for v in data.get('values',[]) if (v.get('id') if isinstance(v,dict) else v) not in aliases];add(name,data)
  elif name.startswith('data/') and name.endswith('.json'):
   try:add(name,canonical(json.loads(files[name])))
   except (ValueError,UnicodeDecodeError):pass
  if '/patchouli_books/forging_notes/' in name and name.startswith('assets/') and name.endswith('.json'):
   # Retain the original copyright and credits; gameplay guidance is replaced below.
   if '/entries/' in name and '/entries/other/' not in name and '/entries/ovo/' not in name:del files[name]
   elif '/categories/' in name and not name.endswith(('/other.json','/ovo.json')):del files[name]
   else:add(name,canonical(json.loads(files[name])))
 for family,kind in [('guduo','crushing'),('song_javelin','piercing'),('dagger_axe','slashing'),('sky_piercing_halberd','piercing'),('tang_dynasty_heng_saber','slashing'),('glaive','slashing')]:
  values=[PREFIX+n for n in weapons if n.endswith(family)]
  path='data/tfc/tags/items/deals_'+kind+'_damage.json';existing=json.loads(files.get(path,b'{"values":[]}'));existing.update(replace=False,values=existing['values']+values);add(path,existing)
 for item in weapons:
  if item.startswith(('wooden_','stone_')) or item.endswith('shield'):continue
  add('data/'+NS+'/tfc/item_heats/'+item+'.json',dict(ingredient={'item':PREFIX+item},heat_capacity=3.0,forging_temperature=924,welding_temperature=1232))
 add('data/empirecraft_armor/tags/items/outer.json',dict(replace=False,values=[PREFIX+n for n in p['armor']]))
 guides(files,p)
 return dict(**p,weapon_recipes=weapons,removed_data=removed)

def guides(files,p):
 def add(name,value):files[name]=encoded(value)
 families={'early_tang_dynasty_ming_guang_armor':'Мінґуан ранньої Тан','middle_tang_dynasty_ming_guang_armor':'Мінґуан середньої Тан','late_tang_dynasty_ming_guang_armor':'Мінґуан пізньої Тан','black_chui_armor':'Чорний Чуй','mountain_character_armor':'Шаньвень','fine_scale_armor':'Дрібнолускатий','footmen_armor':'Важка піхота','song_dynasty_infantry_armor':'Піхота Сун','tang_dynasty_infantry_armor':'Піхота Тан'}
 slots={'helmet':'Шолом','chestplate':'Нагрудник','leggings':'Поножі','boots':'Чоботи'};uk={}
 for name in p['armor']:
  bare=name.removeprefix('iron_');slot=next(s for s in SLOTS if name.endswith(s));family=bare.removesuffix('_'+slot);title=families.get(family,{'suanni':'Суанні','bohai':'Бохай','face_guard':'Захист обличчя'}.get(family,family));uk['item.'+NS+'.'+name]=slots[slot]+' · '+title
 materials={'wooden':'Деревина','stone':'Камінь','iron':'Залізо','golden':'Золоте оздоблення','diamond':'Алмазне оздоблення','netherite':'Незеритове оздоблення'}
 weapons={'dagger_axe':'Ге','glaive':'Гуаньдао','sky_piercing_halberd':'Фантянь хуадзі','tang_dynasty_heng_saber':'Хендао','guduo':'Гудо','song_javelin':'Метальний спис Сун'}
 for name in p['kept']:
  tier,_,family=name.partition('_')
  if family in weapons:uk['item.'+NS+'.'+name]=weapons[family]+' · '+materials.get(tier,tier)
 uk.update({'item.'+NS+'.green_loong_glaive':'Гуаньдао «Зелений Дракон»','item.'+NS+'.iron_tang_dynasty_horse_armor':'Кінський обладунок Тан','item.'+NS+'.tang_dynasty_shield':'Щит Тан','item.'+NS+'.song_standing_shield':'Павеза Сун','item_group.'+NS+'.chinese_weapons':'Китайське спорядження','key.skill.text':'Розмістити / забрати павезу','patchouli.chineseweapons.book.name':'Chinese Weapons: довідка','patchouli.chineseweapons.book.landing_text':'Ця версія інтегрована в Empirecraft. Виготовлення обладунків, ковка, термообробка та бойові профілі описані в головній книзі збірки, у розділі «Китайське спорядження». Тут збережені авторські відомості.'})
 add('assets/'+NS+'/lang/uk_ua.json',uk)
 en_name='assets/'+NS+'/lang/en_us.json';en=json.loads(files[en_name]);en['patchouli.chineseweapons.book.landing_text']='Integrated with Empirecraft: use the main modpack handbook, Chinese equipment chapter, for current assembly, forging and combat rules. Original attribution is retained here.';en['key.skill.text']='Place / retrieve standing shield';add(en_name,en)
 texts={
 'uk_ua':[
 'Китайські обладунки виготовляються на нашому столі складання обладунків. Оберіть модель, наведіть на номер етапу, підготуйте деталі у запасах. JEI показує всі чотири етапи. Власні Ancient Gem, руди, ливарний стіл, камені-шаблони й окремі пластинки цього моду вилучені.',
 'Нагрудники, поножі й чоботи Тан/Сун, Мінґуан, Чорний Чуй, Шаньвень і дрібнолускаті моделі використовують ламелярні секції з нашої майстерні. Шоломи потребують купола та захисту потилиці; лицева маска також потребує забрала. Деталі кують на ковадлі Overgeared з нагрітого металу TFC.',
 'Тут залишені залізні обладунки та піхотні моделі. Золоті, алмазні та незеритові обладунки, включно з кінськими, не реєструються. Кольори дозволеної броні можна змінювати фарбниками у звичайній сітці: зношення, назва й установлені деталі зберігаються.',
 'Кінський обладунок Тан також складається у чотири етапи: шкіра, вовна й нитка; шість ламелярних секцій; захист плечей та поясні пластини; кріплення. Він дає звичайний фізичний захист і не робить коня невразливим до стріл.',
 'Бойова спеціалізація за PR 149: металеві шоломи мають латний профіль, ламелярний корпус — кольчужний. Латний повний комплект змінює вхідний урон: кулі −15%, різальні/стріли −30%, ударні +20%. Для кольчужного профілю відповідно +20%, −35%, +15%.',
 'Профіль кожної речі має вагу свого слота: шолом 20%, нагрудник 40%, поножі 30%, чоботи 10%. Зміни рахуються перед звичайним захистом броні. У підказці наведені значення для повного комплекту одного профілю; змішаний комплект підсумовує внески окремих речей.',
 'Зброя з металевою основою виготовляється ковкою з наявних головок і лез TFC, руків’я та шкіри. Для списів потрібен наконечник списа; для Ге — вістря сокири; для Гудо — головка молота. Леза й головки мають бути нагріті до робочої температури TFC. Дивіться точні кількості в JEI.',
 'Дерев’яна зброя є простим тренувальним варіантом. Кам’яні варіанти використовують леза, зроблені через розколювання TFC, та збираються у сітці. Гуаньдао «Зелений Дракон» виготовляється із залізного гуаньдао, сталевих пластин та оздоблення; Ancient Gem не потрібний.',
 'Залишені звичайний урон, швидкість і додаткова дальність зброї через Forge ENTITY_REACH — той самий атрибут, що в Epic Knights. Дальність не додається повторно. Кровотеча, порізи, засліплення, стягування з коня, вихори, ривки, пасивні бонуси й особливі атаки вимкнені.',
 'Зачарування нового спорядження заборонені, включно з книжками. Старі теги чар не дають ефекту. Метальний спис зберігає звичайний кидок і повернення предмета після підбирання; Вірність, Громовержець, Тягун та інші чарівні властивості не діють. Якість кинутої зброї фіксується в момент кидка.',
 'Павезу Сун можна використати як звичайний щит або фізично встановити й забрати клавішею дії моду (типово J). Це не дає бойових умінь іншій зброї. Моделі, фарбування та відомості про авторів збережені.'
 ],
 'en_us':[
 'Chinese armor uses the Empirecraft Armorer Workbench and four-stage assembly. Search for a model, hover stage requirements and consult JEI. Ancient Gem, its ores, the original casting table, forging stones and duplicate armor pieces are removed.',
 'Lamellar body armor uses existing iron lamellar sections. Helmets use a dome and nape protection; the face guard also needs a visor. Forge sections with heated TFC metal on the Overgeared anvil. Gold, diamond and netherite armor, including horse armor, is removed.',
 'Tang horse armor also uses four-stage assembly: leather, wool and string; six lamellar panels plus shoulder and waist sections; then fasteners. It provides normal armor protection without arrow immunity. Dyeing retained player armor preserves names, wear and installed components.',
 'PR149 specializations apply before normal armor: plate full-set bullet/cutting-arrow/blunt modifiers are −15%/−30%/+20%; mail modifiers are +20%/−35%/+15%. Chinese metal helmets use plate; lamellar body pieces use mail. Helmet/chest/legs/boots contribute 20%/40%/30%/10%.',
 'Metal weapons use native TFC heads/blades, handles and leather in Overgeared forging. Spearheads serve javelins, axe heads serve dagger-axes and hammer heads serve guduo. Stone weapons require TFC-knapped blades; wooden training weapons use ordinary materials. JEI lists exact amounts.',
 'Normal damage, speed and Forge ENTITY_REACH are retained. This is the same reach attribute used by Epic Knights, with no second reach bonus. Bleed, cuts, blindness, dismounting, spinning skills, dashes, extra special attacks and passive armor bonuses are disabled.',
 'Equipment cannot be enchanted or accept enchanted books; stored enchantments have no effect. Javelins retain physical throwing and pickup, with forging quality snapshotted at launch. Loyalty, Channeling, Riptide and other magical behavior are disabled.',
 'Green Dragon Glaive uses an iron glaive, steel plates and decoration. The Song standing shield retains normal blocking and physical placement/retrieval using the action key (default J). Original models, dye behavior and attribution are retained.'
 ]}
 import sys
 sys.path.insert(0,str(ROOT.parent/'handbook'));import layout
 for lang,paragraphs in texts.items():
  title='Китайське спорядження' if lang=='uk_ua' else 'Chinese equipment';entry=dict(name=title,icon=PREFIX+'iron_mountain_character_armor_helmet',category='empirecraft_guidebook:metals',sortnum=31,read_by_default=True,pages=[dict(type='patchouli:text',text=t) for t in paragraphs]);layout.fit({}, {'chinese_equipment':entry});add('assets/empirecraft_guidebook/patchouli_books/handbook/'+lang+'/entries/metals/chinese_equipment.json',entry)
  category=dict(name=title,description='Empirecraft',icon=PREFIX+'iron_mountain_character_armor_helmet',sortnum=0);add('assets/'+NS+'/patchouli_books/forging_notes/'+lang+'/categories/integration.json',category)
  directions='Відкрий головний посібник Empirecraft → Китайське спорядження. Тут залишені авторські відомості; чинні рецепти дивись у JEI.' if lang=='uk_ua' else 'Open the main Empirecraft handbook, Chinese equipment chapter. Original credits remain here; JEI shows current recipes.'
  add('assets/'+NS+'/patchouli_books/forging_notes/'+lang+'/entries/integration.json',dict(name=title,icon=PREFIX+'iron_mountain_character_armor_helmet',category=NS+':integration',pages=[dict(type='patchouli:text',text=directions)]))
